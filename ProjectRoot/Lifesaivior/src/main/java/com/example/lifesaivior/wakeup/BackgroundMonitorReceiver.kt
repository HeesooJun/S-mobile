package com.example.lifesaivior.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.lifesaivior.core.settings.AppSettingsRepository

class BackgroundMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        startIfNeeded(context)
    }

    companion object {
        private const val ACTION_RECOVER = "com.example.lifesaivior.action.RECOVER_BACKGROUND"
        private const val REQUEST_CODE = 4101

        fun startIfNeeded(context: Context) {
            AppSettingsRepository.init(context)
            val settings = AppSettingsRepository.snapshot(context)
            if (settings.isSosBackgroundSuspended) return

            val isDemoOn = settings.isDemoModeEnabled
            val shouldVoiceOn = settings.isVoiceDetectionEnabled || isDemoOn
            val shouldShockOn = settings.isShockDetectionEnabled || isDemoOn

            if (shouldVoiceOn && hasRecordAudioPermission(context)) {
                startServiceSafe(context, VoiceService::class.java)
            }
            if (shouldShockOn) {
                startServiceSafe(context, SensorService::class.java)
            }
        }

        fun schedule(context: Context, delayMs: Long = 1_000L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackgroundMonitorReceiver::class.java).apply {
                action = ACTION_RECOVER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)

            val triggerAt = SystemClock.elapsedRealtime() + delayMs
            val canExact =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }

        private fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun startServiceSafe(context: Context, serviceClass: Class<*>) {
            val intent = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
