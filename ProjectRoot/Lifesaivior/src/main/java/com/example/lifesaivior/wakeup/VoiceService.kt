package com.example.lifesaivior.wakeup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lifesaivior.R
import com.example.lifesaivior.ai.stt.EmergencyIntentClassifierKorean
import com.example.lifesaivior.ai.stt.VoiceTriggerDetector
import com.example.lifesaivior.core.settings.AppSettingsRepository
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class VoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "VOICE_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 102
    }

    private lateinit var intentClassifier: EmergencyIntentClassifierKorean
    private lateinit var voiceDetector: VoiceTriggerDetector

    private var isolationDetector: IsolationDetector? = null
    private var isMicActive = false
    private var isDemoMode = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundNotification(isIsolated = false)

        AppSettingsRepository.init(this)
        val settings = AppSettingsRepository.snapshot(this)
        if (settings.isSosBackgroundSuspended) {
            Log.w("VoiceService", "🛑 SOS 활성화 상태: 음성 감지 중단")
            stopSelf()
            return
        }

        intentClassifier = EmergencyIntentClassifierKorean(this)
        voiceDetector = VoiceTriggerDetector(
            context = this,
            onStateChange = { Log.d("VoiceService", "상태: $it") },
            onDetected = { text -> analyzeVoiceIntent(text) },
            onErrorOccurred = { restartVoiceListening() }
        )

        isDemoMode = settings.isDemoModeEnabled
        if (isDemoMode) {
            Log.w("VoiceService", "🎬 시연 모드: 통신 상태와 무관하게 음성 감지 시작")
            startListening()
            updateNotification(isIsolated = true)
        } else {
            isolationDetector = IsolationDetector(
                context = this,
                onIsolated = {
                    Log.w("VoiceService", "📶 통신 고립! 음성 감지 시작")
                    startListening()
                    updateNotification(isIsolated = true)
                },
                onRecovered = {
                    Log.d("VoiceService", "📶 통신 복구. 음성 대기 모드")
                    stopListening()
                    updateNotification(isIsolated = false)
                }
            )

            isolationDetector?.startMonitoring()
        }
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
        if (!isMicActive) return

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
        // [핵심 1] SOS 동안 백그라운드 감지 중단 플래그 설정
        AppSettingsRepository.init(this)
        val settings = AppSettingsRepository.snapshot(this)
        if (!settings.isSosBackgroundSuspended) {
            AppSettingsRepository.setSosBackgroundSuspended(
                context = this,
                suspended = true,
                backupVoice = settings.isVoiceDetectionEnabled,
                backupShock = settings.isShockDetectionEnabled,
                backupDemo = settings.isDemoModeEnabled
            )
        }
        if (settings.isDemoModeEnabled) {
            // 시연 모드에서는 1회 발동 후 자동 OFF 처리
            AppSettingsRepository.setDemoMode(this, false)
            AppSettingsRepository.setVoiceDetection(this, false)
            AppSettingsRepository.setShockDetection(this, false)
            AppSettingsRepository.setSosBackgroundSuspended(this, false)
            AppSettingsRepository.clearSosBackup(this)
        } else {
            val voiceEnabled = settings.isVoiceDetectionEnabled
            val shockEnabled = settings.isShockDetectionEnabled
            if (voiceEnabled && !shockEnabled) {
                AppSettingsRepository.setVoiceDetection(this, false)
            }
        }
        playEasAlertAsync()

        // 1. 실행할 Activity Intent 생성
        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = SensorService.ACTION_SENSOR_TRIGGERED
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("triggerReason", reason)
        }

        if (activityIntent == null) return

        // 2. 화면이 켜져 있을 때 강제로 앱 실행
        if (android.provider.Settings.canDrawOverlays(this)) {
            startActivity(activityIntent)
            Log.d("VoiceService", "🚀 비상 상황! 앱 강제 실행됨")
        }

        // 3. 잠금 화면 깨우기 및 알림
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 비상 상황 감지!")
            .setContentText("구조 모드로 전환합니다.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, notification)

        // 4. 정리 작업
        stopListening()

        // [핵심 2] SensorService(충격 감지)도 강제 종료
        stopService(Intent(this, SensorService::class.java))

        stopSelf() // 나 자신도 종료
    }

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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
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

        // [핵심 수정] UI 스레드가 기다리지 않도록, 뒷정리는 별도 스레드에서 수행 (비동기)
        if (::intentClassifier.isInitialized) {
            val classifierToClose = intentClassifier
            Thread {
                classifierToClose.close()
            }.start()
        }

        isolationDetector?.stopMonitoring()

        Log.d("VoiceService", "🔴 음성 서비스 종료 (AI 해제는 백그라운드에서 진행)")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        BackgroundMonitorReceiver.schedule(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun playEasAlertAsync() {
        Thread {
            try {
                AppSettingsRepository.init(this)
                val settings = AppSettingsRepository.snapshot(this)
                val level = if (settings.isDemoModeEnabled) settings.demoEasLevel else 100
                if (level <= 0) return@Thread

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val stream = AudioManager.STREAM_ALARM
                val originalVolume = audioManager.getStreamVolume(stream)
                val maxVolume = audioManager.getStreamMaxVolume(stream)
                val targetVolume = ((level / 100.0) * maxVolume).roundToInt().coerceIn(0, maxVolume)
                audioManager.setStreamVolume(stream, targetVolume, 0)

                val freq1 = 853.0
                val freq2 = 960.0
                val durationSeconds = 5
                val sampleRate = 44_100
                val gain = (level / 100.0).coerceIn(0.0, 1.0)

                val numSamples = durationSeconds * sampleRate
                val generatedSnd = ByteArray(2 * numSamples)

                try {
                    for (i in 0 until numSamples) {
                        val time = i.toDouble() / sampleRate
                        val wave1 = sin(2.0 * PI * freq1 * time)
                        val wave2 = sin(2.0 * PI * freq2 * time)
                        val mixed = (wave1 + wave2) * 0.5 * gain
                        val maxVal = Short.MAX_VALUE.toInt()
                        val value = (mixed * maxVal).toInt()
                        generatedSnd[2 * i] = (value and 0x00FF).toByte()
                        generatedSnd[2 * i + 1] = ((value and 0xFF00) shr 8).toByte()
                    }

                    val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(generatedSnd.size)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                    } else {
                        @Suppress("DEPRECATION")
                        AudioTrack(
                            AudioManager.STREAM_ALARM,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            generatedSnd.size,
                            AudioTrack.MODE_STATIC
                        )
                    }

                    try {
                        audioTrack.write(generatedSnd, 0, generatedSnd.size)
                        audioTrack.play()
                        Thread.sleep(durationSeconds * 1000L + 50L)
                    } finally {
                        runCatching { audioTrack.stop() }
                        runCatching { audioTrack.release() }
                    }
                } finally {
                    audioManager.setStreamVolume(stream, originalVolume, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
