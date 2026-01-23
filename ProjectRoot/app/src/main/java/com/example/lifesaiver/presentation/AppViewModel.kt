package com.example.lifesaiver.presentation

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.lifesaiver.core.audio.AudioEngine
import com.example.lifesaiver.core.audio.VoiceRecorder
import com.example.lifesaiver.core.ble.BleManager
import com.example.lifesaiver.core.ble.BleTransport
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.core.ProtocolCore
import com.example.lifesaiver.protocol.model.FileTransferPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import java.io.File

data class AppUiState(
    val hasPermissions: Boolean = false,
    val batteryLevel: Int = 100,
    val isConnected: Boolean = false,
    val isMicOn: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = getApplication<Application>()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private var audioEngine: AudioEngine? = null
    private lateinit var bleManager: BleManager
    private lateinit var protocolCore: ProtocolCore
    private val senderId: ByteArray = ByteArray(8).also { Random.nextBytes(it) }
    private var voiceRecorder: VoiceRecorder? = null
    private var recordingFile: File? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            updateBatteryLevel(intent)
        }
    }

    init {
        initAudio()
        initProtocol()
        initBle()
        initBatteryMonitor()
        refreshPermissions()
    }

    fun refreshPermissions() {
        val granted = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermissions = granted)
        if (!granted) {
            _uiEvents.tryEmit(UiEvent.Toast("권한이 필요합니다. 설정에서 허용해주세요."))
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
        val recorder = VoiceRecorder(outDir)
        val file = recorder.start()
        if (file == null) {
            _uiEvents.tryEmit(UiEvent.Toast("녹음 시작 실패"))
            return
        }
        voiceRecorder = recorder
        recordingFile = file
        _uiState.value = _uiState.value.copy(isMicOn = true)
    }

    fun onMicRelease() {
        if (!_uiState.value.isMicOn) return
        val file = voiceRecorder?.stop() ?: recordingFile
        voiceRecorder = null
        recordingFile = null
        _uiState.value = _uiState.value.copy(isMicOn = false)

        if (file == null || !file.exists()) return

        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
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

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        protocolCore.broadcast(buildMessagePacket(text))
        addMessage(ChatMessage(text = text, isMine = true))
    }

    fun onDisconnect() {
        bleManager.disconnect()
    }

    private fun initAudio() {
        try {
            audioEngine = AudioEngine()
        } catch (e: Exception) {
            _uiEvents.tryEmit(UiEvent.Toast("오디오 초기화 실패 (권한 확인 필요)"))
        }
    }

    private fun initBle() {
        bleManager = BleManager(
            app,
            logCallback = { msg -> Log.d("BleManager", msg) },
            audioCallback = { pcmData -> audioEngine?.playAudio(pcmData) },
            textCallback = { textMsg -> addMessage(ChatMessage(text = textMsg, isMine = false)) },
            protocolCallback = {},
            connectionCallback = { connected ->
                mainHandler.post {
                    _uiState.value = _uiState.value.copy(isConnected = connected)
                }
            }
        )
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
                    val payload = FileTransferPayload.decode(packet.payload) ?: return@setOnPacketReceived
                    val inDir = File(app.filesDir, "voicenotes/incoming")
                    if (!inDir.exists()) inDir.mkdirs()
                    val name = payload.fileName?.takeIf { it.isNotBlank() } ?: "voice_${packet.header.timestamp}.m4a"
                    val file = File(inDir, name)
                    runCatching { file.writeBytes(payload.content) }
                    addMessage(ChatMessage(text = "[voice] ${file.absolutePath}", isMine = false))
                }
                else -> Unit
            }
        }
    }

    private fun initBatteryMonitor() {
        val intent = app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryLevel(intent)
        }
    }

    private fun updateBatteryLevel(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            val percent = (level * 100 / scale)
            _uiState.value = _uiState.value.copy(batteryLevel = percent)
        }
    }

    private fun addMessage(message: ChatMessage) {
        mainHandler.post {
            val current = _uiState.value
            _uiState.value = current.copy(messages = current.messages + message)
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

    override fun onCleared() {
        audioEngine?.stopRecording()
        voiceRecorder?.stop()
        bleManager.disconnect()
        try {
            app.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        super.onCleared()
    }
}
