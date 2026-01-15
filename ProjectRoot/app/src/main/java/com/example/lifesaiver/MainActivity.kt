package com.example.lifesaiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bleManager: BleManager
    private var tvLog: TextView? = null
    private var etMessage: EditText? = null
    private var audioEngine: AudioEngine? = null
    private var isMicOn = false

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
                try {
                    Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
                    tvLog?.append("\n⛔ $errorMsg\n")
                } catch (e: Exception) {
                }
            }
        }

        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tv_log)
        etMessage = findViewById(R.id.et_message)

        try {
            audioEngine = AudioEngine()
        } catch (e: Exception) {
            appendLog("❌ 오디오 초기화 실패 (권한 확인 필요)")
        }

        bleManager = BleManager(
            this,
            logCallback = { msg -> appendLog(msg) },
            audioCallback = { pcmData -> audioEngine?.playAudio(pcmData) },
            textCallback = { textMsg -> appendLog("📩 상대방: $textMsg") }
        )

        if (!hasPermissions(requiredPermissions)) {
            ActivityCompat.requestPermissions(this, requiredPermissions, 1)
        } else {
            setupViews()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1) return

        val allGranted = grantResults.isNotEmpty() && grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            setupViews()
        } else {
            appendLog("❌ 권한이 필요합니다. 설정에서 허용해주세요.")
        }
    }

    private fun setupViews() {
        val btnAutoConnect = findViewById<Button>(R.id.btn_auto_connect)
        btnAutoConnect.setOnClickListener {
            try {
                if (bleManager.isLongRangeSupported()) {
                    bleManager.startAutoConnect()
                    btnAutoConnect.isEnabled = false
                    appendLog("🚀 자동 연결 시작 (Long Range)")
                } else {
                    appendLog("⚠️ 일반 모드로 연결 시도")
                    bleManager.startAutoConnect()
                }
            } catch (e: Exception) {
                appendLog("❌ 실행 오류: ${e.message}")
            }
        }

        val btnPtt = findViewById<Button>(R.id.btn_ptt)
        btnPtt.text = "마이크 켜기 (OFF)"
        btnPtt.setOnClickListener {
            if (isMicOn) {
                isMicOn = false
                audioEngine?.stopRecording()
                btnPtt.text = "마이크 켜기 (OFF)"
                appendLog("🔇 마이크 종료")
            } else {
                isMicOn = true
                appendLog("🎤 마이크 시작 (말씀하세요)")
                btnPtt.text = "마이크 끄기 (ON)"
                audioEngine?.startRecording { pcmData ->
                    bleManager.sendAudio(pcmData)
                }
            }
        }

        val btnSendText = findViewById<Button>(R.id.btn_send_text)
        btnSendText.setOnClickListener {
            val msg = etMessage?.text.toString()
            if (msg.isNotEmpty()) {
                bleManager.sendText(msg)
                appendLog("나: $msg")
                etMessage?.text?.clear()
            }
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            tvLog?.append("$msg\n")
            val scroll = tvLog?.parent as? ScrollView
            scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (p in permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}
