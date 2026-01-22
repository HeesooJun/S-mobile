package com.example.lifesaiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.lifesaiver.core.audio.AudioEngine
import com.example.lifesaiver.core.ble.BleManager
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.theme.LifesaiverTheme

class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasPermissions by mutableStateOf(false)
    private var isMicOn by mutableStateOf(false)
    private var isConnected by mutableStateOf(false)
    private var batteryLevel by mutableStateOf(100)
    private val messages = mutableStateListOf<ChatMessage>()

    private var audioEngine: AudioEngine? = null
    private lateinit var bleManager: BleManager
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent == null) return
            updateBatteryLevel(intent)
        }
    }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val errorMsg = "오류: ${throwable.message}"
            Log.e("CRASH_HANDLER", errorMsg, throwable)
            runOnUiThread {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        initAudio()
        initBle()
        initBatteryMonitor()

        hasPermissions = hasAllPermissions()
        if (!hasPermissions) {
            requestPermissions()
        }

        setContent {
            LifesaiverTheme(darkTheme = true, dynamicColor = false) {
                LifesaiverApp(
                    hasPermissions = hasPermissions,
                    batteryLevel = batteryLevel,
                    isConnected = isConnected,
                    isMicOn = isMicOn,
                    messages = messages,
                    onRequestPermissions = { requestPermissions() },
                    onStartAutoConnect = { bleManager.startAutoConnect() },
                    onToggleMic = { toggleMic() },
                    onSendMessage = { text ->
                        if (text.isNotBlank()) {
                            bleManager.sendText(text)
                            addMessage(ChatMessage(text = text, isMine = true))
                        }
                    },
                    onDisconnect = { bleManager.disconnect() }
                )
            }
        }
    }

    private fun initAudio() {
        try {
            audioEngine = AudioEngine()
        } catch (e: Exception) {
            toast("오디오 초기화 실패 (권한 확인 필요)")
        }
    }

    private fun initBle() {
        bleManager = BleManager(
            this,
            logCallback = { msg -> Log.d("BleManager", msg) },
            audioCallback = { pcmData -> audioEngine?.playAudio(pcmData) },
            textCallback = { textMsg -> addMessage(ChatMessage(text = textMsg, isMine = false)) },
            connectionCallback = { connected -> mainHandler.post { isConnected = connected } }
        )
    }

    private fun initBatteryMonitor() {
        val intent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryLevel(intent)
        }
    }

    private fun updateBatteryLevel(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            batteryLevel = (level * 100 / scale)
        }
    }

    private fun toggleMic() {
        if (audioEngine == null) {
            toast("오디오 초기화 실패")
            isMicOn = false
            return
        }
        if (isMicOn) {
            isMicOn = false
            audioEngine?.stopRecording()
            toast("마이크 종료")
        } else {
            isMicOn = true
            toast("마이크 시작")
            audioEngine?.startRecording { pcmData ->
                bleManager.sendAudio(pcmData)
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        mainHandler.post { messages.add(message) }
    }

    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1) return

        hasPermissions = grantResults.isNotEmpty() && grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            toast("권한이 필요합니다. 설정에서 허용해주세요.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine?.stopRecording()
        bleManager.disconnect()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
        }
    }
}
