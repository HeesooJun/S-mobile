package com.example.lifesaiver.wakeup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lifesaiver.R
import kotlin.math.sqrt

class SensorService : Service(), SensorEventListener {

    companion object {
        const val ACTION_SENSOR_TRIGGERED = "com.example.wakeup.ACTION_SENSOR_TRIGGERED"
        const val NOTIFICATION_ID = 101

        private const val CHANNEL_ID_HIDDEN = "WAKEUP_HIDDEN_CHANNEL_V3"
        private const val CHANNEL_ID_ALERT = "WAKEUP_ALERT_CHANNEL"
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // [핵심 1] 서비스 종료 상태 플래그 (오작동 방지)
    @Volatile private var isDestroyed = false

    // 센서 임계값
    private val IMPACT_THRESHOLD = 40.0f
    private val MOTION_THRESHOLD = 2.0f
    private val WAIT_TIME_MS = 5000L
    private val STABILIZATION_TIME_MS = 2000L

    private var isWaitingForStillness = false
    private var impactTime: Long = 0
    private var isAlertTriggered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForegroundServiceNotification()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // [수정] 서비스가 죽은 상태라면 리스너 등록 안 함
        if (!isDestroyed) {
            accelerometer?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // [핵심 2] 종료되었거나 이미 발동된 경우 로직 차단
        if (isDestroyed || isAlertTriggered) return

        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val gForce = sqrt(x * x + y * y + z * z)
                val currentTime = System.currentTimeMillis()

                if (!isWaitingForStillness) {
                    if (gForce > IMPACT_THRESHOLD) {
                        isWaitingForStillness = true
                        impactTime = currentTime
                    }
                } else {
                    val timePassed = currentTime - impactTime
                    if (timePassed < STABILIZATION_TIME_MS) return

                    if (Math.abs(gForce - 9.8f) > MOTION_THRESHOLD) {
                        isWaitingForStillness = false
                        return
                    }

                    if (timePassed > WAIT_TIME_MS) {
                        triggerAlert("낙상 감지")
                        isWaitingForStillness = false
                    }
                }
            }
        }
    }

    private fun triggerAlert(reason: String) {
        if (isAlertTriggered || isDestroyed) return
        isAlertTriggered = true

        Log.e("SensorService", "🚨 비상 알림 발동! 원인: $reason")

        // [핵심 1] 두 설정 모두 OFF (충격 + 음성)
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("shock_detection", false)
            .putBoolean("voice_detection", false) // 음성 감지도 끔
            .apply()

        // 1. 센서 해제
        sensorManager.unregisterListener(this)

        // 2. 실행할 액티비티 인텐트 준비
        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = ACTION_SENSOR_TRIGGERED
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("triggerReason", reason)
        }

        if (activityIntent != null) {
            // 3. 화면 켜져있을 때 앱 강제 실행
            if (Settings.canDrawOverlays(this)) {
                startActivity(activityIntent)
                Log.d("SensorService", "🚀 비상 상황! 앱 강제 실행됨")
            }

            // 4. 잠금 화면 깨우기
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 5. 알림 표시
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("재난 감지! ($reason)")
                .setContentText("터치하여 구조 요청 보내기")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(9999, notificationBuilder.build())
        }

        // [핵심 2] VoiceService(음성 감지)도 강제 종료
        stopService(Intent(this, VoiceService::class.java))

        // 나 자신도 종료
        stopSelf()
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_HIDDEN)
            .setContentTitle("Saivior 감시 중")
            .setContentText("넘어짐 및 구조 요청 대기 중...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_HIDDEN,
                "백그라운드 감시",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                "재난 경보",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setBypassDnd(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true // [핵심] 깃발 내림

        // [핵심 4] UI 렉 방지: 센서 해제도 백그라운드 스레드에서 처리 (비동기)
        val sm = sensorManager
        val listener = this
        Thread {
            try {
                sm.unregisterListener(listener)
                Log.d("SensorService", "🛑 센서 리스너 안전 해제 완료")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        Log.d("SensorService", "🔴 센서 서비스 종료")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
}
