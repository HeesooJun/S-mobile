package com.example.lifesaiver.wakeup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lifesaiver.R

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 일반 권한 허용 시 서비스 갱신 시도
        refreshServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 일반 권한 (마이크, 알림, 전화 상태) 체크
        checkAndRequestPermissions()

        // 2. [복구] 다른 앱 위에 표시 권한 체크 (비상 화면 띄우기 필수)
        checkOverlayPermission()

        // 3. 서비스 실행 (권한이 있다면)
        refreshServices()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면이나 권한 설정 갔다가 돌아왔을 때 갱신
        refreshServices()
    }

    // ★ 다른 앱 위에 표시 권한 확인 및 요청 함수
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "비상 알림을 위해 '다른 앱 위에 표시' 권한을 허용해주세요.", Toast.LENGTH_LONG).show()

                // 설정 화면으로 이동하는 인텐트
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun refreshServices() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isVoiceOn = prefs.getBoolean("voice_detection", false)
        val isShockOn = prefs.getBoolean("shock_detection", false)

        // 1. 음성 서비스
        if (isVoiceOn) {
            // 마이크 권한이 있을 때만 실행 (크래시 방지)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceSafe(VoiceService::class.java)
            }
        } else {
            stopService(Intent(this, VoiceService::class.java))
        }

        // 2. 센서 서비스
        if (isShockOn) {
            startServiceSafe(SensorService::class.java)
        } else {
            stopService(Intent(this, SensorService::class.java))
        }
    }

    private fun startServiceSafe(serviceClass: Class<*>) {
        val intent = Intent(applicationContext, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

}
