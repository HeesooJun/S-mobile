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
    private val forcePcmCall = true
    private val wifiAwareEnabled = true
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
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.FOREGROUND_SERVICE)
    }.toTypedArray()

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
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private var senderId: ByteArray = ByteArray(0)
    private val profileStore = ProfileStore(app)
    @Volatile private var cachedNickname: String = ""
    private val announcedToPeers = mutableSetOf<String>()
    private val peerNicknames = mutableMapOf<String, String>()
    private val peerDirectAddresses = ConcurrentHashMap<String, String>()
    private val discoveredSurvivors = mutableMapOf<String, SurvivorProfile>()
    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null
    private val meshGraphRegistry = MeshGraphRegistry()
    private val peerIdentityRegistry = PeerIdentityRegistry()
    private val announcedPeerLastSeen = ConcurrentHashMap<String, Long>()
    private var announceJob: kotlinx.coroutines.Job? = null
    private var meshCleanupJob: kotlinx.coroutines.Job? = null
    private var bleDebugJob: kotlinx.coroutines.Job? = null
    private var lastConnectionAnnounceMs: Long = 0L
    private val connectionAnnounceCooldownMs: Long = 3_000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { intent?.let { updateBatteryLevel(it) } }
    }

    init {
        initProtocol()
        initBle()
        initBatteryMonitor()
        refreshPermissions()
        wifiDirectRanger.refreshLocalDeviceAddress()
        observeProfileName()
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
    }

    fun pulseRescueSignal() { if (::bleManager.isInitialized) bleManager.pulseEmergencyAdvertising() }
    fun stopRescueSignal() { bleManager.stopAdvertising(); try { app.startService(Intent(app, RescueService::class.java).apply { action = RescueService.ACTION_STOP_RESCUE }) } catch (e: Exception) {} ; _uiState.update { it.copy(isRescueSignalActive = false) } }
    fun refreshPermissions() {
        val granted = requiredPermissions.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            wifiDirectRanger.refreshLocalDeviceAddress()
            if (audioEngine == null) initAudio()
        }
    }
    fun onPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) {
            wifiDirectRanger.refreshLocalDeviceAddress()
            if (audioEngine == null) initAudio()
        }
    }
    fun onStartAutoConnect() { bleManager.startAutoConnect() }
    fun onStopAutoConnect() { bleManager.disconnect() }
    fun onMicPress() { if (_uiState.value.isMicOn) return; val outDir = File(app.filesDir, "voicenotes/outgoing"); if (!outDir.exists()) outDir.mkdirs(); val recorder = VoiceRecorder(outDir); val file = recorder.start() ?: return; voiceRecorder = recorder; recordingFile = file; _uiState.update { it.copy(isMicOn = true) } }
    fun onMicRelease() { if (!_uiState.value.isMicOn) return; val recorder = voiceRecorder; val pendingFile = recordingFile; voiceRecorder = null; recordingFile = null; _uiState.update { it.copy(isMicOn = false) }; viewModelScope.launch(Dispatchers.IO) { delay(500); val file = recorder?.stop() ?: pendingFile; if (file == null || !file.exists()) return@launch; val bytes = file.readBytes(); val payload = FileTransferPayload(file.name, bytes.size.toLong(), "audio/mp4", bytes).encode(); protocolCore.broadcast(Packet(PacketHeader(2, PacketType.FILE_TRANSFER, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, System.currentTimeMillis(), senderId), payload)); addMessage(ChatMessage(text = "[voice] ${file.absolutePath}", isMine = true)) } }
    fun onSendMessage(text: String) { if (text.isBlank()) return; val packet = buildMessagePacket(text); val signed = signatureManager.sign(packet); gossipSyncManager.onPublicPacketSeen(signed); protocolCore.broadcast(signed); addMessage(ChatMessage(text = text, isMine = true)) }
    fun ensureWifiAwarePermissions(): Boolean { val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager; if (wifi?.isWifiEnabled == false) { _uiEvents.tryEmit(UiEvent.Toast("Wi-Fi를 켜주세요.")); return false }; return true }

    fun sendProfileUpdate(profile: SurvivorProfile) {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(profile.name, mapGender(profile.gender), profile.birthDate, profile.notes, now)
        val packet = Packet(PacketHeader(2, PacketType.MESSAGE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, now, senderId), payload)
        gossipSyncManager.onPublicPacketSeen(packet); protocolCore.broadcast(packet)
    }

    fun sendCallHandshake(targetPeerIdHex: String, action: CallHandshakeAction, callerName: String, wifiAwareSupported: Boolean, wifiDirectSupported: Boolean, useOpus: Boolean, state: CallHandshakeState? = null, rttCm: Int? = null) {
        if (targetPeerIdHex.isBlank()) return
        wifiDirectRanger.refreshLocalDeviceAddress()
        val awareSupported = if (wifiAwareEnabled) wifiAwareSupported else false
        val rtt = if (wifiAwareEnabled) {
            rttCm ?: wifiAwareRanger.isConnectionReady.value.let { if (!it) null else wifiAwareRanger.rttDistance.value }
                ?.let { (it * 100f).roundToInt().coerceIn(0, 0xFFFF) }
        } else {
            null
        }
        val directAddr = wifiDirectRanger.getLocalDeviceAddress()
        val payload = CallHandshakePayload(
            action,
            callerName,
            awareSupported,
            wifiDirectSupported,
            if (forcePcmCall) false else useOpus,
            state,
            rtt,
            directAddr
        ).encode()
        protocolCore.broadcast(Packet(PacketHeader(2, PacketType.CALL_HANDSHAKE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, System.currentTimeMillis(), senderId, hexToBytes(targetPeerIdHex)), payload))
    }

    fun clearIncomingCall(peerIdHex: String) {
        _uiState.update {
            if (it.incomingCallPeerId != peerIdHex) it else it.copy(
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
                val directFromPayload = cachePeerDirectAddress(peerIdHex, payload.directDeviceAddress, "handshake-start")
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                _uiState.update {
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
            CallHandshakeAction.END -> {
                _uiState.update { s ->
                    val ci = s.incomingCallPeerId == peerIdHex; val cp = s.callPeerId == peerIdHex
                    if (!ci && !cp) {
                        s
                    } else {
                        s.copy(
                            incomingCallPeerId = if (ci) null else s.incomingCallPeerId,
                            incomingCallDirectAddress = if (ci) null else s.incomingCallDirectAddress,
                            callPeerId = if (cp) null else s.callPeerId,
                            callPeerDirectAddress = if (cp) null else s.callPeerDirectAddress
                        )
                    }
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
                val directFromPayload = cachePeerDirectAddress(peerIdHex, payload.directDeviceAddress, "handshake-ack")
                val announcedDirect = peerDirectAddresses[peerIdHex]
                val resolvedDirect = directFromPayload ?: announcedDirect
                meshRegistry.updatePeer(peerIdHex, wa, wd, peerInfo?.isUwb ?: false); refreshSurvivorCapabilities()
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
        }
    }

    fun sendProfileTestPacket() {
        val now = System.currentTimeMillis()
        val payload = ProfileTlv.encodeUpdate(cachedNickname.ifBlank { "rescuer-user" }, 'U', "1990-01-01", "rescuer-test", now)
        val packet = Packet(PacketHeader(2, PacketType.MESSAGE, ProtocolConstants.MESSAGE_TTL_HOPS, 0, payload.size, now, senderId), payload)
        gossipSyncManager.onPublicPacketSeen(packet); protocolCore.broadcast(packet)
    }

    fun onDisconnect() { if (_uiState.value.isDisconnecting) return; _uiState.update { it.copy(isDisconnecting = true) }; sendLeavePacket(); stopRescueSignal(); viewModelScope.launch { delay(200); bleManager.disconnect(); _uiState.update { it.copy(isDisconnecting = false) } } }
    fun sendLeaveOnShutdown() { if (::protocolCore.isInitialized) sendLeavePacket() }
    fun stopServicesForShutdown() { if (::bleManager.isInitialized) { bleManager.stopAdvertising(); bleManager.disconnect() }; try { app.startService(Intent(app, RescueService::class.java).apply { action = RescueService.ACTION_STOP_RESCUE }) } catch (e: Exception) {} }
    private fun initAudio() { try { audioEngine = AudioEngine() } catch (e: Exception) { _uiEvents.tryEmit(UiEvent.Toast("오디오 에러")) } }
    private fun initBle() { bleManager = BleManager(app, logCallback = { Log.d("Ble", it) }, audioCallback = { pcm -> audioEngine?.playAudio(pcm) }, textCallback = { msg -> addMessage(ChatMessage(text = msg, isMine = false)) }, protocolCallback = { _, _ -> }, connectionCallback = { connected, count -> refreshDirectPeers() }); bleManager.setLocalPeerId(senderId); protocolCore.attachTransport(BleTransport(bleManager)); startBleDebugLoop() }
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
                    announcedPeerLastSeen[peer] = now
                    val directAddress = announcement.wifiDirectAddress?.trim()?.lowercase()?.ifBlank { null }
                    if (announcement.nickname.isNotBlank()) {
                        peerNicknames[peer] = announcement.nickname
                    }
                    if (directAddress != null) {
                        peerDirectAddresses[peer] = directAddress
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
                            incomingCallDirectAddress = incomingDirect,
                            callPeerDirectAddress = callPeerDirect
                        )
                    }
                    meshGraphRegistry.updateFromAnnouncement(peer, announcement.nickname, GossipTlv.decodeNeighborsFromAnnouncementPayload(packet.payload), packet.header.timestamp)
                    meshRegistry.updatePeer(peer, (packet.header.flags and 0x01) != 0, (packet.header.flags and 0x04) != 0, (packet.header.flags and 0x02) != 0)
                    refreshSurvivorCapabilities(); gossipSyncManager.onPublicPacketSeen(packet)
                }
                PacketType.LEAVE -> {
                    meshGraphRegistry.removePeer(peer)
                    meshRegistry.remove(peer)
                    announcedPeerLastSeen.remove(peer)
                    peerDirectAddresses.remove(peer)
                    _uiState.update { it.copy(peerDirectAddresses = peerDirectAddresses.toMap()) }
                    refreshSurvivorCapabilities()
                }
                PacketType.MESSAGE -> {
                    val res = ProfileTlv.decodeIfProfile(packet.payload);
                    if (res != null) profilePacketHandler.handle(packet, res, path) else addMessage(ChatMessage(text = packet.payload.toString(Charsets.UTF_8), isMine = false, path = path))
                }
                PacketType.FILE_TRANSFER -> viewModelScope.launch(Dispatchers.IO) { val p = FileTransferPayload.decode(packet.payload) ?: return@launch; val s = FileTransferStorage.storeIncoming(app, p, packet.header.timestamp) ?: return@launch; addMessage(ChatMessage(text = FileTransferStorage.buildMarker(s), isMine = false, path = path)) }
                PacketType.REQUEST_SYNC -> gossipSyncManager.handleRequestSync(packet.header.senderId, RequestSyncPayload.decode(packet.payload) ?: return@setOnPacketReceived)
                PacketType.CALL_HANDSHAKE -> handleCallHandshake(peer, CallHandshakePayload.decode(packet.payload) ?: return@setOnPacketReceived)
                else -> Unit
            }
        }
        startAnnounceLoop(); startMeshCleanupLoop(); gossipSyncManager.start()
    }

    private fun initBatteryMonitor() { app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    private fun updateBatteryLevel(intent: Intent) { val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1); if (l >= 0 && s > 0) _uiState.update { it.copy(batteryLevel = (l * 100 / s)) } }
    private fun addMessage(m: ChatMessage) { _uiState.update { it.copy(messages = it.messages + m) } }
    private fun emitMeshActivity(id: String) { _meshVisualEvents.tryEmit(MeshVisualEvent.PacketActivity(id)) }
    private fun refreshDirectPeers() { val ids = bleManager.getConnectedPeerIds(); _uiState.update { it.copy(isConnected = ids.isNotEmpty(), connectedCount = ids.size, directPeerIds = ids) } }
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
                delay(3000)
            }
        }
    }
    private fun sendAnnounce() {
        wifiDirectRanger.refreshLocalDeviceAddress()
        val directAddress = wifiDirectRanger.getLocalDeviceAddress()
        val ann = IdentityAnnouncementPayload(
            nickname = cachedNickname.ifBlank { bytesToHex(senderId) },
            noisePublicKey = signatureManager.getNoisePublicKeyBytes(),
            signingPublicKey = signatureManager.getPublicKeyBytes(),
            wifiDirectAddress = directAddress
        )
        val pay = (ann.encode() ?: return) + GossipTlv.encodeNeighbors(bleManager.getConnectedPeerIds())
        val pkt = Packet(PacketHeader(2, PacketType.ANNOUNCE, ProtocolConstants.MESSAGE_TTL_HOPS, getCurrentCapabilityFlags(), pay.size, System.currentTimeMillis(), senderId), pay)
        protocolCore.broadcast(signatureManager.sign(pkt))
    }

    private fun observeProfileName() { viewModelScope.launch { profileStore.profileFlow.collect { cachedNickname = it.name.trim(); _uiState.update { s -> s.copy(myNickname = cachedNickname) } } } }
    private fun observeProfiles() { viewModelScope.launch { profileDao.getAll().collect { entities -> discoveredSurvivors.clear(); entities.forEach { discoveredSurvivors[it.peerId] = SurvivorProfile(it.name, it.gender, it.birthDate, it.notes, peerId = it.peerId) }; refreshSurvivorCapabilities() } } }
    private fun refreshSurvivorCapabilities() {
        _uiState.update { s ->
            val peerIds = announcedPeerLastSeen.keys.sorted()
            val list = peerIds.map { id ->
                val base = discoveredSurvivors[id]
                    ?: SurvivorProfile(name = peerNicknames[id].orEmpty(), peerId = id)
                val info = meshRegistry.getPeer(id)
                base.copy(
                    isWifiAware = info?.isWifiAware ?: base.isWifiAware,
                    isWifiDirect = info?.isWifiDirect ?: base.isWifiDirect,
                    isUwb = info?.isUwb ?: base.isUwb,
                    peerId = id
                )
            }
            s.copy(survivors = list)
        }
    }
    private fun pruneAnnouncedPeers(now: Long) {
        val cutoff = now - ProtocolConstants.Mesh.PEER_TIMEOUT_MS
        val stale = announcedPeerLastSeen.filterValues { it < cutoff }.keys
        if (stale.isEmpty()) return
        stale.forEach {
            announcedPeerLastSeen.remove(it)
            peerDirectAddresses.remove(it)
        }
        _uiState.update { it.copy(peerDirectAddresses = peerDirectAddresses.toMap()) }
        refreshSurvivorCapabilities()
    }
    private fun observeCallConnection() { viewModelScope.launch { wifiDirectRanger.isConnectionReady.collect { r -> _uiState.update { it.copy(isCallConnected = r) } } } }
    fun isWifiAwareSupportedLocally(): Boolean = if (wifiAwareEnabled) {
        (getCurrentCapabilityFlags() and 0x01) != 0
    } else {
        false
    }
    fun isWifiDirectSupportedLocally(): Boolean = (getCurrentCapabilityFlags() and 0x04) != 0
    private fun getCurrentCapabilityFlags(): Int {
        var f = 0; val pm = app.packageManager; val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wa = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) else false
        if (wifiAwareEnabled && wa && wifi?.isWifiEnabled == true) f = f or 0x01
        if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) f = f or 0x04
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
    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(h: String): ByteArray = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun mapGender(r: String): Char = when (r.trim()) { "남성", "M", "Male" -> 'M'; "여성", "F", "Female" -> 'F'; else -> 'U' }
    private fun packetPathLabel(p: Packet, r: String?): String { if (r == null) return "unknown"; val ttl = if (p.header.type == PacketType.REQUEST_SYNC) ProtocolConstants.SYNC_TTL_HOPS else ProtocolConstants.MESSAGE_TTL_HOPS; return if (p.header.ttl >= ttl) "direct" else "mesh" }

    override fun onCleared() {
        AppShutdownHooks.clear(); toneGenerator.release(); wifiAwareRanger.stop(); wifiDirectRanger.stop(); announceJob?.cancel(); meshCleanupJob?.cancel(); bleDebugJob?.cancel()
        if (::gossipSyncManager.isInitialized) gossipSyncManager.stop()
        if (::bleManager.isInitialized) bleManager.release()
        super.onCleared()
    }
}
