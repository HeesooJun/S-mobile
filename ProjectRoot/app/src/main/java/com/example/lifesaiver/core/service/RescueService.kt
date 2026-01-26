package com.example.lifesaiver.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.lifesaiver.R

class RescueService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "START_RESCUE") {
            startForegroundService()
        } else if (action == "STOP_RESCUE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY // 앱이 강제 종료되어도 시스템이 다시 살려냄
    }

    private fun startForegroundService() {
        val channelId = "rescue_channel"
        val channelName = "구조 신호 알림"

        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🚨 구조 신호 송출 중")
            .setContentText("백그라운드에서 구조 신호가 작동 중입니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 아이콘이 없으면 기본 아이콘 사용
            .setOngoing(true) // 사용자가 알림을 지우지 못하게 함
            .build()

        // 이 호출이 있어야 앱이 백그라운드에서 죽지 않음
        startForeground(1, notification)
    }
}
