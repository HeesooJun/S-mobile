package com.example.lifesaiver.wakeup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lifesaiver.R
import com.example.lifesaiver.ai.stt.EmergencyIntentClassifierKorean
import com.example.lifesaiver.ai.stt.VoiceTriggerDetector

class VoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "VOICE_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 102 // SensorService(101)와 달라야 함
    }

    private lateinit var intentClassifier: EmergencyIntentClassifierKorean
    private lateinit var voiceDetector: VoiceTriggerDetector

    // [수정] lateinit 대신 Nullable(?) 사용 -> 오류 원천 차단
    private var isolationDetector: IsolationDetector? = null
    private var isMicActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 1. 초기엔 '대기 모드' 알림 표시
        startForegroundNotification(isIsolated = false)

        intentClassifier = EmergencyIntentClassifierKorean(this)
        voiceDetector = VoiceTriggerDetector(
            context = this,
            onStateChange = { Log.d("VoiceService", "상태: $it") },
            onDetected = { text -> analyzeVoiceIntent(text) },
            onErrorOccurred = { restartVoiceListening() }
        )

        // 2. IsolationDetector 초기화 (변수에 할당)
        isolationDetector = IsolationDetector(
            context = this,
            onIsolated = {
                // [고립 감지됨!] -> 마이크 ON
                Log.w("VoiceService", "📶 통신 고립! 음성 감지 시작")
                startListening()
                updateNotification(isIsolated = true)
            },
            onRecovered = {
                // [통신 복구됨] -> 마이크 OFF (배터리 절약)
                Log.d("VoiceService", "📶 통신 복구. 음성 대기 모드")
                stopListening()
                updateNotification(isIsolated = false)
            }
        )

        // 감지 시작 (null 안전 호출 ?. 사용)
        isolationDetector?.startMonitoring()
    }

    private fun startListening() {
        if (isMicActive) return
        try {
            voiceDetector.startListening()
            isMicActive = true
            Log.d("VoiceService", "🟢 마이크 활성화됨")
        } catch (e: Exception) {
            Log.e("VoiceService", "마이크 시작 실패: ${e.message}")
            stopSelf()
        }
    }

    private fun stopListening() {
        if (!isMicActive) return
        try {
            voiceDetector.stopListening()
            isMicActive = false
            Log.d("VoiceService", "🔴 마이크 비활성화됨")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun analyzeVoiceIntent(text: String) {
        if (!isMicActive) return // 꺼진 상태면 무시

        intentClassifier.checkIntent(text) { isEmergency, score, match ->
            if (isEmergency) {
                Log.e("VoiceService", "🗣️ 비상 음성 감지: $match")
                triggerAlert("음성 감지($match)")
            } else {
                restartVoiceListening()
            }
        }
    }

    private fun restartVoiceListening() {
        // 마이크가 켜져 있어야 하는 상황일 때만 재시작
        if (!isMicActive) return

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                if (isMicActive) voiceDetector.startListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 1000)
    }

    private fun triggerAlert(reason: String) {
        // 1. 실행할 Activity Intent 생성
        // (getLaunchIntentForPackage 대신 명시적 Intent 사용 권장)
        // MainActivity의 클래스명을 정확히 안다면 아래 방식이 더 확실합니다.
        // val activityIntent = Intent(this, MainActivity::class.java).apply { ... }

        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = SensorService.ACTION_SENSOR_TRIGGERED // NavHost에서 감지할 Action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("triggerReason", reason)
        }

        // activityIntent가 null이면 실행 불가하므로 리턴
        if (activityIntent == null) return

        // 2. [핵심] 화면이 켜져 있을 때 강제로 앱 실행 (권한 필요)
        // 아까 설정 화면에서 '다른 앱 위에 그리기' 권한을 허용했다면 여기서 작동합니다.
        if (android.provider.Settings.canDrawOverlays(this)) {
            startActivity(activityIntent)
            Log.d("VoiceService", "🚀 비상 상황! 앱 강제 실행됨")
        }

        // 3. 잠금 화면일 때를 위한 FullScreenIntent 설정
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 비상 상황 감지!")
            .setContentText("구조 모드로 전환합니다.")
            .setPriority(NotificationCompat.PRIORITY_MAX) // 중요도 MAX로 설정
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 소리, 진동 등 기본 알림 효과
            .setFullScreenIntent(pendingIntent, true) // 잠금 화면 깨우기
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, notification)

        // 4. 비상 상황 발생 시 마이크 끄기 (오디오 점유 해제)
        stopListening()
    }

    // 상태에 따라 알림 갱신
    private fun updateNotification(isIsolated: Boolean) {
        startForegroundNotification(isIsolated)
    }

    private fun startForegroundNotification(isIsolated: Boolean) {
        val title = if (isIsolated) "⚠️ 비상 음성 감시" else "Saivior 음성 대기"
        val text = if (isIsolated) "통신 두절! 구조 요청을 듣고 있습니다." else "통신 상태를 확인하며 대기 중..."

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(if (isIsolated) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE // 마이크 권한 필수
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "음성 감지 서비스", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        intentClassifier.close()

        // [수정] Nullable 처리로 안전하게 해제
        isolationDetector?.stopMonitoring()

        Log.d("VoiceService", "🔴 음성 서비스 종료")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
