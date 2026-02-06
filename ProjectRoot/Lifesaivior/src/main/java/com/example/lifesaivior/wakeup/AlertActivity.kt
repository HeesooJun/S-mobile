package com.example.lifesaivior.wakeup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.lifesaivior.R

class AlertActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        turnOnScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnOnScreen()
        setContentView(R.layout.activity_alert)

        // [추가된 부분 1] 비상 상황이 발생했으니 감시 서비스들은 모두 종료!
        stopMonitoringServices()

        val stopButton = findViewById<Button>(R.id.btn_stop_alarm)
        stopButton.setOnClickListener {
            // [선택 사항] 여기서 '오인 신고'라면 다시 서비스를 켜는 로직을 넣을 수도 있음
            finish()
        }

        // [추가된 부분 2] 서비스에서 보낸 '감지 원인' 받기
        // (SensorService와 VoiceService에서 "triggerReason"이란 이름으로 보냈음)
        handleTriggerReason(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        turnOnScreen()
        handleTriggerReason(intent)
    }

    // ★ 핵심: 감시 서비스 2개 강제 종료
    private fun stopMonitoringServices() {
        val sensorIntent = Intent(this, SensorService::class.java)
        val voiceIntent = Intent(this, VoiceService::class.java)

        stopService(sensorIntent)
        stopService(voiceIntent)
    }

    private fun handleTriggerReason(intent: Intent?) {
        // 기존 코드의 "isFallDetected" 대신, 두 서비스가 공통으로 보내는 "triggerReason"을 받음
        val reason = intent?.getStringExtra("triggerReason")

        if (reason != null) {
            showEmergencyDialog(reason)
        }
    }

    private fun showEmergencyDialog(reason: String) {
        AlertDialog.Builder(this)
            .setTitle("🚨 비상 상황 감지!")
            .setMessage("감지 원인: $reason\n\n위급 상황입니까?\n(확인을 누르면 구조 요청이 전송됩니다)")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("구조 요청") { dialog, _ ->
                // TODO: 여기에 실제 구조 요청(문자 전송 등) 로직 추가
                dialog.dismiss()
            }
            .setNegativeButton("취소 (오인)") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun turnOnScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
