package com.example.lifesaiver.presentation

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifesaiver.core.audio.AudioEngine
import com.example.lifesaiver.core.audio.VoiceRecorder
import com.example.lifesaiver.core.ble.BleDebugSnapshot
import com.example.lifesaiver.core.ble.BleManager
import com.example.lifesaiver.core.ble.BleTransport
import com.example.lifesaiver.core.database.AppDatabase
import com.example.lifesaiver.core.log.ConnectionLog
import com.example.lifesaiver.core.media.FileTransferStorage
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.core.service.RescueService
import com.example.lifesaiver.core.wifi.WifiAwareRanger
import com.example.lifesaiver.core.wifi.WifiDirectRanger
import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.core.ProtocolCore
import com.example.lifesaiver.protocol.mesh.GossipTlv
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.protocol.mesh.MeshPeerRegistry
import com.example.lifesaiver.protocol.mesh.PeerIdentityRegistry
import com.example.lifesaiver.protocol.model.CallHandshakeAction
import com.example.lifesaiver.protocol.model.CallHandshakePayload
import com.example.lifesaiver.protocol.model.CallHandshakeState
import com.example.lifesaiver.protocol.model.FileTransferPayload
import com.example.lifesaiver.protocol.model.IdentityAnnouncementPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.model.RequestSyncPayload
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.profile.ProfileTlv
import com.example.lifesaiver.protocol.security.SignatureManager
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.protocol.sync.GossipSyncManager
import com.example.lifesaiver.protocol.util.sha256Bytes
import com.example.lifesaiver.presentation.packet.ProfilePacketHandler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class BleDebugStats(
    val scanRssiAvg: Int? = null,
    val scanRssiCount: Int = 0,
    val connectionRssiAvg: Int? = null,
    val connectionRssiCount: Int = 0,
    val pendingCount: Int = 0,
    val attemptTracked: Int = 0,
    val maxAttempts: Int = 0
) {
    companion object {
        fun fromSnapshot(snapshot: BleDebugSnapshot): BleDebugStats {
            return BleDebugStats(
                scanRssiAvg = snapshot.scanRssiAvg,
                scanRssiCount = snapshot.scanRssiCount,
                connectionRssiAvg = snapshot.connectionRssiAvg,
                connectionRssiCount = snapshot.connectionRssiCount,
                pendingCount = snapshot.pendingCount,
                attemptTracked = snapshot.attemptTracked,
                maxAttempts = snapshot.maxAttempts
            )
        }
    }
}

// UI 상태
data class AppUiState(
    val hasPermissions: Boolean = false,
    val batteryLevel: Int = 100,
    val isConnected: Boolean = false,
    val connectedCount: Int = 0,
    val meshPeerCount: Int = 0,
    val directPeerIds: List<String> = emptyList(),
    val myPeerId: String = "",
    val myNickname: String = "",
    val peerNicknames: Map<String, String> = emptyMap(),
    val meshGraphSnapshot: MeshGraphRegistry.GraphSnapshot = MeshGraphRegistry.GraphSnapshot(
        emptyList(),
        emptyList()
    ),
    val isMicOn: Boolean = false,
    val isDisconnecting: Boolean = false,
    val isRescueSignalActive: Boolean = false, // 구조 신호 활성화 여부
    val messages: List<ChatMessage> = emptyList(),
    val bleDebug: BleDebugStats = BleDebugStats(),
    val signatureLogs: List<SignatureLogEntry> = emptyList(),
    val profileLogs: List<ProfileSyncLogEntry> = emptyList(),
    val survivors: List<SurvivorProfile> = emptyList(),
    val incomingCallPeerId: String? = null,
    val incomingCallName: String? = null,
    val incomingCallWifiAware: Boolean = false,
    val incomingCallWifiDirect: Boolean = false,
    val incomingCallUseOpus: Boolean = false,
    val incomingCallState: com.example.lifesaiver.protocol.model.CallHandshakeState? = null,
    val incomingCallRttCm: Int? = null,
    val isCallConnected: Boolean = false,
    val callPeerWifiAware: Boolean? = null,
    val callPeerWifiDirect: Boolean? = null,
    val callPeerUseOpus: Boolean? = null,
    val callPeerId: String? = null,
    val callPeerState: com.example.lifesaiver.protocol.model.CallHandshakeState? = null,
    val callPeerRttCm: Int? = null
)

