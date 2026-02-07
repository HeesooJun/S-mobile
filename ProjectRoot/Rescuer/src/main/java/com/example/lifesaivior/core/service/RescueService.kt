package com.example.lifesaivior.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.lifesaivior.R
import com.example.lifesaivior.presentation.AppShutdownHooks

class RescueService : Service() {

    companion object {
        const val ACTION_START_RESCUE = "com.example.lifesaivior.action.START_RESCUE"
        const val ACTION_STOP_RESCUE = "com.example.lifesaivior.action.STOP_RESCUE"
        const val ACTION_SHUTDOWN = "com.example.lifesaivior.action.SHUTDOWN_APP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_RESCUE -> {
                startForegroundService()
                return START_STICKY
            }
            ACTION_STOP_RESCUE -> {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHUTDOWN -> {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                AppShutdownHooks.requestShutdown()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY // 앱이 강제 종료되어도 시스템이 다시 살려냄
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppShutdownHooks.requestShutdown()
        stopSelf()
        super.onTaskRemoved(rootIntent)
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
