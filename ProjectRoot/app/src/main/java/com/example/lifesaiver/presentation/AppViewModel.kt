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

    // [수정] 사이렌 발생기 및 오디오 매니저 (볼륨 제어용)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
     * [수정] 사이렌 울리기 (강제 최대 볼륨 적용)
     * 시나리오: 현재 볼륨 저장 -> 최대 볼륨 설정 -> 사이렌 발사 -> 원래 볼륨 복구
     */
    private fun playSiren() {
        viewModelScope.launch(Dispatchers.Default) {
            // 1. 현재 알람 볼륨 저장 (나중에 복구를 위해)
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            // 2. 알람 채널의 최대 볼륨 가져오기
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            try {
                // 3. 볼륨을 강제로 최대로 설정 (플래그 0: UI 표시 안 함)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

                // 4. 사이렌 울리기 (5회 반복)
                repeat(5) {
                    // TONE_CDMA_ALERT_CALL_GUARD: 매우 시끄럽고 긴급한 소리
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    delay(1500)
                }
            } catch (e: Exception) {
                Log.e("Siren", "Error playing siren: ${e.message}")
            } finally {
                // 5. 사이렌이 끝나면 원래 볼륨으로 복구 (매너 모드)
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
                } catch (e: Exception) {
                    Log.e("Siren", "Error restoring volume: ${e.message}")
                }
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
