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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lifesaiver.R
import com.example.lifesaiver.ai.stt.EmergencyIntentClassifierKorean
import com.example.lifesaiver.ai.stt.VoiceTriggerDetector
import kotlin.math.sqrt

class SensorService : Service(), SensorEventListener {

    companion object {
        const val ACTION_SENSOR_TRIGGERED = "com.example.wakeup.ACTION_SENSOR_TRIGGERED"
    }

    // 알림 채널 ID
    private val CHANNEL_ID_HIDDEN = "WAKEUP_HIDDEN_CHANNEL_V3"
    private val CHANNEL_ID_ALERT = "WAKEUP_ALERT_CHANNEL"

    // 센서 관련 변수
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // 센서 임계값
    private val IMPACT_THRESHOLD = 40.0f
    private val MOTION_THRESHOLD = 2.0f
    private val WAIT_TIME_MS = 5000L
    private val STABILIZATION_TIME_MS = 2000L

    private var isWaitingForStillness = false
    private var impactTime: Long = 0
    private var isAlertTriggered = false // 중복 실행 방지 플래그

    // AI 관련 변수
    private lateinit var intentClassifier: EmergencyIntentClassifierKorean
    private lateinit var voiceDetector: VoiceTriggerDetector

    override fun onCreate() {
        super.onCreate()
        // 1. 알림 채널 생성 (오류 해결: 함수 호출)
        createNotificationChannels()

        // 2. 포그라운드 알림 표시 (오류 해결: 함수 호출)
        startForegroundServiceNotification()

        // 3. 센서 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 4. AI(음성+의도분석) 초기화 및 즉시 실행
        initAndStartAI()
    }

    private fun initAndStartAI() {
        intentClassifier = EmergencyIntentClassifierKorean(this)

        voiceDetector = VoiceTriggerDetector(
            context = this,
            onStateChange = { Log.d("SaiviorVoice", "상태: $it") },
            onDetected = { text ->
                // 말이 들리면 즉시 분석
                analyzeVoiceIntent(text)
            },
            onErrorOccurred = { error ->
                Log.e("SaiviorVoice", "오류: $error")
                // 에러가 나거나 말이 끊겨도, 죽지 않고 다시 듣게 만듦 (무한 루프)
                restartVoiceListening()
            }
        )

        // ★ 앱 켜지자마자 마이크 켜기 (상시 대기)
        Log.d("Saivior", "음성 웨이크업 감시 시작")
        voiceDetector.startListening()
    }

    private fun restartVoiceListening() {
        if (isAlertTriggered) return // 이미 비상상황이면 재시작 안 함

        // 잠시 텀을 두고 다시 켬 (CPU 과부하 방지)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                voiceDetector.startListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1000) // 1초 뒤 재시작
    }

    private fun analyzeVoiceIntent(text: String) {
        if (isAlertTriggered) return

        intentClassifier.checkIntent(text) { isEmergency, score, match ->
            if (isEmergency) {
                Log.w("Saivior", "🗣️ 음성 감지됨: $match (점수: $score)")
                triggerAlert("음성 감지($match)")
            } else {
                Log.d("Saivior", "일상 대화: $match")
                // 비상 상황 아니면 다시 듣기 모드
                restartVoiceListening()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isAlertTriggered) return

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
                        Log.w("Saivior", "📉 낙상 감지됨!")
                        triggerAlert("낙상 감지")
                        isWaitingForStillness = false
                    }
                }
            }
        }
    }

    private fun triggerAlert(reason: String) {
        if (isAlertTriggered) return
        isAlertTriggered = true

        Log.e("Saivior", "🚨 비상 알림 발동! 원인: $reason")

        voiceDetector.stopListening()
        sensorManager.unregisterListener(this)

        val intent = Intent(this, AlertActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("triggerReason", reason)

        if (Settings.canDrawOverlays(this)) {
            startActivity(intent)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("재난 감지! ($reason)")
            .setContentText("터치하여 구조 요청 보내기")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(9999, notificationBuilder.build())
    }

    // [오류 해결] 알림 객체를 생성하고 포그라운드를 시작하는 전체 함수
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
            val serviceType = if (Build.VERSION.SDK_INT >= 34) {
                // Android 14 이상: 마이크 권한 필수
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(1, notification, serviceType)
        } else {
            startForeground(1, notification)
        }
    }

    // [오류 해결] 채널 생성 전체 함수
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
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

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

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        voiceDetector.stopListening()
        intentClassifier.close()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
}
