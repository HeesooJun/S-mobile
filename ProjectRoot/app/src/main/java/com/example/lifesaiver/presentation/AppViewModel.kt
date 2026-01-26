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
import com.example.lifesaiver.core.ble.BleManager
import com.example.lifesaiver.core.ble.BleTransport
import com.example.lifesaiver.core.model.ChatMessage
// [필수] RescueService import 확인 (패키지명에 맞게 수정)
import com.example.lifesaiver.core.service.RescueService
import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.core.ProtocolCore
import com.example.lifesaiver.protocol.model.FileTransferPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
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
import kotlin.random.Random

// UI 상태
data class AppUiState(
    val hasPermissions: Boolean = false,
    val batteryLevel: Int = 100,
    val isConnected: Boolean = false,
    val isMicOn: Boolean = false,
    val isDisconnecting: Boolean = false,
    val isRescueSignalActive: Boolean = false, // 구조 신호 활성화 여부
    val messages: List<ChatMessage> = emptyList()
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

    // 권한 목록
    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE // [필수] 서비스 권한
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

    // 사이렌 발생기
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private val prefs by lazy { app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val senderId: ByteArray by lazy {
        val savedHex = prefs.getString("sender_id", null)
        if (savedHex != null) {
            hexToBytes(savedHex)
        } else {
            val newId = ByteArray(8).also { Random.nextBytes(it) }
            prefs.edit().putString("sender_id", bytesToHex(newId)).apply()
            newId
        }
    }

    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null

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
    }

    // ------------------------------------------------------------------------
    // [핵심 기능] 구조 요청 신호 + 백그라운드 서비스 제어
    // ------------------------------------------------------------------------

    /**
     * SOS 버튼 클릭 시 호출
     */
    fun startRescueSignal() {
        if (!_uiState.value.hasPermissions) {
            _uiEvents.tryEmit(UiEvent.Toast("블루투스 및 서비스 권한이 필요합니다."))
            return
        }

        // 1. BleManager: 72시간 생존 모드 신호 송출 시작
        bleManager.startEmergencyAdvertising()

        // 2. [추가] RescueService: 백그라운드에서 죽지 않도록 서비스 시작
        // (이 코드가 없으면 화면 껐을 때 신호가 멈출 수 있음)
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
        // 메시지는 BleManager의 onModeChange 콜백을 통해 토스트로 출력됨
    }

    /**
     * 구조 요청 중단
     */
    fun stopRescueSignal() {
        // 1. 신호 중단
        bleManager.stopAdvertising()

        // 2. [추가] 백그라운드 서비스 종료
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

    // 사이렌 울리기 (비동기)
    private fun playSiren() {
        viewModelScope.launch(Dispatchers.Default) {
            repeat(5) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                delay(1500)
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
        val file = voiceRecorder?.stop() ?: recordingFile
        voiceRecorder = null
        recordingFile = null
        _uiState.update { it.copy(isMicOn = false) }

        if (file == null || !file.exists()) return

        viewModelScope.launch(Dispatchers.IO) {
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
        protocolCore.broadcast(buildMessagePacket(text))
        addMessage(ChatMessage(text = text, isMine = true))
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
            protocolCallback = { },
            connectionCallback = { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        )

        // 구조대 발견 시 사이렌
        bleManager.onRescueConnected = {
            playSiren()
            _uiEvents.tryEmit(UiEvent.Toast("🚨 구조대가 발견했습니다! (소리 발생)"))
        }

        // 모드 변경 알림
        bleManager.onModeChange = { message ->
            _uiEvents.tryEmit(UiEvent.Toast(message))
        }

        protocolCore.attachTransport(BleTransport(bleManager))
    }

    private fun initProtocol() {
        val codec = BinaryPacketCodec()
        protocolCore = ProtocolCore(codec, codec)
        protocolCore.setOnPacketReceived { packet ->
            if (packet.header.senderId.contentEquals(senderId)) return@setOnPacketReceived

            when (packet.header.type) {
                PacketType.MESSAGE -> {
                    val text = packet.payload.toString(Charsets.UTF_8)
                    addMessage(ChatMessage(text = text, isMine = false))
                }
                PacketType.FILE_TRANSFER -> {
                    viewModelScope.launch(Dispatchers.IO) {
                        val payload = FileTransferPayload.decode(packet.payload) ?: return@launch
                        val inDir = File(app.filesDir, "voicenotes/incoming")
                        if (!inDir.exists()) inDir.mkdirs()

                        val name = payload.fileName?.takeIf { it.isNotBlank() } ?: "voice_${packet.header.timestamp}.m4a"
                        val file = File(inDir, name)

                        runCatching { file.writeBytes(payload.content) }
                        addMessage(ChatMessage(text = "[voice] ${file.absolutePath}", isMine = false))
                    }
                }
                else -> Unit
            }
        }
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

    override fun onCleared() {
        audioEngine?.stopRecording()
        voiceRecorder?.stop()
        toneGenerator.release()
        bleManager.stopAdvertising()
        bleManager.disconnect()

        // 앱 종료 시 서비스도 같이 종료 (필요에 따라 주석 처리 가능)
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
