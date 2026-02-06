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
import android.os.SystemClock
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
import com.example.lifesaiver.core.uwb.UwbRanger
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
import com.example.lifesaiver.protocol.model.DeviceControlCommand
import com.example.lifesaiver.protocol.model.DeviceControlPayload
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

data class AppUiState(
    val hasPermissions: Boolean = false,
    val batteryLevel: Int = 100,
    val isConnected: Boolean = false,
    val connectedCount: Int = 0,
    val meshPeerCount: Int = 0,
    val directPeerIds: List<String> = emptyList(),
    val peerRssi: Map<String, Int> = emptyMap(),
    val myPeerId: String = "",
    val myNickname: String = "",
    val peerNicknames: Map<String, String> = emptyMap(),
    val peerDirectAddresses: Map<String, String> = emptyMap(),
    val peerBatteryLevels: Map<String, Int> = emptyMap(),
    val peerPowerSavingModes: Map<String, Boolean> = emptyMap(),
    val meshGraphSnapshot: MeshGraphRegistry.GraphSnapshot = MeshGraphRegistry.GraphSnapshot(emptyList(), emptyList()),
    val isMicOn: Boolean = false,
    val isDisconnecting: Boolean = false,
    val isRescueSignalActive: Boolean = false,
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
    val incomingCallDirectAddress: String? = null,
    val isCallConnected: Boolean = false,
    val callPeerWifiAware: Boolean? = null,
    val callPeerWifiDirect: Boolean? = null,
    val callPeerUseOpus: Boolean? = null,
    val callPeerId: String? = null,
    val callPeerState: com.example.lifesaiver.protocol.model.CallHandshakeState? = null,
    val callPeerRttCm: Int? = null,
    val callPeerDirectAddress: String? = null
)

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val forcePcmCall = false
    private val wifiAwareEnabled = true
    private val wifiDirectEnabled = false
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    private val _meshVisualEvents = MutableSharedFlow<MeshVisualEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val meshVisualEvents: SharedFlow<MeshVisualEvent> = _meshVisualEvents.asSharedFlow()

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
    val uwbRanger = UwbRanger(app)
    private lateinit var bleManager: BleManager
    private lateinit var protocolCore: ProtocolCore
    private lateinit var signatureManager: SignatureManager
    private lateinit var gossipSyncManager: GossipSyncManager
    private lateinit var profilePacketHandler: ProfilePacketHandler
    private val signatureLogBuffer = ArrayDeque<SignatureLogEntry>()
    private val profileLogBuffer = ArrayDeque<ProfileSyncLogEntry>()
    private val profileDao by lazy { AppDatabase.getInstance(app).profileDao() }
    private val meshRegistry = MeshPeerRegistry()
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private var senderId: ByteArray = ByteArray(0)
    private val profileStore = ProfileStore(app)
    @Volatile private var cachedProfile: SurvivorProfile = SurvivorProfile()
    @Volatile private var cachedNickname: String = ""
    private val announcedToPeers = mutableSetOf<String>()
    private val peerNicknames = mutableMapOf<String, String>()
    private val peerDirectAddresses = ConcurrentHashMap<String, String>()
    private val peerBatteryLevels = ConcurrentHashMap<String, Int>()
    private val peerPowerSavingModes = ConcurrentHashMap<String, Boolean>()
    private val discoveredSurvivors = mutableMapOf<String, SurvivorProfile>()
    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null
    private val meshGraphRegistry = MeshGraphRegistry()
    private val peerIdentityRegistry = PeerIdentityRegistry()
    private val announcedPeerLastSeen = ConcurrentHashMap<String, Long>()
    private var announceJob: kotlinx.coroutines.Job? = null
    private var meshCleanupJob: kotlinx.coroutines.Job? = null
    private var bleDebugJob: kotlinx.coroutines.Job? = null
    @Volatile private var bleRssiActiveMode: Boolean = false
    private var lastConnectionAnnounceMs: Long = 0L
    private val connectionAnnounceCooldownMs: Long = 3_000L
    private var lastProfileBroadcastFingerprint: String = ""
    private var lastProfileBroadcastAtMs: Long = 0L
    private val profileBroadcastCooldownMs: Long = 2_000L
    private val uwbSyncRequestLock = Any()
    @Volatile private var uwbSyncRequestInFlight = false
    private var lastUwbSyncTargetPeerId: String? = null
    private var lastUwbSyncRequestAtMs: Long = 0L
    private val uwbSyncMinIntervalMs: Long = 2_000L
    @Volatile private var uwbTargetPeerId: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { intent?.let { updateBatteryLevel(it) } }
    }

    init {
        initProtocol()
        initBle()
        initBatteryMonitor()
        refreshPermissions()
        wifiDirectRanger.refreshLocalDeviceAddress()
        observeProfileState()
        observeProfiles()
        observeMeshGraph()
        observeCallConnection()
        _uiState.update { it.copy(myPeerId = bytesToHex(senderId)) }
        AppShutdownHooks.register(onSendLeave = { sendLeaveOnShutdown() }, onStopServices = { stopServicesForShutdown() })
    }

    fun startRescueSignal() {
        if (!_uiState.value.hasPermissions) { _uiEvents.tryEmit(UiEvent.Toast("권한이 필요합니다.")); return }
        bleManager.startEmergencyAdvertising()
        try {
            val intent = Intent(app, RescueService::class.java).apply { action = RescueService.ACTION_START_RESCUE }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
        } catch (e: Exception) { Log.e("AppViewModel", "서비스 시작 실패: ${e.message}") }
        _uiState.update { it.copy(isRescueSignalActive = true) }
        broadcastCachedProfileIfNeeded(force = true, reason = "rescue-start")
    }

    fun pulseRescueSignal() { if (::bleManager.isInitialized) bleManager.pulseEmergencyAdvertising() }
    fun stopRescueSignal() { bleManager.stopAdvertising(); try { app.startService(Intent(app, RescueService::class.java).apply { action = RescueService.ACTION_STOP_RESCUE }) } catch (e: Exception) {} ; _uiState.update { it.copy(isRescueSignalActive = false) } }
    fun refreshPermissions() {
        val granted = requiredPermissions.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            wifiDirectRanger.refreshLocalDeviceAddress()
            if (audioEngine == null) initAudio()
            if (isUwbSupportedLocally()) {
                viewModelScope.launch(Dispatchers.IO) {
                    uwbRanger.prepareControllerOfferBlocking()
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
                    uwbRanger.prepareControllerOfferBlocking()
                }
            }
        }
    }
    fun onStartAutoConnect() { bleManager.startAutoConnect() }
    fun onStopAutoConnect() { bleManager.disconnect() }
    fun setBleRssiActiveMode(active: Boolean) {
        bleRssiActiveMode = active
        if (::bleManager.isInitialized) {
            bleManager.setRssiActiveMode(active)
        }
    }
    fun onMicPress() { if (_uiState.value.isMicOn) return; val outDir = File(app.filesDir, "voicenotes/outgoing"); if (!outDir.exists()) outDir.mkdirs(); val recorder = VoiceRecorder(outDir); val file = recorder.start() ?: return; voiceRecorder = recorder; recordingFile = file; _uiState.update { it.copy(isMicOn = true) } }
    fun onMicRelease() { if (!_uiState.value.isMicOn) return; val recorder = voiceRecorder; val pendingFile = recordingFile; voiceRecorder = null; recordingFile = null; _uiState.update { it.copy(isMicOn = false) }; viewModelScope.launch(Dispatchers.IO) { delay(500); val file = recorder?.stop() ?: pendingFile; if (file == null || !file.exists()) return@launch; val bytes = file.readBytes(); val payload = FileTransferPayload(file.name, bytes.size.toLong(), "audio/mp4", bytes).encode(); protocolCore.broadcast(Packet(PacketHeader(2, PacketType.FILE_TRANSFER, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, System.currentTimeMillis(), senderId), payload)); addMessage(ChatMessage(text = "[voice] ${file.absolutePath}", isMine = true, senderName = resolveMyDisplayName(), senderPeerId = bytesToHex(senderId))) } }
    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        val packet = buildMessagePacket(text)
        val signed = signatureManager.sign(packet)
        gossipSyncManager.onPublicPacketSeen(signed)
        protocolCore.broadcast(signed)
        addMessage(
            ChatMessage(
                text = text,
                isMine = true,
                senderName = resolveMyDisplayName(),
                senderPeerId = bytesToHex(senderId)
            )
        )
    }

    fun onSendDirectMessage(targetPeerIdHex: String, text: String) {
        val trimmed = text.trim()
        if (targetPeerIdHex.isBlank() || trimmed.isBlank()) return
        val recipientId = runCatching { hexToBytes(targetPeerIdHex) }.getOrNull() ?: return
        val payload = trimmed.toByteArray()
        val packet = Packet(
            PacketHeader(
                version = 2,
                type = PacketType.MESSAGE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = senderId,
                recipientId = recipientId
            ),
            payload
        )
        val signed = signatureManager.sign(packet)
        protocolCore.send(signed)
        addMessage(
            ChatMessage(
                text = trimmed,
                isMine = true,
                senderName = resolveMyDisplayName(),
                senderPeerId = bytesToHex(senderId),
                recipientPeerId = targetPeerIdHex
            )
        )
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

    fun sendProfileUpdate(profile: SurvivorProfile) {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(profile.name, mapGender(profile.gender), profile.birthDate, profile.notes, now)
        val packet = Packet(
            PacketHeader(
                2,
                PacketType.MESSAGE,
                ProtocolConstants.MESSAGE_TTL_HOPS,
                getCurrentCapabilityFlags(),
                payload.size,
                now,
                senderId
            ),
            payload
        )
        gossipSyncManager.onPublicPacketSeen(packet); protocolCore.broadcast(packet)
    }

    fun sendCallHandshake(targetPeerIdHex: String, action: CallHandshakeAction, callerName: String, wifiAwareSupported: Boolean, wifiDirectSupported: Boolean, useOpus: Boolean, state: CallHandshakeState? = null, rttCm: Int? = null) {
        if (targetPeerIdHex.isBlank()) return
        val recipientId = runCatching { hexToBytes(targetPeerIdHex) }.getOrNull() ?: return
        wifiDirectRanger.refreshLocalDeviceAddress()
        val awareSupported = if (wifiAwareEnabled) wifiAwareSupported else false
        val rtt = if (wifiAwareEnabled) {
            rttCm ?: wifiAwareRanger.isConnectionReady.value.let { if (!it) null else wifiAwareRanger.rttDistance.value }
                ?.let { (it * 100f).roundToInt().coerceIn(0, 0xFFFF) }
        } else {
            null
        }
        val directAddr = wifiDirectRanger.getLocalDeviceAddress()
        var uwbDeviceAddress: String? = null
        var uwbControllerAddress: String? = null
        var uwbControllerChannel: Int? = null
        var uwbControllerPreambleIndex: Int? = null
        var uwbSessionId: Int? = null
        if (isUwbSupportedLocally()) {
            when (action) {
                CallHandshakeAction.START -> {
                    val offer = uwbRanger.getControllerOfferOrNull()
                    if (offer != null) {
                        uwbDeviceAddress = offer.controllerAddress
                        uwbControllerAddress = offer.controllerAddress
                        uwbControllerChannel = offer.channel
                        uwbControllerPreambleIndex = offer.preambleIndex
                        uwbSessionId = offer.sessionId
                        uwbRanger.configureControllerSession(peerAddress = null)
                    } else {
                        viewModelScope.launch(Dispatchers.IO) {
                            uwbRanger.prepareControllerOfferBlocking()
                        }
                    }
                }

                CallHandshakeAction.ACK -> {
                    val offer = uwbRanger.getControllerOfferOrNull()
                    if (offer != null) {
                        uwbDeviceAddress = offer.controllerAddress
                        uwbControllerAddress = offer.controllerAddress
                        uwbControllerChannel = offer.channel
                        uwbControllerPreambleIndex = offer.preambleIndex
                        uwbSessionId = offer.sessionId
                        uwbRanger.configureControllerSession(peerAddress = null)
                    } else {
                        viewModelScope.launch(Dispatchers.IO) {
                            uwbRanger.prepareControllerOfferBlocking()
                        }
                    }
                }

                CallHandshakeAction.END -> {
                    uwbRanger.endSession()
                }

                CallHandshakeAction.UWB_SYNC -> {
                    val offer = uwbRanger.getControllerOfferOrNull()
                    if (offer != null) {
                        uwbDeviceAddress = offer.controllerAddress
                        uwbControllerAddress = offer.controllerAddress
                        uwbControllerChannel = offer.channel
                        uwbControllerPreambleIndex = offer.preambleIndex
                        uwbSessionId = offer.sessionId
                        uwbRanger.configureControllerSession(peerAddress = null)
                    } else {
                        viewModelScope.launch(Dispatchers.IO) {
                            uwbRanger.prepareControllerOfferBlocking()
                        }
                    }
                }
            }
        } else if (action == CallHandshakeAction.END) {
            uwbRanger.endSession()
        }
        val payload = CallHandshakePayload(
            action,
            callerName,
            awareSupported,
            wifiDirectSupported,
            if (forcePcmCall) false else useOpus,
            state,
            rtt,
            directAddr,
            uwbDeviceAddress = uwbDeviceAddress,
            uwbControllerAddress = uwbControllerAddress,
            uwbControllerChannel = uwbControllerChannel,
            uwbControllerPreambleIndex = uwbControllerPreambleIndex,
            uwbSessionId = uwbSessionId
        ).encode()
        protocolCore.broadcast(Packet(PacketHeader(2, PacketType.CALL_HANDSHAKE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, System.currentTimeMillis(), senderId, recipientId), payload))
    }

    fun requestUwbSession(targetPeerIdHex: String) {
        if (targetPeerIdHex.isBlank()) return
        if (!isUwbSupportedLocally()) return
        if (uwbRanger.isRuntimeAvailableCached() == false) return
        val normalizedTarget = targetPeerIdHex.trim().lowercase()
        val now = SystemClock.elapsedRealtime()
        synchronized(uwbSyncRequestLock) {
            val sameTarget = lastUwbSyncTargetPeerId == normalizedTarget
            if (uwbSyncRequestInFlight) return
            if (sameTarget && now - lastUwbSyncRequestAtMs < uwbSyncMinIntervalMs) return
            uwbSyncRequestInFlight = true
            lastUwbSyncTargetPeerId = normalizedTarget
            lastUwbSyncRequestAtMs = now
        }
        uwbTargetPeerId = normalizedTarget
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recipientId = runCatching { hexToBytes(normalizedTarget) }.getOrNull() ?: return@launch
                val offer = uwbRanger.getControllerOfferOrNull() ?: uwbRanger.prepareControllerOfferBlocking() ?: return@launch
                uwbRanger.configureControllerSession(peerAddress = null)
                wifiDirectRanger.refreshLocalDeviceAddress()
                val payload = CallHandshakePayload(
                    action = CallHandshakeAction.UWB_SYNC,
                    callerName = cachedNickname.ifBlank { "구조자" },
                    wifiAwareSupported = false,
                    wifiDirectSupported = false,
                    useOpus = false,
                    directDeviceAddress = wifiDirectRanger.getLocalDeviceAddress(),
                    uwbDeviceAddress = offer.controllerAddress,
                    uwbControllerAddress = offer.controllerAddress,
                    uwbControllerChannel = offer.channel,
                    uwbControllerPreambleIndex = offer.preambleIndex,
                    uwbSessionId = offer.sessionId
                ).encode()
                protocolCore.broadcast(
                    Packet(
                        PacketHeader(
                            2,
                            PacketType.CALL_HANDSHAKE,
                            ProtocolConstants.MESSAGE_TTL_HOPS,
                            0,
                            payload.size,
                            System.currentTimeMillis(),
                            senderId,
                            recipientId
                        ),
                        payload
                    )
                )
            } finally {
                synchronized(uwbSyncRequestLock) {
                    uwbSyncRequestInFlight = false
                }
            }
        }
    }

    fun stopUwbSession() {
        synchronized(uwbSyncRequestLock) {
            uwbSyncRequestInFlight = false
            lastUwbSyncTargetPeerId = null
            lastUwbSyncRequestAtMs = 0L
        }
        uwbTargetPeerId = null
        uwbRanger.endSession()
    }

    fun sendDeviceControl(
        targetPeerIdHex: String,
        command: DeviceControlCommand,
        durationMs: Int = 1_500,
        intensity: Int = 2,
        frequencyHz: Int? = null
    ) {
        if (targetPeerIdHex.isBlank()) return
        val recipientId = runCatching { hexToBytes(targetPeerIdHex) }.getOrNull() ?: return
        val payload = DeviceControlPayload(
            command = command,
            durationMs = durationMs,
            intensity = intensity,
            frequencyHz = frequencyHz
        ).encode()
        protocolCore.broadcast(
            Packet(
                header = PacketHeader(
                    version = 2,
                    type = PacketType.DEVICE_CONTROL,
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
        ConnectionLog.add(
            "DeviceControl",
            "send ${command.name} -> $targetPeerIdHex d=${durationMs}ms i=$intensity f=${frequencyHz ?: 0}"
        )
    }

    fun clearIncomingCall(peerIdHex: String) {
        var clearedIncoming = false
        var hasActivePeer = false
        _uiState.update {
            if (it.incomingCallPeerId != peerIdHex) {
                it
            } else {
                clearedIncoming = true
                hasActivePeer = it.callPeerId == peerIdHex
                it.copy(
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
        _uiState.update { s ->
            val clearIncoming = s.incomingCallPeerId == peerIdHex
            val clearPeer = s.callPeerId == peerIdHex
            clearPeerState = clearPeer
            if (!clearIncoming && !clearPeer) {
                s
            } else {
                s.copy(
                    incomingCallPeerId = if (clearIncoming) null else s.incomingCallPeerId,
                    incomingCallName = if (clearIncoming) null else s.incomingCallName,
                    incomingCallWifiAware = if (clearIncoming) false else s.incomingCallWifiAware,
                    incomingCallWifiDirect = if (clearIncoming) false else s.incomingCallWifiDirect,
                    incomingCallUseOpus = if (clearIncoming) false else s.incomingCallUseOpus,
                    incomingCallState = if (clearIncoming) null else s.incomingCallState,
                    incomingCallRttCm = if (clearIncoming) null else s.incomingCallRttCm,
                    incomingCallDirectAddress = if (clearIncoming) null else s.incomingCallDirectAddress,
                    callPeerWifiAware = if (clearPeer) null else s.callPeerWifiAware,
                    callPeerWifiDirect = if (clearPeer) null else s.callPeerWifiDirect,
                    callPeerUseOpus = if (clearPeer) null else s.callPeerUseOpus,
                    callPeerId = if (clearPeer) null else s.callPeerId,
                    callPeerState = if (clearPeer) null else s.callPeerState,
                    callPeerRttCm = if (clearPeer) null else s.callPeerRttCm,
                    callPeerDirectAddress = if (clearPeer) null else s.callPeerDirectAddress
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
                val wa = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wd = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                val directFromPayload = cachePeerDirectAddress(peerIdHex, payload.directDeviceAddress, "handshake-start")
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                payload.uwbDeviceAddress?.let { remoteAddress ->
                    uwbRanger.configureControllerSession(peerAddress = remoteAddress)
                }
                meshRegistry.updatePeer(
                    peerIdHex,
                    wa,
                    wd,
                    peerUwb,
                    meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
                _uiState.update {
                    val inActiveCallWithPeer = it.isCallConnected && it.callPeerId == peerIdHex
                    if (inActiveCallWithPeer) {
                        it.copy(
                            callPeerWifiAware = wa,
                            callPeerWifiDirect = wd,
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
                            incomingCallWifiAware = wa,
                            incomingCallWifiDirect = wd,
                            incomingCallUseOpus = payload.useOpus,
                            incomingCallState = payload.state,
                            incomingCallRttCm = payload.rttCm,
                            incomingCallDirectAddress = resolvedDirect,
                            callPeerWifiAware = wa,
                            callPeerWifiDirect = wd,
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
            CallHandshakeAction.END -> {
                var ended = false
                _uiState.update { s ->
                    val ci = s.incomingCallPeerId == peerIdHex; val cp = s.callPeerId == peerIdHex
                    if (!ci && !cp) {
                        s
                    } else {
                        ended = true
                        s.copy(
                            incomingCallPeerId = if (ci) null else s.incomingCallPeerId,
                            incomingCallName = if (ci) null else s.incomingCallName,
                            incomingCallWifiAware = if (ci) false else s.incomingCallWifiAware,
                            incomingCallWifiDirect = if (ci) false else s.incomingCallWifiDirect,
                            incomingCallUseOpus = if (ci) false else s.incomingCallUseOpus,
                            incomingCallState = if (ci) null else s.incomingCallState,
                            incomingCallRttCm = if (ci) null else s.incomingCallRttCm,
                            incomingCallDirectAddress = if (ci) null else s.incomingCallDirectAddress,
                            callPeerWifiAware = if (cp) null else s.callPeerWifiAware,
                            callPeerWifiDirect = if (cp) null else s.callPeerWifiDirect,
                            callPeerUseOpus = if (cp) null else s.callPeerUseOpus,
                            callPeerId = if (cp) null else s.callPeerId,
                            callPeerState = if (cp) null else s.callPeerState,
                            callPeerRttCm = if (cp) null else s.callPeerRttCm,
                            callPeerDirectAddress = if (cp) null else s.callPeerDirectAddress
                        )
                    }
                }
                if (ended) {
                    uwbRanger.endSession()
                }
            }
            CallHandshakeAction.ACK -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wa = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wd = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                val directFromPayload = cachePeerDirectAddress(peerIdHex, payload.directDeviceAddress, "handshake-ack")
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                payload.uwbDeviceAddress?.let { remoteAddress ->
                    uwbRanger.configureControllerSession(peerAddress = remoteAddress)
                }
                meshRegistry.updatePeer(
                    peerIdHex,
                    wa,
                    wd,
                    peerUwb,
                    meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
                _uiState.update {
                    it.copy(
                        callPeerWifiAware = wa,
                        callPeerWifiDirect = wd,
                        callPeerUseOpus = payload.useOpus,
                        callPeerId = peerIdHex,
                        callPeerState = payload.state,
                        callPeerRttCm = payload.rttCm,
                        callPeerDirectAddress = resolvedDirect,
                        peerDirectAddresses = peerDirectAddresses.toMap()
                    )
                }
            }
            CallHandshakeAction.UWB_SYNC -> {
                val peerInfo = meshRegistry.getPeer(peerIdHex)
                val wa = if (wifiAwareEnabled) {
                    payload.wifiAwareSupported || (peerInfo?.isWifiAware ?: false)
                } else {
                    false
                }
                val wd = payload.wifiDirectSupported || (peerInfo?.isWifiDirect ?: false)
                val peerUwb =
                    (peerInfo?.isUwb ?: false) ||
                        payload.uwbDeviceAddress != null ||
                        payload.uwbControllerAddress != null
                payload.uwbDeviceAddress?.let { remoteAddress ->
                    uwbRanger.configureControllerSession(peerAddress = remoteAddress)
                }
                meshRegistry.updatePeer(
                    peerIdHex,
                    wa,
                    wd,
                    peerUwb,
                    meshRegistry.getPeer(peerIdHex)?.isRescuer ?: false
                )
                refreshSurvivorCapabilities()
            }
        }
    }

    fun sendProfileTestPacket() {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(cachedNickname.ifBlank { "rescuer-user" }, 'U', "1990-01-01", "rescuer-test", now)
        val packet = Packet(
            PacketHeader(
                2,
                PacketType.MESSAGE,
                ProtocolConstants.MESSAGE_TTL_HOPS,
                getCurrentCapabilityFlags(),
                payload.size,
                now,
                senderId
            ),
            payload
        )
        gossipSyncManager.onPublicPacketSeen(packet); protocolCore.broadcast(packet)
    }

    fun onDisconnect() { if (_uiState.value.isDisconnecting) return; _uiState.update { it.copy(isDisconnecting = true) }; sendLeavePacket(); stopRescueSignal(); viewModelScope.launch { delay(200); bleManager.disconnect(); _uiState.update { it.copy(isDisconnecting = false) } } }
    fun sendLeaveOnShutdown() { if (::protocolCore.isInitialized) sendLeavePacket() }
    fun stopServicesForShutdown() { if (::bleManager.isInitialized) { bleManager.stopAdvertising(); bleManager.disconnect() }; try { app.startService(Intent(app, RescueService::class.java).apply { action = RescueService.ACTION_STOP_RESCUE }) } catch (e: Exception) {} }
    private fun initAudio() { try { audioEngine = AudioEngine() } catch (e: Exception) { _uiEvents.tryEmit(UiEvent.Toast("오디오 에러")) } }
    private fun initBle() { bleManager = BleManager(app, logCallback = { Log.d("Ble", it) }, audioCallback = { pcm -> audioEngine?.playAudio(pcm) }, textCallback = { msg -> addMessage(ChatMessage(text = msg, isMine = false, senderName = "상대방")) }, protocolCallback = { _, _ -> }, connectionCallback = { connected, count -> refreshDirectPeers() }); bleManager.setLocalPeerId(senderId); bleManager.setRssiActiveMode(bleRssiActiveMode); protocolCore.attachTransport(BleTransport(bleManager)); startBleDebugLoop() }
    private fun initProtocol() {
        val codec = BinaryPacketCodec(); signatureManager = SignatureManager(app, codec, ::appendSignatureLog); senderId = loadOrCreatePeerId(signatureManager)
        protocolCore = ProtocolCore(encoder = codec, decoder = codec, myPeerId = senderId, signatureManager = signatureManager)
        gossipSyncManager = GossipSyncManager(senderId, viewModelScope, object : GossipSyncManager.Sender { override fun broadcast(p: Packet) = protocolCore.broadcast(p); override fun sendToPeer(id: ByteArray, p: Packet) = protocolCore.send(p) })
        profilePacketHandler = ProfilePacketHandler(profileDao, viewModelScope, ::appendProfileLog, { gossipSyncManager.onPublicPacketSeen(it) })
        protocolCore.setOnPacketReceived { packet, relay ->
            if (packet.header.senderId.contentEquals(senderId)) return@setOnPacketReceived
            val path = packetPathLabel(packet, relay); val peer = bytesToHex(packet.header.senderId); emitMeshActivity(peer)
            if (packet.header.type != PacketType.LEAVE && packet.header.type != PacketType.ANNOUNCE) meshGraphRegistry.touchPeer(peer, peerNicknames[peer], System.currentTimeMillis())
            if (relay != null && path == "direct") { bleManager.bindPeerIdForAddress(relay, peer); bleManager.onAnnounceReceived(relay); refreshDirectPeers() }
            when (packet.header.type) {
                PacketType.ANNOUNCE -> {
                    val now = System.currentTimeMillis()
                    val announcement = IdentityAnnouncementPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    val decision = peerIdentityRegistry.handleAnnounce(peer, announcement.nickname, announcement.noisePublicKey, now, ProtocolConstants.Mesh.DUPLICATE_NICKNAME_STALE_MS)
                    if (!decision.accept) return@setOnPacketReceived
                    val flags = packet.header.flags
                    val wa = (flags and ProtocolConstants.Capabilities.WIFI_AWARE) != 0
                    val wd = (flags and ProtocolConstants.Capabilities.WIFI_DIRECT) != 0
                    val uwb = (flags and ProtocolConstants.Capabilities.UWB) != 0
                    val isRescuer = (flags and ProtocolConstants.Capabilities.RESCUER) != 0
                    ConnectionLog.add(
                        "Announce",
                        "flags=0x${flags.toString(16)} rescuer=$isRescuer wa=$wa wd=$wd uwb=$uwb peer=$peer"
                    )
                    meshGraphRegistry.updateFromAnnouncement(peer, announcement.nickname, GossipTlv.decodeNeighborsFromAnnouncementPayload(packet.payload), packet.header.timestamp)
                    meshRegistry.updatePeer(peer, wa, wd, uwb, isRescuer)
                    if (isRescuer) {
                        announcedPeerLastSeen.remove(peer)
                        peerNicknames.remove(peer)
                        peerDirectAddresses.remove(peer)
                        peerBatteryLevels.remove(peer)
                        peerPowerSavingModes.remove(peer)
                        discoveredSurvivors.remove(peer)
                        _uiState.update {
                            it.copy(
                                peerNicknames = peerNicknames.toMap(),
                                peerDirectAddresses = peerDirectAddresses.toMap(),
                                peerBatteryLevels = peerBatteryLevels.toMap(),
                                peerPowerSavingModes = peerPowerSavingModes.toMap()
                            )
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            profileDao.deleteByPeerId(peer)
                        }
                        refreshSurvivorCapabilities()
                        gossipSyncManager.onPublicPacketSeen(packet)
                        return@setOnPacketReceived
                    }
                    announcedPeerLastSeen[peer] = now
                    val directAddress = announcement.wifiDirectAddress?.trim()?.lowercase()?.ifBlank { null }
                    val remoteBattery = announcement.batteryLevel
                    val remotePowerSaving = announcement.powerSavingEnabled
                    if (announcement.nickname.isNotBlank()) {
                        peerNicknames[peer] = announcement.nickname
                    }
                    if (directAddress != null) {
                        peerDirectAddresses[peer] = directAddress
                    }
                    if (remoteBattery != null) {
                        peerBatteryLevels[peer] = remoteBattery
                    }
                    if (remotePowerSaving != null) {
                        peerPowerSavingModes[peer] = remotePowerSaving
                    }
                    _uiState.update { state ->
                        val incomingDirect = if (directAddress != null && state.incomingCallPeerId == peer) {
                            directAddress
                        } else {
                            state.incomingCallDirectAddress
                        }
                        val callPeerDirect = if (directAddress != null && state.callPeerId == peer) {
                            directAddress
                        } else {
                            state.callPeerDirectAddress
                        }
                        state.copy(
                            peerNicknames = peerNicknames.toMap(),
                            peerDirectAddresses = peerDirectAddresses.toMap(),
                            peerBatteryLevels = peerBatteryLevels.toMap(),
                            peerPowerSavingModes = peerPowerSavingModes.toMap(),
                            incomingCallDirectAddress = incomingDirect,
                            callPeerDirectAddress = callPeerDirect
                        )
                    }
                    refreshSurvivorCapabilities()
                    gossipSyncManager.onPublicPacketSeen(packet)
                }
                PacketType.LEAVE -> {
                    meshGraphRegistry.removePeer(peer)
                    meshRegistry.remove(peer)
                    announcedPeerLastSeen.remove(peer)
                    peerDirectAddresses.remove(peer)
                    peerBatteryLevels.remove(peer)
                    peerPowerSavingModes.remove(peer)
                    _uiState.update {
                        it.copy(
                            peerDirectAddresses = peerDirectAddresses.toMap(),
                            peerBatteryLevels = peerBatteryLevels.toMap(),
                            peerPowerSavingModes = peerPowerSavingModes.toMap()
                        )
                    }
                    refreshSurvivorCapabilities()
                }
                PacketType.MESSAGE -> {
                    val myPeerIdHex = bytesToHex(senderId)
                    val recipientHex = packet.header.recipientId?.let { bytesToHex(it) }
                    if (recipientHex != null && recipientHex != myPeerIdHex) {
                        return@setOnPacketReceived
                    }
                    val res = ProfileTlv.decodeIfProfile(packet.payload);
                    if (res != null) {
                        profilePacketHandler.handle(packet, res, path)
                    } else {
                        addMessage(
                            ChatMessage(
                                text = packet.payload.toString(Charsets.UTF_8),
                                isMine = false,
                                path = path,
                                senderName = resolvePeerDisplayName(peer),
                                senderPeerId = peer,
                                recipientPeerId = recipientHex
                            )
                        )
                    }
                }
                PacketType.FILE_TRANSFER -> viewModelScope.launch(Dispatchers.IO) { val p = FileTransferPayload.decode(packet.payload) ?: return@launch; val s = FileTransferStorage.storeIncoming(app, p, packet.header.timestamp) ?: return@launch; addMessage(ChatMessage(text = FileTransferStorage.buildMarker(s), isMine = false, path = path, senderName = resolvePeerDisplayName(peer), senderPeerId = peer)) }
                PacketType.REQUEST_SYNC -> gossipSyncManager.handleRequestSync(packet.header.senderId, RequestSyncPayload.decode(packet.payload) ?: return@setOnPacketReceived)
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
                    // 선택된 대상 외 UWB_SYNC 응답은 무시합니다.
                    if (payload.action == CallHandshakeAction.UWB_SYNC) {
                        val expectedTarget = uwbTargetPeerId
                        if (expectedTarget != null && peer != expectedTarget) {
                            return@setOnPacketReceived
                        }
                    }
                    handleCallHandshake(peer, payload)
                }
                else -> Unit
            }
        }
        startAnnounceLoop(); startMeshCleanupLoop(); gossipSyncManager.start()
    }

    private fun initBatteryMonitor() { app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    private fun updateBatteryLevel(intent: Intent) { val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1); if (l >= 0 && s > 0) _uiState.update { it.copy(batteryLevel = (l * 100 / s)) } }
    private fun addMessage(m: ChatMessage) { _uiState.update { it.copy(messages = it.messages + m) } }
    private fun defaultRescuerName(): String {
        val peerSuffix = bytesToHex(senderId).take(4).ifBlank { "----" }
        return "구조자[$peerSuffix]"
    }
    private fun resolveMyDisplayName(): String {
        val peerSuffix = bytesToHex(senderId).take(4).ifBlank { "----" }
        val nickname = cachedNickname.ifBlank { _uiState.value.myNickname }.trim()
        return when {
            nickname.isBlank() -> "구조자[$peerSuffix]"
            nickname.endsWith("[$peerSuffix]") -> nickname
            else -> "$nickname[$peerSuffix]"
        }
    }
    private fun resolvePeerDisplayName(peerId: String?): String {
        if (peerId.isNullOrBlank()) return "구조자[----]"
        val peerSuffix = peerId.take(4)
        val nickname = peerNicknames[peerId].orEmpty().trim()
        return if (nickname.isNotBlank()) "$nickname[$peerSuffix]" else "구조자[$peerSuffix]"
    }
    private fun emitMeshActivity(id: String) { _meshVisualEvents.tryEmit(MeshVisualEvent.PacketActivity(id)) }
    private fun refreshDirectPeers() {
        val ids = bleManager.getConnectedPeerIds()
        val wasConnected = _uiState.value.directPeerIds.isNotEmpty()
        val isNowConnected = ids.isNotEmpty()
        _uiState.update { it.copy(isConnected = isNowConnected, connectedCount = ids.size, directPeerIds = ids) }
        if (!wasConnected && isNowConnected) {
            broadcastCachedProfileIfNeeded(force = true, reason = "direct-connected")
        }
    }
    private fun startAnnounceLoop() { announceJob = viewModelScope.launch(Dispatchers.IO) { delay(1000); while(true) { sendAnnounce(); delay(ProtocolConstants.Mesh.ANNOUNCE_INTERVAL_MS) } } }
    private fun startMeshCleanupLoop() {
        meshCleanupJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val now = System.currentTimeMillis()
                meshGraphRegistry.prune(ProtocolConstants.Mesh.PEER_TIMEOUT_MS, now)
                pruneAnnouncedPeers(now)
                delay(ProtocolConstants.Mesh.PEER_CLEANUP_INTERVAL_MS)
            }
        }
    }
    private fun startBleDebugLoop() {
        bleDebugJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val s = bleManager.getDebugSnapshot()
                val peerRssi = bleManager.getPeerRssiSnapshot()
                _uiState.update {
                    it.copy(
                        bleDebug = BleDebugStats.fromSnapshot(s),
                        peerRssi = peerRssi
                    )
                }
                delay(if (bleRssiActiveMode) 1000 else 3000)
            }
        }
    }
    private fun sendAnnounce() {
        wifiDirectRanger.refreshLocalDeviceAddress()
        val directAddress = wifiDirectRanger.getLocalDeviceAddress()
        val localBattery = _uiState.value.batteryLevel.coerceIn(0, 100)
        val announceName = cachedNickname
            .ifBlank { _uiState.value.myNickname.trim() }
            .ifBlank { defaultRescuerName() }
        val ann = IdentityAnnouncementPayload(
            nickname = announceName,
            noisePublicKey = signatureManager.getNoisePublicKeyBytes(),
            signingPublicKey = signatureManager.getPublicKeyBytes(),
            wifiDirectAddress = directAddress,
            batteryLevel = localBattery
        )
        val pay = (ann.encode() ?: return) + GossipTlv.encodeNeighbors(bleManager.getConnectedPeerIds())
        val pkt = Packet(PacketHeader(2, PacketType.ANNOUNCE, ProtocolConstants.MESSAGE_TTL_HOPS, getCurrentCapabilityFlags(), pay.size, System.currentTimeMillis(), senderId), pay)
        protocolCore.broadcast(signatureManager.sign(pkt))
    }

    private fun observeProfileState() {
        viewModelScope.launch {
            profileStore.profileFlow.collect { profile ->
                val profileName = profile.name.trim()
                val resolvedName = profileName.ifBlank { defaultRescuerName() }
                if (profileName.isBlank()) {
                    viewModelScope.launch {
                        profileStore.saveProfile(profile.copy(name = resolvedName))
                    }
                }
                cachedProfile = profile.copy(name = resolvedName)
                cachedNickname = resolvedName
                _uiState.update { s -> s.copy(myNickname = resolvedName) }
                val ui = _uiState.value
                if (ui.isRescueSignalActive || ui.directPeerIds.isNotEmpty()) {
                    broadcastCachedProfileIfNeeded(force = false, reason = "profile-change")
                }
            }
        }
    }
    private fun observeProfiles() { viewModelScope.launch { profileDao.getAll().collect { entities -> discoveredSurvivors.clear(); entities.forEach { discoveredSurvivors[it.peerId] = SurvivorProfile(it.name, it.gender, it.birthDate, it.notes, peerId = it.peerId) }; refreshSurvivorCapabilities() } } }
    private fun refreshSurvivorCapabilities() {
        _uiState.update { s ->
            val peerIds = announcedPeerLastSeen.keys.sorted()
            val list = peerIds.mapNotNull { id ->
                val base = discoveredSurvivors[id]
                    ?: SurvivorProfile(name = peerNicknames[id].orEmpty(), peerId = id)
                val info = meshRegistry.getPeer(id)
                val displayName = base.name.ifBlank { peerNicknames[id].orEmpty() }
                if (info?.isRescuer == true || isRescuerDisplayName(displayName)) {
                    return@mapNotNull null
                }
                base.copy(
                    isWifiAware = info?.isWifiAware ?: base.isWifiAware,
                    isWifiDirect = info?.isWifiDirect ?: base.isWifiDirect,
                    // Prefer live capability from announce/handshake; stale DB values should not force UWB UI.
                    isUwb = info?.isUwb ?: false,
                    peerId = id
                )
            }
            s.copy(survivors = list)
        }
    }

    private fun isRescuerDisplayName(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        return trimmed.startsWith("구조자") || trimmed.startsWith("rescuer", ignoreCase = true)
    }
    private fun pruneAnnouncedPeers(now: Long) {
        val cutoff = now - ProtocolConstants.Mesh.PEER_TIMEOUT_MS
        val stale = announcedPeerLastSeen.filterValues { it < cutoff }.keys
        if (stale.isEmpty()) return
        stale.forEach {
            announcedPeerLastSeen.remove(it)
            peerDirectAddresses.remove(it)
            peerBatteryLevels.remove(it)
            peerPowerSavingModes.remove(it)
        }
        _uiState.update {
            it.copy(
                peerDirectAddresses = peerDirectAddresses.toMap(),
                peerBatteryLevels = peerBatteryLevels.toMap(),
                peerPowerSavingModes = peerPowerSavingModes.toMap()
            )
        }
        refreshSurvivorCapabilities()
    }
    private fun observeCallConnection() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
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
    fun isUwbRuntimeAvailableLocally(): Boolean {
        if (!isUwbSupportedLocally()) return false
        return uwbRanger.isRuntimeAvailableCached() != false
    }
    private fun getCurrentCapabilityFlags(): Int {
        var f = 0; val pm = app.packageManager; val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) else false
        if (wifiAwareEnabled && wa && wifi?.isWifiEnabled == true) {
            f = f or ProtocolConstants.Capabilities.WIFI_AWARE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            pm.hasSystemFeature(PackageManager.FEATURE_UWB) &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.UWB_RANGING) == PackageManager.PERMISSION_GRANTED
        ) {
            f = f or ProtocolConstants.Capabilities.UWB
        }
        if (wifiDirectEnabled && pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            f = f or ProtocolConstants.Capabilities.WIFI_DIRECT
        }
        f = f or ProtocolConstants.Capabilities.RESCUER
        return f
    }
    private fun observeMeshGraph() { viewModelScope.launch { meshGraphRegistry.graphState.collect { snap -> _uiState.update { it.copy(meshGraphSnapshot = snap, meshPeerCount = snap.nodes.size.coerceAtLeast(1)) } } } }
    fun clearSignatureLogs() { signatureLogBuffer.clear(); _uiState.update { it.copy(signatureLogs = emptyList()) } }
    fun clearProfileLogs() { profileLogBuffer.clear(); _uiState.update { it.copy(profileLogs = emptyList()) } }
    fun clearDeviceMonitoring() { if (::bleManager.isInitialized) { bleManager.clearAllConnectionsAndMappings(); _uiEvents.tryEmit(UiEvent.Toast("초기화됨")) } }
    private fun appendSignatureLog(e: SignatureLogEntry) { signatureLogBuffer.addLast(e); if (signatureLogBuffer.size > 200) signatureLogBuffer.removeFirst(); _uiState.update { it.copy(signatureLogs = signatureLogBuffer.toList()) } }
    private fun appendProfileLog(e: ProfileSyncLogEntry) { profileLogBuffer.addLast(e); if (profileLogBuffer.size > 200) profileLogBuffer.removeFirst(); _uiState.update { it.copy(profileLogs = profileLogBuffer.toList()) } }
    private fun loadOrCreatePeerId(m: SignatureManager): ByteArray { val d = m.getNoisePublicKeyBytes().sha256Bytes().copyOfRange(0, 8); val s = prefs.getString("sender_id", null); if (s != null) { val b = runCatching { hexToBytes(s) }.getOrNull(); if (b != null && b.contentEquals(d)) return b }; prefs.edit().putString("sender_id", bytesToHex(d)).apply(); return d }
    private fun buildMessagePacket(t: String): Packet = Packet(PacketHeader(2, PacketType.MESSAGE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, t.toByteArray().size, System.currentTimeMillis(), senderId), t.toByteArray())
    private fun sendLeavePacket() { if (::protocolCore.isInitialized) protocolCore.broadcast(Packet(PacketHeader(2, PacketType.LEAVE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, 0, System.currentTimeMillis(), senderId), ByteArray(0))) }
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
        val isHex = parts.all { part -> part.length == 2 && part.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
        return if (isHex) normalized else null
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
    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(h: String): ByteArray = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun mapGender(r: String): Char = when (r.trim()) { "남성", "M", "Male" -> 'M'; "여성", "F", "Female" -> 'F'; else -> 'U' }
    private fun packetPathLabel(p: Packet, r: String?): String { if (r == null) return "unknown"; val ttl = if (p.header.type == PacketType.REQUEST_SYNC) ProtocolConstants.SYNC_TTL_HOPS else ProtocolConstants.MESSAGE_TTL_HOPS; return if (p.header.ttl >= ttl) "direct" else "mesh" }

    override fun onCleared() {
        AppShutdownHooks.clear(); toneGenerator.release(); if (wifiAwareEnabled) wifiAwareRanger.stop(); wifiDirectRanger.stop(); uwbRanger.release(); announceJob?.cancel(); meshCleanupJob?.cancel(); bleDebugJob?.cancel()
        if (::gossipSyncManager.isInitialized) gossipSyncManager.stop()
        if (::bleManager.isInitialized) bleManager.release()
        super.onCleared()
    }
}