// UI 이벤트
sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val forcePcmCall = true

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _meshVisualEvents = MutableSharedFlow<MeshVisualEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val meshVisualEvents: SharedFlow<MeshVisualEvent> = _meshVisualEvents.asSharedFlow()

    // 권한 목록
    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }

    private var audioEngine: AudioEngine? = null
    val wifiAwareRanger = WifiAwareRanger(app)
    val wifiDirectRanger = WifiDirectRanger(app)
    private lateinit var bleManager: BleManager
    private lateinit var protocolCore: ProtocolCore
    private lateinit var signatureManager: SignatureManager
    private lateinit var gossipSyncManager: GossipSyncManager
    private lateinit var profilePacketHandler: ProfilePacketHandler
    private val signatureLogBuffer = ArrayDeque<SignatureLogEntry>()
    private val profileLogBuffer = ArrayDeque<ProfileSyncLogEntry>()
    private val profileDao by lazy { AppDatabase.getInstance(app).profileDao() }
    private val meshRegistry = MeshPeerRegistry()

    // [수정] 사이렌 발생기 및 오디오 매니저 (볼륨 제어용)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private var senderId: ByteArray = ByteArray(0)
    private val profileStore = ProfileStore(app)
    @Volatile private var cachedNickname: String = ""
    private val announcedToPeers = mutableSetOf<String>()
    private val peerNicknames = mutableMapOf<String, String>()
    private val discoveredSurvivors = mutableMapOf<String, SurvivorProfile>()

    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null
    private val meshGraphRegistry = MeshGraphRegistry()
    private val peerIdentityRegistry = PeerIdentityRegistry()
    private var announceJob: kotlinx.coroutines.Job? = null
    private var meshCleanupJob: kotlinx.coroutines.Job? = null
    private var bleDebugJob: kotlinx.coroutines.Job? = null
    private var lastConnectionAnnounceMs: Long = 0L
    private val connectionAnnounceCooldownMs: Long = 3_000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryLevel(it) }
        }
    }

    init {
        initProtocol()
        initBle()
        initBatteryMonitor()
        refreshPermissions()
        observeProfileName()
        observeProfiles()
        observeMeshGraph()
        observeCallConnection()
        _uiState.update { it.copy(myPeerId = bytesToHex(senderId)) }
        AppShutdownHooks.register(
            onSendLeave = { sendLeaveOnShutdown() },
            onStopServices = { stopServicesForShutdown() }
        )
    }

    // ------------------------------------------------------------------------
    // [핵심 기능] 구조 요청 신호 + 백그라운드 서비스 제어
    // ------------------------------------------------------------------------

    fun startRescueSignal() {
        if (!_uiState.value.hasPermissions) {
            _uiEvents.tryEmit(UiEvent.Toast("블루투스 및 서비스 권한이 필요합니다."))
            return
        }

        // 1. BleManager: 72시간 생존 모드 신호 송출 시작
        bleManager.startEmergencyAdvertising()

        // 2. RescueService: 백그라운드 서비스 시작
        try {
            val intent = Intent(app, RescueService::class.java).apply {
                action = RescueService.ACTION_START_RESCUE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "서비스 시작 실패: ${e.message}")
        }

        _uiState.update { it.copy(isRescueSignalActive = true) }
    }

    fun pulseRescueSignal() {
        if (!_uiState.value.hasPermissions) return
        if (!::bleManager.isInitialized) return
        bleManager.pulseEmergencyAdvertising()
    }

    fun stopRescueSignal() {
        // 1. 신호 중단
        bleManager.stopAdvertising()

        // 2. 백그라운드 서비스 종료
        try {
            val intent = Intent(app, RescueService::class.java).apply {
                action = RescueService.ACTION_STOP_RESCUE
            }
            app.startService(intent)
        } catch (e: Exception) {
            Log.e("AppViewModel", "서비스 종료 실패: ${e.message}")
        }

        _uiState.update { it.copy(isRescueSignalActive = false) }
        _uiEvents.tryEmit(UiEvent.Toast("구조 신호 중단됨"))
    }

    /**
     * [수정] 사이렌 울리기 (기기 볼륨 설정을 그대로 사용)
     */
    private fun playSiren() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 기기 설정된 알람 볼륨으로 사이렌 1회 울리기
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
            } catch (e: Exception) {
                Log.e("Siren", "Error playing siren: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------------
    // 기존 기능 유지
    // ------------------------------------------------------------------------

    fun refreshPermissions() {
        val granted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }
        _uiState.update { it.copy(hasPermissions = granted) }

        if (granted && audioEngine == null) {
            initAudio()
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            if (audioEngine == null) initAudio()
        } else {
            _uiEvents.tryEmit(UiEvent.Toast("필수 권한이 필요합니다."))
        }
    }

    fun onStartAutoConnect() {
        bleManager.startAutoConnect()
    }

    fun onStopAutoConnect() {
        bleManager.disconnect()
    }

    fun onMicPress() {
        if (!_uiState.value.hasPermissions) {
            _uiEvents.tryEmit(UiEvent.Toast("마이크 권한이 필요합니다."))
            return
        }
        if (_uiState.value.isMicOn) return

        val outDir = File(app.filesDir, "voicenotes/outgoing")
        if (!outDir.exists()) outDir.mkdirs()

        val recorder = VoiceRecorder(outDir)
        val file = recorder.start()

        if (file == null) {
            _uiEvents.tryEmit(UiEvent.Toast("녹음 시작 실패"))
            return
        }

        voiceRecorder = recorder
        recordingFile = file
        _uiState.update { it.copy(isMicOn = true) }
    }

    fun onMicRelease() {
        if (!_uiState.value.isMicOn) return
        val recorder = voiceRecorder
        val pendingFile = recordingFile
        voiceRecorder = null
        recordingFile = null
        _uiState.update { it.copy(isMicOn = false) }

        viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            val file = recorder?.stop() ?: pendingFile
            if (file == null || !file.exists()) return@launch

            val bytes = runCatching { file.readBytes() }.getOrNull()

            if (bytes == null) {
                _uiEvents.emit(UiEvent.Toast("파일 읽기 실패"))
                return@launch
            }

            val payload = FileTransferPayload(
                fileName = file.name,
                fileSize = bytes.size.toLong(),
                mimeType = "audio/mp4",
                content = bytes
            ).encode()

            val packet = Packet(
                header = PacketHeader(
                    version = 2,
                    type = PacketType.FILE_TRANSFER,
                    ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                    flags = 0,
                    length = payload.size,
                    timestamp = System.currentTimeMillis(),
                    senderId = senderId
                ),
                payload = payload
            )

            protocolCore.broadcast(packet)
            addMessage(ChatMessage(text = "[voice] ${file.absolutePath}", isMine = true))
        }
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        val packet = buildMessagePacket(text)
        val signedPacket = signatureManager.sign(packet)
        gossipSyncManager.onPublicPacketSeen(signedPacket)
        protocolCore.broadcast(signedPacket)
        addMessage(ChatMessage(text = text, isMine = true))
    }

    fun ensureWifiAwarePermissions(): Boolean {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        required.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        required.add(Manifest.permission.RECORD_AUDIO)

        val missing = required.filterNot { permission ->
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            _uiEvents.tryEmit(UiEvent.Toast("근처 기기/위치 권한이 필요합니다."))
            return false
        }

        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager?.isWifiEnabled == false) {
            _uiEvents.tryEmit(UiEvent.Toast("Wi-Fi가 꺼져있습니다."))
            return false
        }

        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            providers.isNotEmpty()
        }
        if (!locationEnabled) {
            _uiEvents.tryEmit(UiEvent.Toast("위치 서비스가 꺼져있습니다."))
            return false
        }
        return true
    }

    fun getConnectivityBlockReason(): String? {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) !=
                    PackageManager.PERMISSION_GRANTED) {
                    add("NEARBY_WIFI_DEVICES")
                }
            }
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
                add("ACCESS_FINE_LOCATION")
            }
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
                add("ACCESS_COARSE_LOCATION")
            }
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
                add("RECORD_AUDIO")
            }
        }
        if (missing.isNotEmpty()) {
            return "권한 없음: ${missing.joinToString(", ")}"
        }
        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager?.isWifiEnabled == false) {
            return "Wi-Fi OFF"
        }
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            providers.isNotEmpty()
        }
        if (!locationEnabled) {
            return "Location OFF"
        }
        return null
    }

    fun sendCallHandshake(
        targetPeerIdHex: String,
        action: CallHandshakeAction,
        callerName: String,
        wifiAwareSupported: Boolean,
        wifiDirectSupported: Boolean,
        useOpus: Boolean,
        state: CallHandshakeState? = null,
        rttCm: Int? = null
    ) {
        if (targetPeerIdHex.isBlank()) return
        val actualUseOpus = if (forcePcmCall) false else useOpus
        val resolvedRttCm = rttCm ?: wifiAwareRanger.isConnectionReady.value.let { ready ->
            if (!ready) null else wifiAwareRanger.rttDistance.value
        }?.let { distance ->
            val cm = (distance * 100f).roundToInt()
            cm.coerceIn(0, 0xFFFF)
        }
        val payload = CallHandshakePayload(
            action = action,
            callerName = callerName,
            wifiAwareSupported = wifiAwareSupported,
            wifiDirectSupported = wifiDirectSupported,
            useOpus = actualUseOpus,
            state = state,
            rttCm = resolvedRttCm
        ).encode()
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.CALL_HANDSHAKE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = senderId,
                recipientId = hexToBytes(targetPeerIdHex)
            ),
            payload = payload
        )
        protocolCore.broadcast(packet)
    }

    fun clearIncomingCall(peerIdHex: String) {
        _uiState.update { state ->
            if (state.incomingCallPeerId != peerIdHex) return@update state
            state.copy(
                incomingCallPeerId = null,
                incomingCallName = null,
                incomingCallWifiAware = false,
                incomingCallWifiDirect = false,
                incomingCallUseOpus = false,
                incomingCallState = null,
                incomingCallRttCm = null
            )
        }
    }

    private fun handleCallHandshake(peerIdHex: String, payload: CallHandshakePayload) {
        ConnectionLog.add(
            "CallHandshake",
            "recv ${payload.action.name} from=$peerIdHex aware=${payload.wifiAwareSupported} direct=${payload.wifiDirectSupported} opus=${payload.useOpus}"
        )
        when (payload.action) {
            CallHandshakeAction.START -> {
                val callerName = payload.callerName?.ifBlank { "구조자" } ?: "구조자"
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wifiAware = payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                val wifiDirect = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                _uiState.update {
                    it.copy(
                        incomingCallPeerId = peerIdHex,
                        incomingCallName = callerName,
                        incomingCallWifiAware = wifiAware,
                        incomingCallWifiDirect = wifiDirect,
                        incomingCallUseOpus = payload.useOpus,
                        incomingCallState = payload.state,
                        incomingCallRttCm = payload.rttCm,
                        callPeerWifiAware = wifiAware,
                        callPeerWifiDirect = wifiDirect,
                        callPeerUseOpus = payload.useOpus,
                        callPeerId = peerIdHex,
                        callPeerState = payload.state,
                        callPeerRttCm = payload.rttCm
                    )
                }
            }
            CallHandshakeAction.END -> {
                _uiState.update { state ->
                    val shouldClearIncoming = state.incomingCallPeerId == peerIdHex
                    val shouldClearCallPeer = state.callPeerId == peerIdHex
                    if (!shouldClearIncoming && !shouldClearCallPeer) return@update state
                    state.copy(
                        incomingCallPeerId = if (shouldClearIncoming) null else state.incomingCallPeerId,
                        incomingCallName = if (shouldClearIncoming) null else state.incomingCallName,
                        incomingCallWifiAware = if (shouldClearIncoming) false else state.incomingCallWifiAware,
                        incomingCallWifiDirect = if (shouldClearIncoming) false else state.incomingCallWifiDirect,
                        incomingCallUseOpus = if (shouldClearIncoming) false else state.incomingCallUseOpus,
                        incomingCallState = if (shouldClearIncoming) null else state.incomingCallState,
                        incomingCallRttCm = if (shouldClearIncoming) null else state.incomingCallRttCm,
                        callPeerWifiAware = if (shouldClearCallPeer) null else state.callPeerWifiAware,
                        callPeerWifiDirect = if (shouldClearCallPeer) null else state.callPeerWifiDirect,
                        callPeerUseOpus = if (shouldClearCallPeer) null else state.callPeerUseOpus,
                        callPeerId = if (shouldClearCallPeer) null else state.callPeerId,
                        callPeerState = if (shouldClearCallPeer) null else state.callPeerState,
                        callPeerRttCm = if (shouldClearCallPeer) null else state.callPeerRttCm
                    )
                }
            }
            CallHandshakeAction.ACK -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val mergedWifiAware = payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                val mergedWifiDirect = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                meshRegistry.updatePeer(
                    peerIdHex = peerIdHex,
                    isWifiAware = mergedWifiAware,
                    isWifiDirect = mergedWifiDirect,
                    isUwb = peerInfo?.isUwb ?: false
                )
                refreshSurvivorCapabilities()
                _uiState.update {
                    it.copy(
                        callPeerWifiAware = mergedWifiAware,
                        callPeerWifiDirect = mergedWifiDirect,
                        callPeerUseOpus = payload.useOpus,
                        callPeerId = peerIdHex,
                        callPeerState = payload.state,
                        callPeerRttCm = payload.rttCm
                    )
                }
            }
        }
    }

    fun sendProfileTestPacket() {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(
            name = cachedNickname.ifBlank { "debug-user" },
            gender = 'U',
            birthDate = "2000-01-01",
            notes = "tlv-test",
            updatedAt = now
        )
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.MESSAGE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = now,
                senderId = senderId
            ),
            payload = payload
        )
        gossipSyncManager.onPublicPacketSeen(packet)
        protocolCore.broadcast(packet)
        _uiEvents.tryEmit(UiEvent.Toast("TLV 테스트 패킷 전송"))
    }

    fun sendProfileUpdate(profile: SurvivorProfile) {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(
            name = profile.name,
            gender = mapGender(profile.gender),
            birthDate = profile.birthDate,
            notes = profile.notes,
            updatedAt = now
        )
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.MESSAGE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = now,
                senderId = senderId
            ),
            payload = payload
        )
        gossipSyncManager.onPublicPacketSeen(packet)
        protocolCore.broadcast(packet)
    }

    fun onDisconnect() {
        if (_uiState.value.isDisconnecting) return
        _uiState.update { it.copy(isDisconnecting = true) }

        sendLeavePacket()
        stopRescueSignal()

        viewModelScope.launch {
            delay(200)
            bleManager.disconnect()
            _uiState.update { it.copy(isDisconnecting = false) }
        }
    }

    fun sendLeaveOnShutdown() {
        if (!::protocolCore.isInitialized) return
        sendLeavePacket()
    }

    fun stopServicesForShutdown() {
        if (::bleManager.isInitialized) {
            bleManager.stopAdvertising()
            bleManager.disconnect()
        }
        try {
            val intent = Intent(app, RescueService::class.java).apply {
                action = RescueService.ACTION_STOP_RESCUE
            }
            app.startService(intent)
        } catch (e: Exception) {
            Log.e("AppViewModel", "서비스 종료 실패: ${e.message}")
        }
    }

    private fun initAudio() {
        try {
            audioEngine = AudioEngine()
        } catch (e: Exception) {
            _uiEvents.tryEmit(UiEvent.Toast("오디오 초기화 실패"))
        }
    }

    private fun initBle() {
        bleManager = BleManager(
            app,
            logCallback = { msg -> Log.d("BleManager", msg) },
            audioCallback = { pcmData -> audioEngine?.playAudio(pcmData) },
            textCallback = { textMsg -> addMessage(ChatMessage(text = textMsg, isMine = false)) },
            protocolCallback = { _, _ -> },
            connectionCallback = { connected, count ->
                val directPeerIds = bleManager.getConnectedPeerIds()
                if (connected) {
                    val now = System.currentTimeMillis()
                    if (now - lastConnectionAnnounceMs >= connectionAnnounceCooldownMs) {
                        sendAnnounce()
                        lastConnectionAnnounceMs = now
                    }
                }
                val newPeers = directPeerIds.filterNot { announcedToPeers.contains(it) }
                if (newPeers.isNotEmpty()) {
                    announcedToPeers.addAll(newPeers)
                    newPeers.forEach { peerId ->
                        gossipSyncManager.scheduleInitialSyncToPeer(hexToBytes(peerId), 1_000L)
                    }
                }
                announcedToPeers.retainAll(directPeerIds.toSet())
                _uiState.update {
                    val meshCount = meshGraphRegistry.countNodes().coerceAtLeast(1)
                    it.copy(
                        isConnected = connected,
                        connectedCount = count,
                        meshPeerCount = meshCount,
                        directPeerIds = directPeerIds
                    )
                }
            }
        )
        bleManager.setLocalPeerId(senderId)

        bleManager.onRescueConnected = null

        // 모드 변경 알림
        bleManager.onModeChange = { message ->
            _uiEvents.tryEmit(UiEvent.Toast(message))
        }

        protocolCore.attachTransport(BleTransport(bleManager))
        startBleDebugLoop()
    }

    private fun initProtocol() {
        val codec = BinaryPacketCodec()
        signatureManager = SignatureManager(app, codec, ::appendSignatureLog)
        senderId = loadOrCreatePeerId(signatureManager)
        protocolCore = ProtocolCore(
            codec,
            codec,
            myPeerId = senderId,
            signatureManager = signatureManager
        )
        gossipSyncManager = GossipSyncManager(
            myPeerId = senderId,
            scope = viewModelScope,
            sender = object : GossipSyncManager.Sender {
                override fun broadcast(packet: Packet) {
                    protocolCore.broadcast(packet)
                }

                override fun sendToPeer(peerId: ByteArray, packet: Packet) {
                    protocolCore.send(packet)
                }
            }
        )
        profilePacketHandler = ProfilePacketHandler(
            profileDao = profileDao,
            scope = viewModelScope,
            logSink = ::appendProfileLog,
            onPublicPacketSeen = { packet -> gossipSyncManager.onPublicPacketSeen(packet) }
        )
        protocolCore.setOnPacketReceived { packet, relayAddress ->
            if (packet.header.senderId.contentEquals(senderId)) return@setOnPacketReceived

            val pathLabel = packetPathLabel(packet, relayAddress)
            val peerHex = bytesToHex(packet.header.senderId)
            emitMeshActivity(peerHex)
            if (packet.header.type != PacketType.LEAVE && packet.header.type != PacketType.ANNOUNCE) {
                meshGraphRegistry.touchPeer(peerHex, peerNicknames[peerHex], System.currentTimeMillis())
            }
            if (relayAddress != null && pathLabel == "direct") {
                bleManager.bindPeerIdForAddress(relayAddress, peerHex)
                bleManager.onAnnounceReceived(relayAddress)
                meshGraphRegistry.touchPeer(peerHex, peerNicknames[peerHex], System.currentTimeMillis())
                refreshDirectPeers()
            }
            when (packet.header.type) {
                PacketType.ANNOUNCE -> {
                    val now = System.currentTimeMillis()
                    val age = now - packet.header.timestamp
                    if (age > ProtocolConstants.Mesh.PEER_TIMEOUT_MS) return@setOnPacketReceived
                    val flags = packet.header.flags
                    val isWifiCapable = (flags and 0x01) != 0
                    val isUwbCapable = (flags and 0x02) != 0
                    val isWifiDirectCapable = (flags and 0x04) != 0
                    val announcement = IdentityAnnouncementPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    val decision = peerIdentityRegistry.handleAnnounce(
                        peerId = peerHex,
                        nickname = announcement.nickname,
                        noisePublicKey = announcement.noisePublicKey,
                        now = now,
                        duplicateNicknameStaleMs = ProtocolConstants.Mesh.DUPLICATE_NICKNAME_STALE_MS
                    )
                    if (!decision.accept) return@setOnPacketReceived
                    decision.removedPeerIds.forEach { removedPeerId ->
                        meshGraphRegistry.removePeer(removedPeerId)
                        gossipSyncManager.removeAnnouncementForPeer(removedPeerId)
                        announcedToPeers.remove(removedPeerId)
                        peerNicknames.remove(removedPeerId)
                    }
                    if (announcement.nickname.isNotBlank()) {
                        peerNicknames[peerHex] = announcement.nickname
                        _uiState.update { it.copy(peerNicknames = peerNicknames.toMap()) }
                    }
                    if (relayAddress != null && packet.header.ttl >= ProtocolConstants.MESSAGE_TTL_HOPS) {
                        bleManager.bindPeerIdForAddress(relayAddress, peerHex)
                        bleManager.onAnnounceReceived(relayAddress)
                    }
                    val neighbors = GossipTlv.decodeNeighborsFromAnnouncementPayload(packet.payload)
                    meshGraphRegistry.updateFromAnnouncement(
                        originPeerId = peerHex,
                        originNickname = announcement.nickname,
                        neighborsOrNull = neighbors,
                        timestamp = packet.header.timestamp
                    )
                    meshRegistry.updatePeer(
                        peerIdHex = peerHex,
                        isWifiAware = isWifiCapable,
                        isWifiDirect = isWifiDirectCapable,
                        isUwb = isUwbCapable
                    )
                    refreshSurvivorCapabilities()
                    updateMeshCount()
                    gossipSyncManager.onPublicPacketSeen(packet)
                }
                PacketType.LEAVE -> {
                    meshGraphRegistry.removePeer(peerHex)
                    meshRegistry.remove(peerHex)
                    peerIdentityRegistry.removePeer(peerHex)
                    gossipSyncManager.removeAnnouncementForPeer(peerHex)
                    announcedToPeers.remove(peerHex)
                    if (peerNicknames.remove(peerHex) != null) {
                        _uiState.update { it.copy(peerNicknames = peerNicknames.toMap()) }
                    }
                    updateMeshCount()
                    refreshSurvivorCapabilities()
                }
                PacketType.MESSAGE -> {
                    val profileResult = ProfileTlv.decodeIfProfile(packet.payload)
                    if (profileResult != null) {
                        profilePacketHandler.handle(packet, profileResult, pathLabel)
                    } else {
                        val text = packet.payload.toString(Charsets.UTF_8)
                        addMessage(ChatMessage(text = text, isMine = false, path = pathLabel))
                        if (packet.header.recipientId == null) {
                            gossipSyncManager.onPublicPacketSeen(packet)
                        }
                    }
                }
                PacketType.FILE_TRANSFER -> {
                    viewModelScope.launch(Dispatchers.IO) {
                        val payload = FileTransferPayload.decode(packet.payload) ?: return@launch
                        val stored = FileTransferStorage.storeIncoming(
                            context = app,
                            payload = payload,
                            timestamp = packet.header.timestamp
                        ) ?: return@launch
                        addMessage(
                            ChatMessage(
                                text = FileTransferStorage.buildMarker(stored),
                                isMine = false,
                                path = pathLabel
                            )
                        )
                        if (packet.header.recipientId != null) {
                            val transferId = packet.payload.sha256Bytes()
                            protocolCore.sendFileAck(packet.header.senderId, transferId)
                        }
                    }
                }
                PacketType.REQUEST_SYNC -> {
                    val request = RequestSyncPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    gossipSyncManager.handleRequestSync(packet.header.senderId, request)
                }
                PacketType.CALL_HANDSHAKE -> {
                    val payload = CallHandshakePayload.decode(packet.payload) ?: return@setOnPacketReceived
                    val recipientHex = packet.header.recipientId?.let { bytesToHex(it) }
                    val myHex = bytesToHex(senderId)
                    if (recipientHex != null && recipientHex != myHex) return@setOnPacketReceived
                    handleCallHandshake(peerHex, payload)
                }
                else -> Unit
            }
        }
        startAnnounceLoop()
        startMeshCleanupLoop()
        gossipSyncManager.start()
    }

    private fun initBatteryMonitor() {
        val intent = app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let { updateBatteryLevel(it) }
    }

    private fun updateBatteryLevel(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            val percent = (level * 100 / scale)
            _uiState.update { it.copy(batteryLevel = percent) }
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { current ->
            current.copy(messages = current.messages + message)
        }
    }

    private fun updateMeshCount() {
        val meshCount = meshGraphRegistry.countNodes().coerceAtLeast(1)
        _uiState.update { it.copy(meshPeerCount = meshCount) }
    }

    private fun emitMeshActivity(peerId: String) {
        _meshVisualEvents.tryEmit(MeshVisualEvent.PacketActivity(peerId))
    }

    private fun refreshDirectPeers() {
        val directPeerIds = bleManager.getConnectedPeerIds()
        val nickname = cachedNickname.ifBlank { bytesToHex(senderId) }
        meshGraphRegistry.updateFromAnnouncement(
            originPeerId = bytesToHex(senderId),
            originNickname = nickname,
            neighborsOrNull = directPeerIds,
            timestamp = System.currentTimeMillis()
        )
        val newPeers = directPeerIds.filterNot { announcedToPeers.contains(it) }
        if (newPeers.isNotEmpty()) {
            sendAnnounce()
            announcedToPeers.addAll(newPeers)
            newPeers.forEach { peerId ->
                gossipSyncManager.scheduleInitialSyncToPeer(hexToBytes(peerId), 1_000L)
            }
        }
        announcedToPeers.retainAll(directPeerIds.toSet())
        _uiState.update {
            val meshCount = meshGraphRegistry.countNodes().coerceAtLeast(1)
            it.copy(
                isConnected = directPeerIds.isNotEmpty(),
                connectedCount = directPeerIds.size,
                meshPeerCount = meshCount,
                directPeerIds = directPeerIds
            )
        }
    }

    private fun startAnnounceLoop() {
        if (announceJob?.isActive == true) return
        announceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(ProtocolConstants.Mesh.ANNOUNCE_INITIAL_DELAY_MS)
            while (true) {
                sendAnnounce()
                delay(ProtocolConstants.Mesh.ANNOUNCE_INTERVAL_MS)
            }
        }
    }

    private fun startMeshCleanupLoop() {
        if (meshCleanupJob?.isActive == true) return
        meshCleanupJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                meshGraphRegistry.prune(ProtocolConstants.Mesh.PEER_TIMEOUT_MS)
                updateMeshCount()
                delay(ProtocolConstants.Mesh.PEER_CLEANUP_INTERVAL_MS)
            }
        }
    }

    private fun startBleDebugLoop() {
        if (bleDebugJob?.isActive == true) return
        bleDebugJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = bleManager.getDebugSnapshot()
                _uiState.update { it.copy(bleDebug = BleDebugStats.fromSnapshot(snapshot)) }
                delay(3_000L)
            }
        }
    }

    private fun sendAnnounce() {
        val nickname = cachedNickname.ifBlank { bytesToHex(senderId) }
        val noisePublicKey = signatureManager.getNoisePublicKeyBytes()
        val signingPublicKey = signatureManager.getPublicKeyBytes()
        val announcement = IdentityAnnouncementPayload(
            nickname = nickname,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey
        )
        val basePayload = announcement.encode() ?: return
        val directPeers = bleManager.getConnectedPeerIds()
        val payload = if (directPeers.isNotEmpty()) {
            basePayload + GossipTlv.encodeNeighbors(directPeers)
        } else {
            basePayload
        }
        val now = System.currentTimeMillis()
        meshGraphRegistry.updateFromAnnouncement(
            originPeerId = bytesToHex(senderId),
            originNickname = nickname,
            neighborsOrNull = directPeers,
            timestamp = now
        )
        updateMeshCount()
        val currentFlags = getCurrentCapabilityFlags()
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.ANNOUNCE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = currentFlags,
                length = payload.size,
                timestamp = now,
                senderId = senderId
            ),
            payload = payload
        )
        val signedPacket = signatureManager.sign(packet)
        gossipSyncManager.onPublicPacketSeen(signedPacket)
        protocolCore.broadcast(signedPacket)
    }

    private fun observeProfileName() {
        viewModelScope.launch {
            profileStore.profileFlow.collect { profile ->
                cachedNickname = profile.name.trim()
                _uiState.update { it.copy(myNickname = cachedNickname) }
            }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            profileDao.getAll().collect { entities ->
                discoveredSurvivors.clear()
                entities.forEach { entity ->
                    discoveredSurvivors[entity.peerId] = SurvivorProfile(
                        name = entity.name,
                        gender = entity.gender,
                        birthDate = entity.birthDate,
                        notes = entity.notes,
                        peerId = entity.peerId
                    )
                }
                refreshSurvivorCapabilities()
            }
        }
    }

    private fun refreshSurvivorCapabilities() {
        _uiState.update { state ->
            val mergedList = discoveredSurvivors.map { (peerId, profile) ->
                val peerInfo = meshRegistry.getPeer(peerId)
                profile.copy(
                    isWifiAware = peerInfo?.isWifiAware ?: profile.isWifiAware,
                    isWifiDirect = peerInfo?.isWifiDirect ?: profile.isWifiDirect,
                    isUwb = peerInfo?.isUwb ?: profile.isUwb,
                    peerId = peerId
                )
            }
            state.copy(
                meshPeerCount = meshGraphRegistry.countNodes().coerceAtLeast(1),
                survivors = mergedList
            )
        }
    }

    private fun observeCallConnection() {
        viewModelScope.launch {
            wifiDirectRanger.isConnectionReady.collect { ready ->
                _uiState.update { it.copy(isCallConnected = ready) }
            }
        }
    }

    private fun isLocalWifiAwareSupported(): Boolean {
        return (getCurrentCapabilityFlags() and 0x01) != 0
    }

    fun isWifiAwareSupportedLocally(): Boolean {
        val supported = isLocalWifiAwareSupported()
        val pm = app.packageManager
        val awareFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        } else {
            false
        }
        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled
        } else {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            providers.isNotEmpty()
        }
        val awareManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
        } else {
            null
        }
        val awareAvailable = awareManager?.isAvailable
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasFine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(
            "AppViewModel",
            "Local Wi-Fi Aware supported=$supported feature=$awareFeature available=$awareAvailable wifiEnabled=$wifiEnabled locationEnabled=$locationEnabled nearby=$hasNearby fine=$hasFine coarse=$hasCoarse"
        )
        return supported
    }

    fun isWifiDirectSupportedLocally(): Boolean {
        val supported = (getCurrentCapabilityFlags() and 0x04) != 0
        val pm = app.packageManager
        val directFeature = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled
        } else {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            providers.isNotEmpty()
        }
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasFine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(
            "AppViewModel",
            "Local Wi-Fi Direct supported=$supported feature=$directFeature wifiEnabled=$wifiEnabled locationEnabled=$locationEnabled nearby=$hasNearby fine=$hasFine coarse=$hasCoarse"
        )
        return supported
    }

    private fun getCurrentCapabilityFlags(): Int {
        var flags = 0
        val pm = app.packageManager
        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled == true
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            providers.isNotEmpty()
        }
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasFine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasLocationPermission = hasFine || hasCoarse

        val awareFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        } else {
            false
        }
        val awareManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.getSystemService(Context.WIFI_AWARE_SERVICE) as? android.net.wifi.aware.WifiAwareManager
        } else {
            null
        }
        val awareAvailable = awareManager?.isAvailable == true
        val awareReady = awareFeature && wifiEnabled && locationEnabled && hasNearby && hasLocationPermission && awareAvailable
        if (awareReady) {
            flags = flags or 0x01
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pm.hasSystemFeature("android.hardware.uwb")) {
            flags = flags or 0x02
        }

        val directFeature = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val directReady = directFeature && wifiEnabled && locationEnabled && hasNearby && hasLocationPermission
        if (directReady) {
            flags = flags or 0x04
        }

        Log.d(
            "AppViewModel",
            "Capability Flags(now): $flags (Aware=$awareReady, Direct=$directReady, UWB=${flags and 2 != 0})"
        )
        return flags
    }

    private fun observeMeshGraph() {
        viewModelScope.launch {
            meshGraphRegistry.graphState.collect { snapshot ->
                val meshCount = snapshot.nodes.size.coerceAtLeast(1)
                _uiState.update {
                    it.copy(
                        meshGraphSnapshot = snapshot,
                        meshPeerCount = meshCount
                    )
                }
            }
        }
    }

    fun clearSignatureLogs() {
        signatureLogBuffer.clear()
        _uiState.update { it.copy(signatureLogs = emptyList()) }
    }

    fun clearProfileLogs() {
        profileLogBuffer.clear()
        _uiState.update { it.copy(profileLogs = emptyList()) }
    }

    fun clearDeviceMonitoring() {
        if (::bleManager.isInitialized) {
            bleManager.clearAllConnectionsAndMappings()
            _uiEvents.tryEmit(UiEvent.Toast("연결/차단 목록 초기화됨"))
        }
    }

    private fun appendSignatureLog(entry: SignatureLogEntry) {
        signatureLogBuffer.addLast(entry)
        while (signatureLogBuffer.size > MAX_SIGNATURE_LOGS) {
            signatureLogBuffer.removeFirst()
        }
        _uiState.update { it.copy(signatureLogs = signatureLogBuffer.toList()) }
    }

    private fun appendProfileLog(entry: ProfileSyncLogEntry) {
        profileLogBuffer.addLast(entry)
        while (profileLogBuffer.size > MAX_PROFILE_LOGS) {
            profileLogBuffer.removeFirst()
        }
        _uiState.update { it.copy(profileLogs = profileLogBuffer.toList()) }
    }

    private fun loadOrCreatePeerId(manager: SignatureManager): ByteArray {
        val derived = derivePeerId(manager)
        val savedHex = prefs.getString("sender_id", null)
        if (savedHex != null) {
            val saved = runCatching { hexToBytes(savedHex) }.getOrNull()
            if (saved != null && saved.contentEquals(derived)) {
                return saved
            }
            prefs.edit().putString("sender_id", bytesToHex(derived)).apply()
            Log.w("AppViewModel", "sender_id mismatch; reset to derived id")
            return derived
        }
        prefs.edit().putString("sender_id", bytesToHex(derived)).apply()
        return derived
    }

    private fun derivePeerId(manager: SignatureManager): ByteArray {
        val noiseKey = manager.getNoisePublicKeyBytes()
        val hash = noiseKey.sha256Bytes()
        return hash.copyOfRange(0, 8)
    }

    private fun buildMessagePacket(text: String): Packet {
        val payload = text.toByteArray(Charsets.UTF_8)
        val header = PacketHeader(
            version = 2,
            type = PacketType.MESSAGE,
            ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
            flags = 0,
            length = payload.size,
            timestamp = System.currentTimeMillis(),
            senderId = senderId
        )
        return Packet(header = header, payload = payload)
    }

    private fun sendLeavePacket() {
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.LEAVE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = 0,
                timestamp = System.currentTimeMillis(),
                senderId = senderId
            ),
            payload = ByteArray(0)
        )
        protocolCore.broadcast(packet)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun mapGender(raw: String): Char {
        return when (raw.trim()) {
            "남성", "M", "m", "male", "Male" -> 'M'
            "여성", "F", "f", "female", "Female" -> 'F'
            else -> 'U'
        }
    }

    private fun packetPathLabel(packet: Packet, relayAddress: String?): String {
        if (relayAddress == null) return "unknown"
        val baseTtl = when (packet.header.type) {
            PacketType.REQUEST_SYNC,
            PacketType.FILE_ACK -> ProtocolConstants.SYNC_TTL_HOPS
            else -> ProtocolConstants.MESSAGE_TTL_HOPS
        }
        return if (packet.header.ttl >= baseTtl) "direct" else "mesh"
    }

    private companion object {
        const val MAX_SIGNATURE_LOGS = 200
        const val MAX_PROFILE_LOGS = 200
    }

    override fun onCleared() {
        AppShutdownHooks.clear()
        audioEngine?.stopRecording()
        voiceRecorder?.stop()
        toneGenerator.release()
        wifiAwareRanger.stop()
        wifiDirectRanger.stop()
        announceJob?.cancel()
        meshCleanupJob?.cancel()
        bleDebugJob?.cancel()
        if (::gossipSyncManager.isInitialized) {
            gossipSyncManager.stop()
        }

        // [수정] BleManager 리소스 완전 해제 (메모리 릭 방지)
        if (::bleManager.isInitialized) {
            bleManager.release()
        }

        // 앱 종료 시 서비스도 같이 종료
        val intent = Intent(app, RescueService::class.java).apply {
            action = RescueService.ACTION_STOP_RESCUE
        }
        app.startService(intent)

        try {
            app.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) { }
        super.onCleared()
    }
}
