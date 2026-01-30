package com.example.lifesaiver.presentation

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
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
import com.example.lifesaiver.core.media.FileTransferStorage
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.core.service.RescueService
import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.core.ProtocolCore
import com.example.lifesaiver.protocol.mesh.GossipTlv
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.protocol.mesh.PeerIdentityRegistry
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
    val profileLogs: List<ProfileSyncLogEntry> = emptyList()
)

// UI 이벤트
sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

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
    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    private lateinit var bleManager: BleManager
    private lateinit var protocolCore: ProtocolCore
    private lateinit var signatureManager: SignatureManager
    private lateinit var gossipSyncManager: GossipSyncManager
    private lateinit var profilePacketHandler: ProfilePacketHandler
    private val signatureLogBuffer = ArrayDeque<SignatureLogEntry>()
    private val profileLogBuffer = ArrayDeque<ProfileSyncLogEntry>()
    private val profileDao by lazy { AppDatabase.getInstance(app).profileDao() }

    // [수정] 사이렌 발생기 및 오디오 매니저 (볼륨 제어용)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private var senderId: ByteArray = ByteArray(0)
    private val profileStore = ProfileStore(app)
    @Volatile private var cachedNickname: String = ""
    private val announcedToPeers = mutableSetOf<String>()
    private val peerNicknames = mutableMapOf<String, String>()

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
        observeMeshGraph()
        _uiState.update { it.copy(myPeerId = bytesToHex(senderId)) }
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
                action = "START_RESCUE"
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
                action = "STOP_RESCUE"
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
                    updateMeshCount()
                    gossipSyncManager.onPublicPacketSeen(packet)
                }
                PacketType.LEAVE -> {
                    meshGraphRegistry.removePeer(peerHex)
                    peerIdentityRegistry.removePeer(peerHex)
                    gossipSyncManager.removeAnnouncementForPeer(peerHex)
                    announcedToPeers.remove(peerHex)
                    if (peerNicknames.remove(peerHex) != null) {
                        _uiState.update { it.copy(peerNicknames = peerNicknames.toMap()) }
                    }
                    updateMeshCount()
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
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.ANNOUNCE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
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
        audioEngine?.stopRecording()
        voiceRecorder?.stop()
        toneGenerator.release()
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
            action = "STOP_RESCUE"
        }
        app.startService(intent)

        try {
            app.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) { }
        super.onCleared()
    }
}
