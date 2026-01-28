package com.example.lifesaiver.wakeup

import android.app.*
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
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class SensorService : Service(), SensorEventListener {

    companion object {
        const val ACTION_SENSOR_TRIGGERED = "com.example.wakeup.ACTION_SENSOR_TRIGGERED"
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val CHANNEL_ID_SERVICE = "WAKEUP_SERVICE_CHANNEL"
    private val CHANNEL_ID_ALERT = "WAKEUP_ALERT_CHANNEL"

    private val IMPACT_THRESHOLD = 40.0f // 충격 임계값

    private val MOTION_THRESHOLD = 2.0f // 움직임 임계값 (충격 이후 움직임 여부)

    private val WAIT_TIME_MS = 5000L // 충격 이후 10초 동안 움직임 없을 시 알람

    private val STABILIZATION_TIME_MS = 2000L // 충격 직후 2초 휴대폰 튀는 시간 고려
    private var lastAlertTime: Long = 0 // 마지막 알람 시각 (중복 실행 방지용)

    private var isWaitingForStillness = false
    private var impactTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        // 알림 채널 생성
        createNotificationChannels()
        // 센서 매니저 및 가속도 센서 획득
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // 포그라운드 서비스 시작 알림 표시
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        return START_STICKY
    }

    // 충격 임계치 설정
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val gForce = sqrt(x * x + y * y + z * z)

                val currentTime = System.currentTimeMillis()

                if (!isWaitingForStillness) {
                    // [1단계] 큰 충격이 발생했는지 감시
                    if (gForce > IMPACT_THRESHOLD) {
                        isWaitingForStillness = true
                        impactTime = currentTime
                        // 로그: "충격 감지! 움직임 감시 시작"
                    }
                } else {
                    // [2단계] 충격 이후 움직임이 있는지 감시
                    val timePassed = currentTime - impactTime

                    // A. 충격 직후 2초간은 폰이 튕길 수 있으므로 무시 (Stabilization)
                    if (timePassed < STABILIZATION_TIME_MS) return

                    // B. 2초가 지났는데, 사용자가 움직이는가?
                    // 현재 gForce와 9.8(중력)의 차이가 크다면 움직이는 것임
                    if (Math.abs(gForce - 9.8f) > MOTION_THRESHOLD) {
                        // 사용자가 움직임 -> 알람 취소 및 초기화
                        isWaitingForStillness = false
                        // 로그: "움직임 감지됨. 알람 취소"
                        return
                    }

                    if (timePassed > WAIT_TIME_MS) {
                        triggerAlert() // 알람 발동!
                        isWaitingForStillness = false // 초기화
                    }
                }

            }
        }
    }

    private fun triggerAlert() {
        // 앱이 포그라운드일 때만 Standby 화면 이동 처리
        sendBroadcast(Intent(ACTION_SENSOR_TRIGGERED).apply { setPackage(packageName) })

        val intent = Intent(this, AlertActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // 권한이 있으면 화면을 바로 띄워버림 (홈화면 상태 해결)
        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        }

        // 잠김 화면일 때를 대비해 기존 알림 로직도 유지
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("재난 감지!")
            .setContentText("터치하여 전체 화면 보기")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(9999, notificationBuilder.build())
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Wakeup 감시 중")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE, "감시 상태", NotificationManager.IMPORTANCE_MIN
            )
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT, "재난 경보", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
