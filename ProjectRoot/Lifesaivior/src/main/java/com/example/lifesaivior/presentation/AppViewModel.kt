package com.example.lifesaivior.presentation

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifesaivior.core.audio.AudioEngine
import com.example.lifesaivior.core.audio.VoiceRecorder
import com.example.lifesaivior.core.ble.BleDebugSnapshot
import com.example.lifesaivior.core.ble.BleManager
import com.example.lifesaivior.core.ble.BleTransport
import com.example.lifesaivior.core.database.AppDatabase
import com.example.lifesaivior.core.log.ConnectionLog
import com.example.lifesaivior.core.media.FileTransferStorage
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.core.profile.ProfileStore
import com.example.lifesaivior.core.profile.SurvivorProfile
import com.example.lifesaivior.core.service.RescueService
import com.example.lifesaivior.core.settings.AppSettingsRepository
import com.example.lifesaivior.core.uwb.UwbRanger
import com.example.lifesaivior.core.wifi.WifiAwareRanger
import com.example.lifesaivior.core.wifi.WifiDirectRanger
import com.example.lifesaivior.presentation.packet.ProfilePacketHandler
import com.example.lifesaivior.protocol.codec.BinaryPacketCodec
import com.example.lifesaivior.protocol.core.ProtocolConstants
import com.example.lifesaivior.protocol.core.ProtocolCore
import com.example.lifesaivior.protocol.mesh.DirectPeerAnnounceTracker
import com.example.lifesaivior.protocol.mesh.GossipTlv
import com.example.lifesaivior.protocol.mesh.MeshGraphRegistry
import com.example.lifesaivior.protocol.mesh.MeshPeerRegistry
import com.example.lifesaivior.protocol.mesh.PeerIdentityRegistry
import com.example.lifesaivior.protocol.model.*
import com.example.lifesaivior.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaivior.protocol.profile.ProfileTlv
import com.example.lifesaivior.protocol.security.SignatureLogEntry
import com.example.lifesaivior.protocol.security.SignatureManager
import com.example.lifesaivior.protocol.sync.GossipSyncManager
import com.example.lifesaivior.protocol.util.sha256Bytes
import com.example.lifesaivior.wakeup.SensorService
import com.example.lifesaivior.wakeup.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val peerDirectAddresses: Map<String, String> = emptyMap(),
    val meshGraphSnapshot: MeshGraphRegistry.GraphSnapshot = MeshGraphRegistry.GraphSnapshot(
        emptyList(),
        emptyList()
    ),
    val isMicOn: Boolean = false,
    val isDisconnecting: Boolean = false,
    val isAutoConnectBlocked: Boolean = false,
    val isRescueSignalActive: Boolean = false,
    // [추가] 설정 상태 관리
    val isVoiceDetectionEnabled: Boolean = false,
    val isShockDetectionEnabled: Boolean = false,
    val isDemoModeEnabled: Boolean = false,
    val demoBeepLevel: Int = 100,
    val demoHighToneLevel: Int = 100,
    val demoVibrateLevel: Int = 100,
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
    val incomingCallState: CallHandshakeState? = null,
    val incomingCallRttCm: Int? = null,
    val incomingCallDirectAddress: String? = null,
    val isCallConnected: Boolean = false,
    val callPeerWifiAware: Boolean? = null,
    val callPeerWifiDirect: Boolean? = null,
    val callPeerUseOpus: Boolean? = null,
    val callPeerId: String? = null,
    val callPeerState: CallHandshakeState? = null,
    val callPeerRttCm: Int? = null,
    val callPeerDirectAddress: String? = null
)

// UI 이벤트
sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}

data class AutoSosTrigger(
    val triggeredAtMs: Long,
    val reason: String?
)

