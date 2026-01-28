package com.example.lifesaiver.wakeup

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.lifesaiver.R

class AlertActivity : AppCompatActivity() {

    // 화면이 켜질 때마다 실행 (중요!)
    override fun onResume() {
        super.onResume()
        turnOnScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. 레이아웃 먼저!
        setContentView(R.layout.activity_alert)

        // 2. 그 다음 버튼 찾기
        val stopButton = findViewById<Button>(R.id.btn_stop_alarm)
        stopButton.setOnClickListener {
            finish()
        }
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