private data class AlertToneRequest(
    val command: DeviceControlCommand,
    val durationMs: Int,
    val intensity: Int,
    val frequencyHz: Int? = null,
    val enqueuedAtMs: Long = System.currentTimeMillis()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val forcePcmCall = false
    private val wifiAwareEnabled = true
    private val wifiDirectEnabled = false

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    private val _pendingAutoSos = MutableStateFlow<AutoSosTrigger?>(null)
    val pendingAutoSos: StateFlow<AutoSosTrigger?> = _pendingAutoSos.asStateFlow()

    private val _meshVisualEvents = MutableSharedFlow<MeshVisualEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val meshVisualEvents: SharedFlow<MeshVisualEvent> = _meshVisualEvents.asSharedFlow()
    private val _remotePowerSaveExitEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val remotePowerSaveExitEvents: SharedFlow<Long> = _remotePowerSaveExitEvents.asSharedFlow()
    private val _remotePowerSaveSetEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val remotePowerSaveSetEvents: SharedFlow<Boolean> = _remotePowerSaveSetEvents.asSharedFlow()

    // 권한 목록
    val requiredPermissions: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.UWB_RANGING)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.FOREGROUND_SERVICE)
    }.toTypedArray()

    private var audioEngine: AudioEngine? = null
    val wifiAwareRanger = WifiAwareRanger(app)
    val wifiDirectRanger = WifiDirectRanger(app)
    private val uwbRanger = UwbRanger(app)
    private lateinit var bleManager: BleManager
    private lateinit var protocolCore: ProtocolCore
    private lateinit var signatureManager: SignatureManager
    private lateinit var gossipSyncManager: GossipSyncManager
    private lateinit var profilePacketHandler: ProfilePacketHandler
    private val signatureLogBuffer = ArrayDeque<SignatureLogEntry>()
    private val profileLogBuffer = ArrayDeque<ProfileSyncLogEntry>()
    private val profileDao by lazy { AppDatabase.getInstance(app).profileDao() }

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val alarmVolumeLock = Any()
    @Volatile private var alarmVolumeBackup: Int? = null
    private val alertQueueMutex = Mutex()
    private val beepQueue = ArrayDeque<AlertToneRequest>()
    private val highToneQueue = ArrayDeque<AlertToneRequest>()
    @Volatile private var lastAlertType: DeviceControlCommand? = null
    @Volatile private var lastBeepEnqueueAtMs: Long = 0L
    @Volatile private var lastHighEnqueueAtMs: Long = 0L
    private var alertPlaybackJob: Job? = null

    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    private var senderId: ByteArray = ByteArray(0)
    private val profileStore = ProfileStore(app)
    @Volatile private var cachedProfile: SurvivorProfile = SurvivorProfile()
    @Volatile private var cachedNickname: String = ""
    @Volatile private var autoConnectBlocked: Boolean = false
    private val directPeerAnnounceTracker = DirectPeerAnnounceTracker()
    private val peerNicknames = mutableMapOf<String, String>()

    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null
    @Volatile private var localPowerSavingEnabled: Boolean = false
    private val meshGraphRegistry = MeshGraphRegistry()
    private val peerIdentityRegistry = PeerIdentityRegistry()
    private val meshRegistry = MeshPeerRegistry()
    private val peerDirectAddresses = ConcurrentHashMap<String, String>()
    private val discoveredSurvivors = mutableMapOf<String, SurvivorProfile>()
    private val announcedProfiles = ConcurrentHashMap<String, SurvivorProfile>()
    private val announcedPeerLastSeen = ConcurrentHashMap<String, Long>()
    private var announceJob: kotlinx.coroutines.Job? = null
    private var meshCleanupJob: kotlinx.coroutines.Job? = null
    private var bleDebugJob: kotlinx.coroutines.Job? = null
    private var highToneJob: Job? = null
    private var lastProfileBroadcastFingerprint: String = ""
    private var lastProfileBroadcastAtMs: Long = 0L
    private val profileBroadcastCooldownMs: Long = 2_000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryLevel(it) }
        }
    }

    init {
        AppSettingsRepository.init(app)
        initProtocol()
        initBle()
        initBatteryMonitor()
        refreshPermissions()
        wifiDirectRanger.refreshLocalDeviceAddress()
        observeProfileState()
        observeProfiles()
        observeMeshGraph()
        observeCallConnection()

        // [초기화] 저장된 설정 불러오기
        val settings = AppSettingsRepository.snapshot(app)
        val effectiveVoice = if (settings.isDemoModeEnabled) true else settings.isVoiceDetectionEnabled
        val effectiveShock = if (settings.isDemoModeEnabled) true else settings.isShockDetectionEnabled
        _uiState.update {
            it.copy(
                myPeerId = bytesToHex(senderId),
                isVoiceDetectionEnabled = effectiveVoice,
                isShockDetectionEnabled = effectiveShock,
                isDemoModeEnabled = settings.isDemoModeEnabled,
                demoBeepLevel = settings.demoBeepLevel,
                demoHighToneLevel = settings.demoHighToneLevel,
                demoVibrateLevel = settings.demoVibrateLevel
            )
        }

        viewModelScope.launch {
            AppSettingsRepository.state.collect { latest ->
                val voiceEnabled = if (latest.isDemoModeEnabled) true else latest.isVoiceDetectionEnabled
                val shockEnabled = if (latest.isDemoModeEnabled) true else latest.isShockDetectionEnabled
                _uiState.update { state ->
                    state.copy(
                        isVoiceDetectionEnabled = voiceEnabled,
                        isShockDetectionEnabled = shockEnabled,
                        isDemoModeEnabled = latest.isDemoModeEnabled,
                        demoBeepLevel = latest.demoBeepLevel,
                        demoHighToneLevel = latest.demoHighToneLevel,
                        demoVibrateLevel = latest.demoVibrateLevel
                    )
                }
            }
        }

        AppShutdownHooks.register(
            onSendLeave = { sendLeaveOnShutdown() },
            onStopServices = { stopServicesForShutdown() }
        )
    }

    // ------------------------------------------------------------------------
    // [추가] 설정 제어 및 서비스 실행 로직
    // ------------------------------------------------------------------------

    fun setVoiceDetection(enabled: Boolean) {
        if (!enabled && AppSettingsRepository.state.value.isDemoModeEnabled) {
            _uiEvents.tryEmit(UiEvent.Toast("시연 모드에서는 음성 감지를 끌 수 없습니다."))
            return
        }
        AppSettingsRepository.setVoiceDetection(app, enabled)
        _uiState.update { it.copy(isVoiceDetectionEnabled = enabled) }

        if (enabled) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceSafe(VoiceService::class.java)
                _uiEvents.tryEmit(UiEvent.Toast("음성 감지 켜짐"))
            } else {
                _uiEvents.tryEmit(UiEvent.Toast("마이크 권한이 필요합니다."))
                // 권한이 없으면 다시 끔
                _uiState.update { it.copy(isVoiceDetectionEnabled = false) }
                AppSettingsRepository.setVoiceDetection(app, false)
            }
        } else {
            app.stopService(Intent(app, VoiceService::class.java))
            _uiEvents.tryEmit(UiEvent.Toast("음성 감지 꺼짐"))
        }
    }

    fun setShockDetection(enabled: Boolean) {
        if (!enabled && AppSettingsRepository.state.value.isDemoModeEnabled) {
            _uiEvents.tryEmit(UiEvent.Toast("시연 모드에서는 충격 감지를 끌 수 없습니다."))
            return
        }
        AppSettingsRepository.setShockDetection(app, enabled)
        _uiState.update { it.copy(isShockDetectionEnabled = enabled) }

        if (enabled) {
            startServiceSafe(SensorService::class.java)
            _uiEvents.tryEmit(UiEvent.Toast("충격 감지 켜짐"))
        } else {
            app.stopService(Intent(app, SensorService::class.java))
            _uiEvents.tryEmit(UiEvent.Toast("충격 감지 꺼짐"))
        }
    }

    fun onAutoSosTriggered(reason: String?) {
        _pendingAutoSos.value = AutoSosTrigger(
            triggeredAtMs = System.currentTimeMillis(),
            reason = reason
        )
    }

    fun consumeAutoSosTrigger() {
        _pendingAutoSos.value = null
    }

    fun setDemoMode(enabled: Boolean) {
        AppSettingsRepository.setDemoMode(app, enabled)
        _uiState.update { it.copy(isDemoModeEnabled = enabled) }

        if (enabled) {
            app.stopService(Intent(app, VoiceService::class.java))
            setVoiceDetection(true)
            setShockDetection(true)
        } else {
            setVoiceDetection(false)
            setShockDetection(false)
        }
    }

    fun setDemoBeepLevel(level: Int) {
        val safe = level.coerceIn(0, 100)
        AppSettingsRepository.setDemoBeepLevel(app, safe)
        _uiState.update { it.copy(demoBeepLevel = safe) }
    }

    fun setDemoHighToneLevel(level: Int) {
        val safe = level.coerceIn(0, 100)
        AppSettingsRepository.setDemoHighToneLevel(app, safe)
        _uiState.update { it.copy(demoHighToneLevel = safe) }
    }

    fun setDemoVibrateLevel(level: Int) {
        val safe = level.coerceIn(0, 100)
        AppSettingsRepository.setDemoVibrateLevel(app, safe)
        _uiState.update { it.copy(demoVibrateLevel = safe) }
    }

    private fun suspendBackgroundForSos() {
        val settings = AppSettingsRepository.snapshot(app)
        if (settings.isSosBackgroundSuspended) return

        AppSettingsRepository.setSosBackgroundSuspended(
            context = app,
            suspended = true,
            backupVoice = settings.isVoiceDetectionEnabled,
            backupShock = settings.isShockDetectionEnabled,
            backupDemo = settings.isDemoModeEnabled
        )

        app.stopService(Intent(app, VoiceService::class.java))
        app.stopService(Intent(app, SensorService::class.java))
    }

    private fun restoreBackgroundAfterSos() {
        val settings = AppSettingsRepository.snapshot(app)
        if (!settings.isSosBackgroundSuspended) return

        AppSettingsRepository.setSosBackgroundSuspended(app, false)
        AppSettingsRepository.clearSosBackup(app)

        val isDemoOn = settings.isDemoModeEnabled
        val shouldVoiceOn = settings.isVoiceDetectionEnabled || isDemoOn
        val shouldShockOn = settings.isShockDetectionEnabled || isDemoOn

        if (shouldVoiceOn && ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startServiceSafe(VoiceService::class.java)
        } else {
            app.stopService(Intent(app, VoiceService::class.java))
        }

        if (shouldShockOn) {
            startServiceSafe(SensorService::class.java)
        } else {
            app.stopService(Intent(app, SensorService::class.java))
        }
    }

    private fun startServiceSafe(serviceClass: Class<*>) {
        try {
            val intent = Intent(app, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "서비스 시작 실패: ${e.message}")
            _uiEvents.tryEmit(UiEvent.Toast("서비스 시작 오류: ${e.message}"))
        }
    }

    // ------------------------------------------------------------------------
    // [기존 로직 유지]
    // ------------------------------------------------------------------------

    fun startRescueSignal() {
        if (!_uiState.value.hasPermissions) {
            _uiEvents.tryEmit(UiEvent.Toast("블루투스 및 서비스 권한이 필요합니다."))
            return
        }
        suspendBackgroundForSos()
        autoConnectBlocked = false
        ConnectionLog.add("Runtime", "auto-connect unblocked (rescue-start)")
        _uiState.update { it.copy(isAutoConnectBlocked = false) }
        bleManager.startEmergencyAdvertising()
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
        broadcastCachedProfileIfNeeded(force = true, reason = "rescue-start")
    }

    fun pulseRescueSignal() {
        if (!_uiState.value.hasPermissions) return
        if (!::bleManager.isInitialized) return
        bleManager.pulseEmergencyAdvertising()
    }

    fun stopRescueSignal() {
        bleManager.stopAdvertising()
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
        restoreBackgroundAfterSos()
    }

    fun refreshPermissions() {
        val granted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }
        _uiState.update { it.copy(hasPermissions = granted) }

        if (granted) {
            wifiDirectRanger.refreshLocalDeviceAddress()
            if (audioEngine == null) {
                initAudio()
            }
            if (isUwbSupportedLocally()) {
                viewModelScope.launch(Dispatchers.IO) {
                    uwbRanger.prepareControleeAddressBlocking()
                }
            }
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            wifiDirectRanger.refreshLocalDeviceAddress()
            if (audioEngine == null) initAudio()
            if (isUwbSupportedLocally()) {
                viewModelScope.launch(Dispatchers.IO) {
                    uwbRanger.prepareControleeAddressBlocking()
                }
            }
        } else {
            _uiEvents.tryEmit(UiEvent.Toast("필수 권한이 필요합니다."))
        }
    }

    fun onStartAutoConnect() {
        if (autoConnectBlocked) {
            ConnectionLog.add("Runtime", "auto-connect skipped (manual disconnect)")
            return
        }
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
            addMessage(
                ChatMessage(
                    text = "[voice] ${file.absolutePath}",
                    isMine = true,
                    senderName = resolveMyDisplayName(),
                    senderPeerId = bytesToHex(senderId)
                )
            )
        }
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        val packet = buildMessagePacket(text)
        val signedPacket = signatureManager.sign(packet)
        gossipSyncManager.onPublicPacketSeen(signedPacket)
        protocolCore.broadcast(signedPacket)
        addMessage(
            ChatMessage(
                text = text,
                isMine = true,
                senderName = resolveMyDisplayName(),
                senderPeerId = bytesToHex(senderId)
            )
        )
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
                flags = getCurrentCapabilityFlags(),
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
                flags = getCurrentCapabilityFlags(),
                length = payload.size,
                timestamp = now,
                senderId = senderId
            ),
            payload = payload
        )
        gossipSyncManager.onPublicPacketSeen(packet)
        protocolCore.broadcast(packet)
    }

    fun ensureWifiAwarePermissions(): Boolean {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi?.isWifiEnabled == false) {
            _uiEvents.tryEmit(UiEvent.Toast("Wi-Fi를 켜주세요."))
            return false
        }
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = when {
            locationManager == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> locationManager.isLocationEnabled
            else -> {
                val gps = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
                val network = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
                gps || network
            }
        }
        if (!locationEnabled) {
            _uiEvents.tryEmit(UiEvent.Toast("위치 서비스를 켜주세요."))
            return false
        }
        return true
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
        val recipientId = runCatching { hexToBytes(targetPeerIdHex) }.getOrNull() ?: return

        wifiDirectRanger.refreshLocalDeviceAddress()
        val awareSupported = if (wifiAwareEnabled) wifiAwareSupported else false
        val resolvedRttCm = if (wifiAwareEnabled) {
            rttCm ?: wifiAwareRanger.isConnectionReady.value.let { ready ->
                if (!ready) null else wifiAwareRanger.rttDistance.value
            }?.let { distance ->
                (distance * 100f).roundToInt().coerceIn(0, 0xFFFF)
            }
        } else {
            null
        }
        val directAddress = wifiDirectRanger.getLocalDeviceAddress()
        var uwbDeviceAddress: String? = null
        if (isUwbSupportedLocally()) {
            when (action) {
                CallHandshakeAction.START, CallHandshakeAction.ACK -> {
                    uwbDeviceAddress = uwbRanger.getControleeAddressOrNull()
                    if (uwbDeviceAddress == null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            uwbRanger.prepareControleeAddressBlocking()
                        }
                    }
                }

                CallHandshakeAction.UWB_SYNC -> {
                    val cachedAddress = uwbRanger.getControleeAddressOrNull()
                    if (cachedAddress != null) {
                        uwbDeviceAddress = cachedAddress
                    } else {
                        viewModelScope.launch(Dispatchers.IO) {
                            val preparedAddress = uwbRanger.prepareControleeAddressBlocking() ?: return@launch
                            wifiDirectRanger.refreshLocalDeviceAddress()
                            val syncPayload = CallHandshakePayload(
                                action = CallHandshakeAction.UWB_SYNC,
                                callerName = cachedNickname.ifBlank { "생존자" },
                                wifiAwareSupported = false,
                                wifiDirectSupported = false,
                                useOpus = false,
                                directDeviceAddress = wifiDirectRanger.getLocalDeviceAddress(),
                                uwbDeviceAddress = preparedAddress
                            ).encode()
                            protocolCore.broadcast(
                                Packet(
                                    header = PacketHeader(
                                        version = 2,
                                        type = PacketType.CALL_HANDSHAKE,
                                        ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                                        flags = 0,
                                        length = syncPayload.size,
                                        timestamp = System.currentTimeMillis(),
                                        senderId = senderId,
                                        recipientId = recipientId
                                    ),
                                    payload = syncPayload
                                )
                            )
                        }
                        return
                    }
                }

                CallHandshakeAction.END -> {
                    uwbRanger.endSession()
                }
            }
        } else if (action == CallHandshakeAction.END) {
            uwbRanger.endSession()
        }
        val payload = CallHandshakePayload(
            action = action,
            callerName = callerName,
            wifiAwareSupported = awareSupported,
            wifiDirectSupported = wifiDirectSupported,
            useOpus = if (forcePcmCall) false else useOpus,
            state = state,
            rttCm = resolvedRttCm,
            directDeviceAddress = directAddress,
            uwbDeviceAddress = uwbDeviceAddress
        ).encode()
        protocolCore.broadcast(
            Packet(
                header = PacketHeader(
                    version = 2,
                    type = PacketType.CALL_HANDSHAKE,
                    ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                    flags = 0,
                    length = payload.size,
                    timestamp = System.currentTimeMillis(),
                    senderId = senderId,
                    recipientId = recipientId
                ),
                payload = payload
            )
        )
    }

    fun clearIncomingCall(peerIdHex: String) {
        var clearedIncoming = false
        var hasActivePeer = false
        _uiState.update { state ->
            if (state.incomingCallPeerId != peerIdHex) {
                state
            } else {
                clearedIncoming = true
                hasActivePeer = state.callPeerId == peerIdHex
                state.copy(
                    incomingCallPeerId = null,
                    incomingCallName = null,
                    incomingCallWifiAware = false,
                    incomingCallWifiDirect = false,
                    incomingCallUseOpus = false,
                    incomingCallState = null,
                    incomingCallRttCm = null,
                    incomingCallDirectAddress = null
                )
            }
        }
        if (clearedIncoming && !hasActivePeer) {
            uwbRanger.endSession()
        }
    }

    fun clearLocalCallState(peerIdHex: String) {
        var clearPeerState = false
        _uiState.update { state ->
            val clearIncoming = state.incomingCallPeerId == peerIdHex
            val clearPeer = state.callPeerId == peerIdHex
            clearPeerState = clearPeer
            if (!clearIncoming && !clearPeer) {
                state
            } else {
                state.copy(
                    incomingCallPeerId = if (clearIncoming) null else state.incomingCallPeerId,
                    incomingCallName = if (clearIncoming) null else state.incomingCallName,
                    incomingCallWifiAware = if (clearIncoming) false else state.incomingCallWifiAware,
                    incomingCallWifiDirect = if (clearIncoming) false else state.incomingCallWifiDirect,
                    incomingCallUseOpus = if (clearIncoming) false else state.incomingCallUseOpus,
                    incomingCallState = if (clearIncoming) null else state.incomingCallState,
                    incomingCallRttCm = if (clearIncoming) null else state.incomingCallRttCm,
                    incomingCallDirectAddress = if (clearIncoming) null else state.incomingCallDirectAddress,
                    callPeerWifiAware = if (clearPeer) null else state.callPeerWifiAware,
                    callPeerWifiDirect = if (clearPeer) null else state.callPeerWifiDirect,
                    callPeerUseOpus = if (clearPeer) null else state.callPeerUseOpus,
                    callPeerId = if (clearPeer) null else state.callPeerId,
                    callPeerState = if (clearPeer) null else state.callPeerState,
                    callPeerRttCm = if (clearPeer) null else state.callPeerRttCm,
                    callPeerDirectAddress = if (clearPeer) null else state.callPeerDirectAddress
                )
            }
        }
        if (clearPeerState) {
            uwbRanger.endSession()
        }
    }

    private fun handleCallHandshake(peerIdHex: String, payload: CallHandshakePayload) {
        when (payload.action) {
            CallHandshakeAction.START -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wifiAware = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wifiDirect = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                val directFromPayload = cachePeerDirectAddress(
                    peerIdHex = peerIdHex,
                    rawAddress = payload.directDeviceAddress,
                    source = "handshake-start"
                )
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                configureUwbFromHandshake(payload)
                meshRegistry.updatePeer(
                    peerIdHex = peerIdHex,
                    isWifiAware = wifiAware,
                    isWifiDirect = wifiDirect,
                    isUwb = peerUwb,
                    isRescuer = meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
                broadcastCachedProfileIfNeeded(force = true, reason = "call-start")
                _uiState.update {
                    val inActiveCallWithPeer = it.isCallConnected && it.callPeerId == peerIdHex
                    if (inActiveCallWithPeer) {
                        it.copy(
                            callPeerWifiAware = wifiAware,
                            callPeerWifiDirect = wifiDirect,
                            callPeerUseOpus = payload.useOpus,
                            callPeerState = payload.state,
                            callPeerRttCm = payload.rttCm,
                            callPeerDirectAddress = resolvedDirect,
                            peerDirectAddresses = peerDirectAddresses.toMap()
                        )
                    } else {
                        it.copy(
                            incomingCallPeerId = peerIdHex,
                            incomingCallName = payload.callerName ?: "구조자",
                            incomingCallWifiAware = wifiAware,
                            incomingCallWifiDirect = wifiDirect,
                            incomingCallUseOpus = payload.useOpus,
                            incomingCallState = payload.state,
                            incomingCallRttCm = payload.rttCm,
                            incomingCallDirectAddress = resolvedDirect,
                            callPeerWifiAware = wifiAware,
                            callPeerWifiDirect = wifiDirect,
                            callPeerUseOpus = payload.useOpus,
                            callPeerId = peerIdHex,
                            callPeerState = payload.state,
                            callPeerRttCm = payload.rttCm,
                            callPeerDirectAddress = resolvedDirect,
                            peerDirectAddresses = peerDirectAddresses.toMap()
                        )
                    }
                }
            }

            CallHandshakeAction.UWB_SYNC -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wifiAware = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wifiDirect = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                meshRegistry.updatePeer(
                    peerIdHex = peerIdHex,
                    isWifiAware = wifiAware,
                    isWifiDirect = wifiDirect,
                    isUwb = peerUwb,
                    isRescuer = meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
                configureUwbFromHandshake(payload)
                val hasControllerOffer =
                    payload.uwbControllerAddress != null &&
                        payload.uwbControllerChannel != null &&
                        payload.uwbControllerPreambleIndex != null &&
                        payload.uwbSessionId != null
                if (hasControllerOffer && isUwbSupportedLocally()) {
                    sendCallHandshake(
                        targetPeerIdHex = peerIdHex,
                        action = CallHandshakeAction.UWB_SYNC,
                        callerName = cachedNickname.ifBlank { "생존자" },
                        wifiAwareSupported = false,
                        wifiDirectSupported = false,
                        useOpus = false
                    )
                }
            }

            CallHandshakeAction.END -> {
                stopAllRemoteAlerts()
                var ended = false
                _uiState.update { state ->
                    val clearIncoming = state.incomingCallPeerId == peerIdHex
                    val clearPeer = state.callPeerId == peerIdHex
                    if (!clearIncoming && !clearPeer) {
                        state
                    } else {
                        ended = true
                        state.copy(
                            incomingCallPeerId = if (clearIncoming) null else state.incomingCallPeerId,
                            incomingCallName = if (clearIncoming) null else state.incomingCallName,
                            incomingCallWifiAware = if (clearIncoming) false else state.incomingCallWifiAware,
                            incomingCallWifiDirect = if (clearIncoming) false else state.incomingCallWifiDirect,
                            incomingCallUseOpus = if (clearIncoming) false else state.incomingCallUseOpus,
                            incomingCallState = if (clearIncoming) null else state.incomingCallState,
                            incomingCallRttCm = if (clearIncoming) null else state.incomingCallRttCm,
                            incomingCallDirectAddress = if (clearIncoming) null else state.incomingCallDirectAddress,
                            callPeerWifiAware = if (clearPeer) null else state.callPeerWifiAware,
                            callPeerWifiDirect = if (clearPeer) null else state.callPeerWifiDirect,
                            callPeerUseOpus = if (clearPeer) null else state.callPeerUseOpus,
                            callPeerId = if (clearPeer) null else state.callPeerId,
                            callPeerState = if (clearPeer) null else state.callPeerState,
                            callPeerRttCm = if (clearPeer) null else state.callPeerRttCm,
                            callPeerDirectAddress = if (clearPeer) null else state.callPeerDirectAddress
                        )
                    }
                }
                if (ended) {
                    uwbRanger.endSession()
                }
            }

            CallHandshakeAction.ACK -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wifiAware = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wifiDirect = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                val directFromPayload = cachePeerDirectAddress(
                    peerIdHex = peerIdHex,
                    rawAddress = payload.directDeviceAddress,
                    source = "handshake-ack"
                )
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                configureUwbFromHandshake(payload)
                meshRegistry.updatePeer(
                    peerIdHex = peerIdHex,
                    isWifiAware = wifiAware,
                    isWifiDirect = wifiDirect,
                    isUwb = peerUwb,
                    isRescuer = meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
                _uiState.update {
                    it.copy(
                        callPeerWifiAware = wifiAware,
                        callPeerWifiDirect = wifiDirect,
                        callPeerUseOpus = payload.useOpus,
                        callPeerId = peerIdHex,
                        callPeerState = payload.state,
                        callPeerRttCm = payload.rttCm,
                        callPeerDirectAddress = resolvedDirect,
                        peerDirectAddresses = peerDirectAddresses.toMap()
                    )
                }
            }
        }
    }

    fun onDisconnect() {
        if (_uiState.value.isDisconnecting) return
        autoConnectBlocked = true
        ConnectionLog.add("Runtime", "auto-connect blocked (manual disconnect)")
        _uiState.update { it.copy(isDisconnecting = true, isAutoConnectBlocked = true) }
        stopAllRemoteAlerts()

        sendLeavePacket()
        stopRescueSignal()

        if (::bleManager.isInitialized) {
            bleManager.disconnect()
        }
        viewModelScope.launch {
            delay(200)
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
            textCallback = { textMsg -> addMessage(ChatMessage(text = textMsg, isMine = false, senderName = "상대방")) },
            protocolCallback = { _, _ -> },
            connectionCallback = { connected, count ->
                val directPeerIds = bleManager.getConnectedPeerIds()
                handleDirectPeerUpdate(connected, directPeerIds, count)
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
            signatureManager = signatureManager,
            routePlanner = meshGraphRegistry::shortestPath
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
                    val announcement = IdentityAnnouncementPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    val decision = peerIdentityRegistry.handleAnnounce(
                        peerId = peerHex,
                        nickname = announcement.nickname,
                        noisePublicKey = announcement.noisePublicKey,
                        now = now,
                        duplicateNicknameStaleMs = ProtocolConstants.Mesh.DUPLICATE_NICKNAME_STALE_MS
                    )
                    if (!decision.accept) return@setOnPacketReceived
                    announcedPeerLastSeen[peerHex] = now
                    decision.removedPeerIds.forEach { removedPeerId ->
                        meshGraphRegistry.removePeer(removedPeerId)
                        meshRegistry.remove(removedPeerId)
                        gossipSyncManager.removeAnnouncementForPeer(removedPeerId)
                        directPeerAnnounceTracker.removePeer(removedPeerId)
                        announcedPeerLastSeen.remove(removedPeerId)
                        peerDirectAddresses.remove(removedPeerId)
                        peerNicknames.remove(removedPeerId)
                        announcedProfiles.remove(removedPeerId)
                    }
                    val directAddress = announcement.wifiDirectAddress?.trim()?.lowercase()?.ifBlank { null }
                    if (announcement.nickname.isNotBlank()) {
                        peerNicknames[peerHex] = announcement.nickname
                    }
                    if (directAddress != null) {
                        peerDirectAddresses[peerHex] = directAddress
                    }
                    updateAnnouncedProfile(peerHex, announcement)
                    _uiState.update { state ->
                        val incomingDirect = if (directAddress != null && state.incomingCallPeerId == peerHex) {
                            directAddress
                        } else {
                            state.incomingCallDirectAddress
                        }
                        val callPeerDirect = if (directAddress != null && state.callPeerId == peerHex) {
                            directAddress
                        } else {
                            state.callPeerDirectAddress
                        }
                        state.copy(
                            peerNicknames = peerNicknames.toMap(),
                            peerDirectAddresses = peerDirectAddresses.toMap(),
                            incomingCallDirectAddress = incomingDirect,
                            callPeerDirectAddress = callPeerDirect
                        )
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
                    val flags = packet.header.flags
                    val wa = (flags and ProtocolConstants.Capabilities.WIFI_AWARE) != 0
                    val wd = (flags and ProtocolConstants.Capabilities.WIFI_DIRECT) != 0
                    val uwb = (flags and ProtocolConstants.Capabilities.UWB) != 0
                    val isRescuer = (flags and ProtocolConstants.Capabilities.RESCUER) != 0
                    meshRegistry.updatePeer(
                        peerIdHex = peerHex,
                        isWifiAware = wa,
                        isWifiDirect = wd,
                        isUwb = uwb,
                        isRescuer = isRescuer
                    )
                    refreshSurvivorCapabilities()
                    updateMeshCount()
                    gossipSyncManager.onPublicPacketSeen(packet)
                }
                PacketType.LEAVE -> {
                    meshGraphRegistry.removePeer(peerHex)
                    peerIdentityRegistry.removePeer(peerHex)
                    meshRegistry.remove(peerHex)
                    gossipSyncManager.removeAnnouncementForPeer(peerHex)
                    directPeerAnnounceTracker.removePeer(peerHex)
                    announcedPeerLastSeen.remove(peerHex)
                    peerDirectAddresses.remove(peerHex)
                    announcedProfiles.remove(peerHex)
                    if (peerNicknames.remove(peerHex) != null) {
                        _uiState.update {
                            it.copy(
                                peerNicknames = peerNicknames.toMap(),
                                peerDirectAddresses = peerDirectAddresses.toMap()
                            )
                        }
                    }
                    refreshSurvivorCapabilities()
                    updateMeshCount()
                }
                PacketType.MESSAGE -> {
                    val profileResult = ProfileTlv.decodeIfProfile(packet.payload)
                    if (profileResult != null) {
                        profilePacketHandler.handle(packet, profileResult, pathLabel)
                    } else {
                        val text = packet.payload.toString(Charsets.UTF_8)
                        addMessage(
                            ChatMessage(
                                text = text,
                                isMine = false,
                                path = pathLabel,
                                senderName = resolvePeerDisplayName(peerHex),
                                senderPeerId = peerHex
                            )
                        )
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
                                path = pathLabel,
                                senderName = resolvePeerDisplayName(peerHex),
                                senderPeerId = peerHex
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
                    val recipient = packet.header.recipientId
                    if (recipient != null && !recipient.contentEquals(senderId)) {
                        return@setOnPacketReceived
                    }
                    val payload = CallHandshakePayload.decode(packet.payload) ?: return@setOnPacketReceived
                    // UWB_SYNC는 수신자 지정 패킷만 처리해 다른 peer 응답 혼선을 막습니다.
                    if (payload.action == CallHandshakeAction.UWB_SYNC && recipient == null) {
                        return@setOnPacketReceived
                    }
                    handleCallHandshake(peerHex, payload)
                }
                PacketType.DEVICE_CONTROL -> {
                    val recipient = packet.header.recipientId
                    if (recipient != null && !recipient.contentEquals(senderId)) {
                        return@setOnPacketReceived
                    }
                    val payload = DeviceControlPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    handleDeviceControl(peerHex, payload)
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
    private fun resolveMyDisplayName(): String {
        return cachedNickname.ifBlank { _uiState.value.myNickname }.ifBlank { "나" }
    }
    private fun resolvePeerDisplayName(peerId: String?): String {
        if (peerId.isNullOrBlank()) return "익명"
        val nickname = peerNicknames[peerId].orEmpty().trim()
        return if (nickname.isNotBlank()) nickname else "익명 (${peerId.take(4)})"
    }

    private fun updateMeshCount() {
        val meshCount = meshGraphRegistry.countNodes().coerceAtLeast(1)
        _uiState.update { it.copy(meshPeerCount = meshCount) }
    }

    private fun emitMeshActivity(peerId: String) {
        _meshVisualEvents.tryEmit(MeshVisualEvent.PacketActivity(peerId))
    }

    private fun handleDirectPeerUpdate(connected: Boolean, directPeerIds: List<String>, connectedCount: Int) {
        val update = directPeerAnnounceTracker.onConnectionUpdate(connected, directPeerIds)
        if (update.shouldAnnounce || update.newPeers.isNotEmpty()) {
            sendAnnounce()
        }
        if (update.newPeers.isNotEmpty()) {
            update.newPeers.forEach { peerId ->
                sendAnnounceToPeer(peerId)
                gossipSyncManager.scheduleInitialSyncToPeer(hexToBytes(peerId), 1_000L)
            }
        }
        _uiState.update {
            val meshCount = meshGraphRegistry.countNodes().coerceAtLeast(1)
            it.copy(
                isConnected = connected,
                connectedCount = connectedCount,
                meshPeerCount = meshCount,
                directPeerIds = directPeerIds
            )
        }
    }

    private fun refreshDirectPeers() {
        val directPeerIds = bleManager.getConnectedPeerIds()
        val wasConnected = _uiState.value.directPeerIds.isNotEmpty()
        val isNowConnected = directPeerIds.isNotEmpty()
        val nickname = cachedNickname.ifBlank { bytesToHex(senderId) }
        meshGraphRegistry.updateFromAnnouncement(
            originPeerId = bytesToHex(senderId),
            originNickname = nickname,
            neighborsOrNull = directPeerIds,
            timestamp = System.currentTimeMillis()
        )
        handleDirectPeerUpdate(isNowConnected, directPeerIds, directPeerIds.size)
        if (!wasConnected && isNowConnected) {
            broadcastCachedProfileIfNeeded(force = true, reason = "direct-connected")
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
                pruneAnnouncedPeers(System.currentTimeMillis())
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
        wifiDirectRanger.refreshLocalDeviceAddress()
        val directAddress = wifiDirectRanger.getLocalDeviceAddress()
        val nickname = cachedNickname.ifBlank { bytesToHex(senderId) }
        val localBattery = _uiState.value.batteryLevel.coerceIn(0, 100)
        val noisePublicKey = signatureManager.getNoisePublicKeyBytes()
        val signingPublicKey = signatureManager.getPublicKeyBytes()
        val profileSnapshot = cachedProfile
        val announcement = IdentityAnnouncementPayload(
            nickname = nickname,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey,
            wifiDirectAddress = directAddress,
            batteryLevel = localBattery,
            powerSavingEnabled = localPowerSavingEnabled,
            gender = profileSnapshot.gender,
            birthDate = profileSnapshot.birthDate,
            notes = profileSnapshot.notes
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
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.ANNOUNCE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = getCurrentCapabilityFlags(),
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

    private fun sendAnnounceToPeer(peerIdHex: String) {
        wifiDirectRanger.refreshLocalDeviceAddress()
        val directAddress = wifiDirectRanger.getLocalDeviceAddress()
        val nickname = cachedNickname.ifBlank { bytesToHex(senderId) }
        val localBattery = _uiState.value.batteryLevel.coerceIn(0, 100)
        val noisePublicKey = signatureManager.getNoisePublicKeyBytes()
        val signingPublicKey = signatureManager.getPublicKeyBytes()
        val profileSnapshot = cachedProfile
        val announcement = IdentityAnnouncementPayload(
            nickname = nickname,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey,
            wifiDirectAddress = directAddress,
            batteryLevel = localBattery,
            powerSavingEnabled = localPowerSavingEnabled,
            gender = profileSnapshot.gender,
            birthDate = profileSnapshot.birthDate,
            notes = profileSnapshot.notes
        )
        val basePayload = announcement.encode() ?: return
        val directPeers = bleManager.getConnectedPeerIds()
        val payload = if (directPeers.isNotEmpty()) {
            basePayload + GossipTlv.encodeNeighbors(directPeers)
        } else {
            basePayload
        }
        val recipient = runCatching { hexToBytes(peerIdHex) }.getOrNull() ?: return
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.ANNOUNCE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = getCurrentCapabilityFlags(),
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = senderId,
                recipientId = recipient
            ),
            payload = payload
        )
        val signedPacket = signatureManager.sign(packet)
        protocolCore.send(signedPacket)
    }

    private fun observeProfileState() {
        viewModelScope.launch {
            profileStore.profileFlow.collect { profile ->
                cachedProfile = profile
                cachedNickname = profile.name.trim()
                _uiState.update { it.copy(myNickname = cachedNickname) }
                val ui = _uiState.value
                if (ui.isRescueSignalActive || ui.directPeerIds.isNotEmpty()) {
                    broadcastCachedProfileIfNeeded(force = false, reason = "profile-change")
                }
            }
        }
    }

    private fun handleDeviceControl(peerIdHex: String, payload: DeviceControlPayload) {
        when (payload.command) {
            DeviceControlCommand.WAKE_SCREEN -> {
                updateLocalPowerSavingState(false)
                _remotePowerSaveSetEvents.tryEmit(false)
                _remotePowerSaveExitEvents.tryEmit(System.currentTimeMillis())
                _uiEvents.tryEmit(UiEvent.Toast("구조자 요청: 절전 모드 해제"))
                ConnectionLog.add("DeviceControl", "wake requested by $peerIdHex")
            }

            DeviceControlCommand.BEEP -> {
                enqueueAlertTone(
                    AlertToneRequest(
                        command = DeviceControlCommand.BEEP,
                        durationMs = payload.durationMs,
                        intensity = payload.intensity,
                        frequencyHz = null
                    )
                )
                ConnectionLog.add(
                    "DeviceControl",
                    "beep by $peerIdHex d=${payload.durationMs} i=${payload.intensity}"
                )
            }

            DeviceControlCommand.VIBRATE -> {
                triggerLocalVibration(payload.durationMs, payload.intensity)
                ConnectionLog.add(
                    "DeviceControl",
                    "vibrate by $peerIdHex d=${payload.durationMs} i=${payload.intensity}"
                )
            }

            DeviceControlCommand.HIGH_TONE -> {
                enqueueAlertTone(
                    AlertToneRequest(
                        command = DeviceControlCommand.HIGH_TONE,
                        durationMs = payload.durationMs,
                        intensity = payload.intensity,
                        frequencyHz = payload.frequencyHz ?: DEFAULT_HIGH_TONE_HZ
                    )
                )
                ConnectionLog.add(
                    "DeviceControl",
                    "high-tone by $peerIdHex f=${payload.frequencyHz ?: DEFAULT_HIGH_TONE_HZ} d=${payload.durationMs} i=${payload.intensity}"
                )
            }

            DeviceControlCommand.STOP_ALERTS -> {
                stopAllRemoteAlerts()
                ConnectionLog.add("DeviceControl", "stop alerts by $peerIdHex")
            }
            DeviceControlCommand.POWER_SAVE_ON -> {
                updateLocalPowerSavingState(true)
                _remotePowerSaveSetEvents.tryEmit(true)
                _uiEvents.tryEmit(UiEvent.Toast("구조자 요청: 절전 모드 켜기"))
                ConnectionLog.add("DeviceControl", "power-save on by $peerIdHex")
            }
            DeviceControlCommand.POWER_SAVE_OFF -> {
                updateLocalPowerSavingState(false)
                _remotePowerSaveSetEvents.tryEmit(false)
                _remotePowerSaveExitEvents.tryEmit(System.currentTimeMillis())
                _uiEvents.tryEmit(UiEvent.Toast("구조자 요청: 절전 모드 해제"))
                ConnectionLog.add("DeviceControl", "power-save off by $peerIdHex")
            }
        }
    }

    private fun enqueueAlertTone(request: AlertToneRequest) {
        viewModelScope.launch {
            alertQueueMutex.withLock {
                val totalSize = beepQueue.size + highToneQueue.size
                if (totalSize >= MAX_ALERT_QUEUE_SIZE) {
                    // Drop the oldest in the same queue if possible; otherwise drop from the other queue.
                    val dropQueue = when (request.command) {
                        DeviceControlCommand.BEEP -> if (beepQueue.isNotEmpty()) beepQueue else highToneQueue
                        DeviceControlCommand.HIGH_TONE -> if (highToneQueue.isNotEmpty()) highToneQueue else beepQueue
                        else -> beepQueue
                    }
                    if (dropQueue.isNotEmpty()) dropQueue.removeFirst()
                }

                when (request.command) {
                    DeviceControlCommand.BEEP -> {
                        beepQueue.addLast(request)
                        lastBeepEnqueueAtMs = request.enqueuedAtMs
                    }
                    DeviceControlCommand.HIGH_TONE -> {
                        highToneQueue.addLast(request)
                        lastHighEnqueueAtMs = request.enqueuedAtMs
                    }
                    else -> Unit
                }
            }
            startAlertPlaybackLoop()
        }
    }

    private fun startAlertPlaybackLoop() {
        if (alertPlaybackJob?.isActive == true) return
        alertPlaybackJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                while (isActive) {
                    val next = alertQueueMutex.withLock {
                        val hasBeep = beepQueue.isNotEmpty()
                        val hasHigh = highToneQueue.isNotEmpty()
                        if (!hasBeep && !hasHigh) return@withLock null

                        val now = System.currentTimeMillis()
                        val repeatMode = hasBeep && hasHigh &&
                            (now - lastBeepEnqueueAtMs <= ALERT_REPEAT_WINDOW_MS) &&
                            (now - lastHighEnqueueAtMs <= ALERT_REPEAT_WINDOW_MS)

                        val nextType = if (repeatMode && hasBeep && hasHigh) {
                            if (lastAlertType == DeviceControlCommand.BEEP) {
                                DeviceControlCommand.HIGH_TONE
                            } else {
                                DeviceControlCommand.BEEP
                            }
                        } else if (hasBeep && hasHigh) {
                            val beepAt = beepQueue.first().enqueuedAtMs
                            val highAt = highToneQueue.first().enqueuedAtMs
                            if (beepAt <= highAt) DeviceControlCommand.BEEP else DeviceControlCommand.HIGH_TONE
                        } else if (hasBeep) {
                            DeviceControlCommand.BEEP
                        } else {
                            DeviceControlCommand.HIGH_TONE
                        }

                        val request = if (nextType == DeviceControlCommand.BEEP) {
                            beepQueue.removeFirst()
                        } else {
                            highToneQueue.removeFirst()
                        }
                        lastAlertType = nextType
                        request
                    } ?: break

                    when (next.command) {
                        DeviceControlCommand.BEEP -> playBeepBlocking(next)
                        DeviceControlCommand.HIGH_TONE -> playHighToneBlocking(next)
                        else -> Unit
                    }
                    delay(ALERT_GAP_MS)
                }
            } finally {
                restoreAlarmVolumeIfNeeded()
            }
        }
    }

    private fun resolveDemoLevel(value: Int): Int {
        val demoEnabled = AppSettingsRepository.state.value.isDemoModeEnabled
        return if (!demoEnabled) {
            100
        } else {
            value.coerceIn(0, 100)
        }
    }

    private fun applyAlarmVolume(level: Int) {
        if (level <= 0) return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = ((level / 100.0) * maxVolume).roundToInt().coerceIn(0, maxVolume)
        synchronized(alarmVolumeLock) {
            if (alarmVolumeBackup == null) {
                alarmVolumeBackup = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != target) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        }
    }

    private fun restoreAlarmVolumeIfNeeded() {
        synchronized(alarmVolumeLock) {
            val backup = alarmVolumeBackup ?: return
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, backup, 0)
            alarmVolumeBackup = null
        }
    }

    private suspend fun playBeepBlocking(request: AlertToneRequest) {
        val level = resolveDemoLevel(AppSettingsRepository.state.value.demoBeepLevel)
        if (level <= 0) return
        applyAlarmVolume(level)
        val safeDuration = request.durationMs.coerceIn(220, 8_000)
        val intensity = request.intensity.coerceIn(0, 3)
        val baseHz = when (intensity) {
            0 -> 700
            1 -> 900
            2 -> 1_100
            else -> 1_300
        }
        val toneScale = 0.55 + (level / 100.0) * 0.45
        val frequencyHz = (baseHz * toneScale).roundToInt().coerceIn(250, 2_000)
        val gain = (level / 100.0 * MAX_BEEP_GAIN).coerceIn(0.0, MAX_BEEP_GAIN)

        val fallbackToneType = when (intensity) {
            0 -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            1 -> ToneGenerator.TONE_CDMA_HIGH_L
            2 -> ToneGenerator.TONE_CDMA_HIGH_PBX_L
            else -> ToneGenerator.TONE_CDMA_HIGH_SS
        }

        runCatching {
            val sampleRate = 48_000
            val sampleCount = (sampleRate * (safeDuration / 1000.0)).toInt().coerceAtLeast(1)
            val pcm = ShortArray(sampleCount)

            val rawDotMs = (safeDuration / 27.0).roundToInt().coerceAtLeast(8)
            val dotMs = minOf(rawDotMs, 180)
            val dashMs = dotMs * 3
            val intraGapMs = dotMs
            val letterGapMs = dotMs * 3

            val pattern = ArrayList<Pair<Boolean, Int>>(20)
            fun addOn(ms: Int) {
                if (ms > 0) pattern.add(true to ms)
            }
            fun addOff(ms: Int) {
                if (ms > 0) pattern.add(false to ms)
            }
            fun addLetter(symbols: List<Boolean>) {
                symbols.forEachIndexed { index, isDash ->
                    addOn(if (isDash) dashMs else dotMs)
                    if (index < symbols.lastIndex) addOff(intraGapMs)
                }
            }

            addLetter(listOf(false, false, false)) // S: ...
            addOff(letterGapMs)
            addLetter(listOf(true, true, true)) // O: ---
            addOff(letterGapMs)
            addLetter(listOf(false, false, false)) // S: ...

            val patternDuration = pattern.sumOf { it.second }
            val trailingSilence = (safeDuration - patternDuration).coerceAtLeast(0)
            if (trailingSilence > 0) addOff(trailingSilence)

            var cursorSample = 0
            fun writeToneSegment(durationMs: Int) {
                if (durationMs <= 0 || cursorSample >= sampleCount) return
                val durationSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
                val safeEnd = (cursorSample + durationSamples).coerceAtMost(sampleCount)
                val segmentSamples = safeEnd - cursorSample
                if (segmentSamples <= 0) return
                val segmentMs = segmentSamples * 1000.0 / sampleRate
                val rampMs = segmentMs.coerceIn(4.0, 10.0)
                val rampSamples = (sampleRate * (rampMs / 1000.0)).toInt().coerceAtLeast(1)
                for (i in 0 until segmentSamples) {
                    val attack = (i + 1).toDouble() / rampSamples
                    val release = (segmentSamples - i).toDouble() / rampSamples
                    val env = minOf(1.0, minOf(attack, release))
                    val angle = 2.0 * PI * frequencyHz * i / sampleRate
                    val sample = sin(angle) * Short.MAX_VALUE * gain * env
                    pcm[cursorSample + i] = sample.toInt().toShort()
                }
                cursorSample = safeEnd
            }

            fun skipSilence(durationMs: Int) {
                if (durationMs <= 0 || cursorSample >= sampleCount) return
                val durationSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
                cursorSample = (cursorSample + durationSamples).coerceAtMost(sampleCount)
            }

            pattern.forEach { (isOn, ms) ->
                if (isOn) {
                    writeToneSegment(ms)
                } else {
                    skipSilence(ms)
                }
            }

            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                pcm.size * 2,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            try {
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(safeDuration.toLong() + 40L)
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.onFailure { throwable ->
            Log.w("DeviceControl", "beep playback fallback: ${throwable.message}")
            runCatching { toneGenerator.stopTone() }
            toneGenerator.startTone(fallbackToneType, safeDuration)
            try {
                delay(safeDuration.toLong())
            } catch (_: CancellationException) {
                // no-op
            } finally {
                runCatching { toneGenerator.stopTone() }
            }
        }
    }

    private suspend fun playHighToneBlocking(request: AlertToneRequest) {
        val level = resolveDemoLevel(AppSettingsRepository.state.value.demoHighToneLevel)
        if (level <= 0) return
        applyAlarmVolume(level)
        highToneJob?.cancel()
        val job = viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val sampleRate = 48_000
                val safeFrequency = request.frequencyHz?.coerceIn(500, 20_000) ?: DEFAULT_HIGH_TONE_HZ
                val safeDurationMs = request.durationMs.coerceIn(220, 8_000)
                val sampleCount = (sampleRate * (safeDurationMs / 1000.0)).toInt().coerceAtLeast(1)
                val gain = (level / 100.0 * MAX_HIGH_TONE_GAIN).coerceIn(0.0, MAX_HIGH_TONE_GAIN)
                val pcm = ShortArray(sampleCount)
                for (idx in 0 until sampleCount) {
                    val angle = 2.0 * PI * safeFrequency * idx / sampleRate
                    pcm[idx] = (sin(angle) * Short.MAX_VALUE * gain).toInt().toShort()
                }
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    pcm.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                try {
                    track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                    track.play()
                    delay(safeDurationMs.toLong() + 40L)
                } finally {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            }.onFailure { throwable ->
                Log.w("DeviceControl", "high-tone playback failed: ${throwable.message}")
            }
        }
        highToneJob = job
        job.join()
    }

    private fun triggerLocalVibration(durationMs: Int, _intensity: Int) {
        val vibrator = getVibrator() ?: return
        val level = resolveDemoLevel(AppSettingsRepository.state.value.demoVibrateLevel)
        if (level <= 0) return
        val safeDuration = durationMs.coerceIn(200, 8_000).toLong()
        val amplitude = ((level / 100.0) * 255.0).roundToInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(safeDuration, amplitude)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(safeDuration)
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun stopAllRemoteAlerts() {
        runCatching { toneGenerator.stopTone() }
        highToneJob?.cancel()
        alertPlaybackJob?.cancel()
        viewModelScope.launch {
            alertQueueMutex.withLock {
                beepQueue.clear()
                highToneQueue.clear()
            }
        }
        restoreAlarmVolumeIfNeeded()
        runCatching { getVibrator()?.cancel() }
    }

    private fun configureUwbFromHandshake(payload: CallHandshakePayload) {
        if (!isUwbSupportedLocally()) return
        val controllerAddress = payload.uwbControllerAddress ?: payload.uwbDeviceAddress ?: return
        val channel = payload.uwbControllerChannel ?: return
        val preambleIndex = payload.uwbControllerPreambleIndex ?: return
        val sessionId = payload.uwbSessionId ?: return
        val cachedAddress = uwbRanger.getControleeAddressOrNull()
        if (cachedAddress != null) {
            uwbRanger.configureControleeSession(
                controllerAddress = controllerAddress,
                channel = channel,
                preambleIndex = preambleIndex,
                sessionId = sessionId
            )
            uwbRanger.start()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val localAddress = uwbRanger.prepareControleeAddressBlocking() ?: return@launch
            if (localAddress.isBlank()) return@launch
            uwbRanger.configureControleeSession(
                controllerAddress = controllerAddress,
                channel = channel,
                preambleIndex = preambleIndex,
                sessionId = sessionId
            )
            uwbRanger.start()
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
            val peerIds = announcedPeerLastSeen.keys.sorted()
            val list = peerIds.map { peerId ->
                val base = discoveredSurvivors[peerId]
                    ?: SurvivorProfile(name = peerNicknames[peerId].orEmpty(), peerId = peerId)
                val merged = mergeProfile(base, announcedProfiles[peerId], peerNicknames[peerId].orEmpty())
                val info = meshRegistry.getPeer(peerId)
                merged.copy(
                    isWifiAware = info?.isWifiAware ?: merged.isWifiAware,
                    isWifiDirect = info?.isWifiDirect ?: merged.isWifiDirect,
                    isUwb = info?.isUwb ?: merged.isUwb,
                    peerId = peerId
                )
            }
            state.copy(survivors = list)
        }
    }

    private fun updateAnnouncedProfile(peerId: String, announcement: IdentityAnnouncementPayload) {
        val profile = SurvivorProfile(
            name = announcement.nickname,
            gender = announcement.gender.orEmpty(),
            birthDate = announcement.birthDate.orEmpty(),
            notes = announcement.notes.orEmpty(),
            peerId = peerId
        )
        announcedProfiles[peerId] = profile
    }

    private fun mergeProfile(
        base: SurvivorProfile,
        announced: SurvivorProfile?,
        fallbackName: String
    ): SurvivorProfile {
        val mergedName = pickNonBlank(base.name, announced?.name).ifBlank { fallbackName }
        return base.copy(
            name = mergedName,
            gender = pickNonBlank(base.gender, announced?.gender),
            birthDate = pickNonBlank(base.birthDate, announced?.birthDate),
            notes = pickNonBlank(base.notes, announced?.notes)
        )
    }

    private fun pickNonBlank(primary: String, fallback: String?): String {
        return if (primary.isNotBlank()) primary else fallback?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun pruneAnnouncedPeers(now: Long) {
        val cutoff = now - ProtocolConstants.Mesh.PEER_TIMEOUT_MS
        val stalePeers = announcedPeerLastSeen.filterValues { it < cutoff }.keys
        if (stalePeers.isEmpty()) return
        stalePeers.forEach { peerId ->
            announcedPeerLastSeen.remove(peerId)
            peerDirectAddresses.remove(peerId)
            meshRegistry.remove(peerId)
            announcedProfiles.remove(peerId)
        }
        _uiState.update {
            it.copy(peerDirectAddresses = peerDirectAddresses.toMap())
        }
        refreshSurvivorCapabilities()
    }

    private fun observeCallConnection() {
        viewModelScope.launch {
            combine(
                wifiAwareRanger.isConnectionReady,
                wifiDirectRanger.isConnectionReady
            ) { awareReady, directReady ->
                awareReady || (wifiDirectEnabled && directReady)
            }.collect { connected ->
                _uiState.update { it.copy(isCallConnected = connected) }
            }
        }
    }

    fun isWifiAwareSupportedLocally(): Boolean = if (wifiAwareEnabled) {
        (getCurrentCapabilityFlags() and ProtocolConstants.Capabilities.WIFI_AWARE) != 0
    } else {
        false
    }

    fun isWifiDirectSupportedLocally(): Boolean = if (wifiDirectEnabled) {
        (getCurrentCapabilityFlags() and ProtocolConstants.Capabilities.WIFI_DIRECT) != 0
    } else {
        false
    }

    fun isUwbSupportedLocally(): Boolean =
        (getCurrentCapabilityFlags() and ProtocolConstants.Capabilities.UWB) != 0

    private fun getCurrentCapabilityFlags(): Int {
        var flags = 0
        val packageManager = app.packageManager
        val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiAwareSupported =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
            } else {
                false
            }
        if (wifiAwareEnabled && wifiAwareSupported && wifiManager?.isWifiEnabled == true) {
            flags = flags or ProtocolConstants.Capabilities.WIFI_AWARE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_UWB) &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.UWB_RANGING) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            flags = flags or ProtocolConstants.Capabilities.UWB
        }
        if (wifiDirectEnabled && packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            flags = flags or ProtocolConstants.Capabilities.WIFI_DIRECT
        }
        return flags
    }

    private fun cachePeerDirectAddress(peerIdHex: String, rawAddress: String?, source: String): String? {
        val normalized = normalizeMacAddress(rawAddress) ?: return null
        val previous = peerDirectAddresses.put(peerIdHex, normalized)
        if (previous != normalized) {
            ConnectionLog.add("CallHandshake", "peer direct updated $source peer=$peerIdHex addr=$normalized")
        }
        return normalized
    }

    private fun normalizeMacAddress(rawAddress: String?): String? {
        val normalized = rawAddress?.trim()?.lowercase()?.ifBlank { null } ?: return null
        val parts = normalized.split(':')
        if (parts.size != 6) return null
        val isHexAddress = parts.all { part ->
            part.length == 2 && part.all { ch -> ch in '0'..'9' || ch in 'a'..'f' }
        }
        return if (isHexAddress) normalized else null
    }

    private fun broadcastCachedProfileIfNeeded(force: Boolean, reason: String) {
        val profile = cachedProfile
        val normalized = profile.copy(
            name = profile.name.trim(),
            gender = profile.gender.trim(),
            birthDate = profile.birthDate.trim(),
            notes = profile.notes.trim()
        )
        val hasShareableField = normalized.name.isNotBlank() ||
            normalized.gender.isNotBlank() ||
            normalized.birthDate.isNotBlank() ||
            normalized.notes.isNotBlank()
        if (!hasShareableField) return
        val fingerprint = listOf(
            normalized.name,
            normalized.gender,
            normalized.birthDate,
            normalized.notes
        ).joinToString("|")
        val now = System.currentTimeMillis()
        if (!force && fingerprint == lastProfileBroadcastFingerprint) return
        if (now - lastProfileBroadcastAtMs < profileBroadcastCooldownMs) return
        sendProfileUpdate(normalized)
        lastProfileBroadcastFingerprint = fingerprint
        lastProfileBroadcastAtMs = now
        ConnectionLog.add("ProfileSync", "broadcast profile reason=$reason")
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

    fun updateLocalPowerSavingState(enabled: Boolean) {
        if (localPowerSavingEnabled == enabled) return
        localPowerSavingEnabled = enabled
        if (::protocolCore.isInitialized) {
            sendAnnounce()
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
        const val DEFAULT_HIGH_TONE_HZ = 17_500
        const val MAX_HIGH_TONE_GAIN = 1.0
        const val MAX_BEEP_GAIN = 0.85
        const val ALERT_GAP_MS = 150L
        const val MAX_ALERT_QUEUE_SIZE = 10
        const val ALERT_REPEAT_WINDOW_MS = 4_000L
    }

    override fun onCleared() {
        AppShutdownHooks.clear()
        audioEngine?.stopRecording()
        voiceRecorder?.stop()
        stopAllRemoteAlerts()
        toneGenerator.release()
        if (wifiAwareEnabled) {
            wifiAwareRanger.stop()
        }
        wifiDirectRanger.stop()
        uwbRanger.release()
        announceJob?.cancel()
        meshCleanupJob?.cancel()
        bleDebugJob?.cancel()
        highToneJob?.cancel()
        if (::gossipSyncManager.isInitialized) {
            gossipSyncManager.stop()
        }

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
