package com.example.lifesaivior.wakeup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // [추가] 비동기 작업을 위해 필요
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.lifesaivior.core.settings.AppSettingsRepository
import com.example.lifesaivior.ui.screen.settings.DemoSettingsScreen
import com.example.lifesaivior.ui.screen.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    // UI 상태 관리
    private val isVoiceOnState = mutableStateOf(false)
    private val isShockOnState = mutableStateOf(false)
    private val isDemoOnState = mutableStateOf(false)
    private val isSosSuspendedState = mutableStateOf(false)
    private val showDemoDetailsState = mutableStateOf(false)
    private val demoBeepLevelState = mutableStateOf(100)
    private val demoHighToneLevelState = mutableStateOf(100)
    private val demoVibrateLevelState = mutableStateOf(100)
    private val demoEasLevelState = mutableStateOf(100)

    // 연속 클릭 방지 (시간 단축: 0.5초 -> 0.3초)
    private var lastVoiceToggleTime = 0L
    private var lastShockToggleTime = 0L
    private val CLICK_DELAY_MS = 300L

    // 지연 실행 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private val startServiceRunnable = Runnable { startServicesIfEnabled() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        Toast.makeText(this, "설정 완료. 앱을 닫으면 감시가 시작됩니다.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettingsRepository.init(this)
        loadSettings()
        lifecycleScope.launch {
            AppSettingsRepository.state.collect { settings ->
                isVoiceOnState.value = settings.isVoiceDetectionEnabled
                isShockOnState.value = settings.isShockDetectionEnabled
                isDemoOnState.value = settings.isDemoModeEnabled
                isSosSuspendedState.value = settings.isSosBackgroundSuspended
                demoBeepLevelState.value = settings.demoBeepLevel
                demoHighToneLevelState.value = settings.demoHighToneLevel
                demoVibrateLevelState.value = settings.demoVibrateLevel
                demoEasLevelState.value = settings.demoEasLevel
            }
        }

        setContent {
            if (showDemoDetailsState.value) {
                DemoSettingsScreen(
                    isDemoOn = isDemoOnState.value,
                    beepLevel = demoBeepLevelState.value,
                    highToneLevel = demoHighToneLevelState.value,
                    vibrateLevel = demoVibrateLevelState.value,
                    easLevel = demoEasLevelState.value,
                    onBeepLevelChange = { level ->
                        demoBeepLevelState.value = level
                        AppSettingsRepository.setDemoBeepLevel(this, level)
                    },
                    onHighToneLevelChange = { level ->
                        demoHighToneLevelState.value = level
                        AppSettingsRepository.setDemoHighToneLevel(this, level)
                    },
                    onVibrateLevelChange = { level ->
                        demoVibrateLevelState.value = level
                        AppSettingsRepository.setDemoVibrateLevel(this, level)
                    },
                    onEasLevelChange = { level ->
                        demoEasLevelState.value = level
                        AppSettingsRepository.setDemoEasLevel(this, level)
                    },
                    onBack = { showDemoDetailsState.value = false }
                )
            } else {
                SettingsScreen(
                    isVoiceOn = isVoiceOnState.value,
                    isShockOn = isShockOnState.value,
                    isDemoOn = isDemoOnState.value,
                    isSosActive = isSosSuspendedState.value,
                    isDemoToggleEnabled = false,
                    onVoiceToggle = { newValue ->
                        // 1. 연타 방지 (300ms)
                        val currentTime = SystemClock.elapsedRealtime()
                        if (currentTime - lastVoiceToggleTime < CLICK_DELAY_MS) {
                            return@SettingsScreen
                        }
                        lastVoiceToggleTime = currentTime

                        // 2. UI 즉시 반영 (여기가 중요! 렉 없이 스위치부터 움직임)
                        isVoiceOnState.value = newValue
                        AppSettingsRepository.setVoiceDetection(this, newValue)

                        if (newValue) {
                            clearSosSuspensionIfNeeded()
                            // 켜기: 준비 과정 수행
                            stopAllServicesAsync() // 기존 서비스 정리
                            checkAndRequestPermissions()
                            Toast.makeText(this, "준비됨. 화면을 끄면 시작됩니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 끄기: 비동기로 서비스 종료 (UI 버벅임 방지)
                            stopAllServicesAsync()
                            Toast.makeText(this, "감시가 해제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShockToggle = { newValue ->
                        val currentTime = SystemClock.elapsedRealtime()
                        if (currentTime - lastShockToggleTime < CLICK_DELAY_MS) {
                            return@SettingsScreen
                        }
                        lastShockToggleTime = currentTime

                        isShockOnState.value = newValue
                        AppSettingsRepository.setShockDetection(this, newValue)

                        if (newValue) {
                            clearSosSuspensionIfNeeded()
                            stopAllServicesAsync()
                            Toast.makeText(this, "준비됨. 화면을 끄면 시작됩니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            stopAllServicesAsync()
                            Toast.makeText(this, "감시가 해제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDemoToggle = { enabled ->
                        if (enabled != isDemoOnState.value) {
                            Toast.makeText(this, "시연 모드는 스탠바이 설정에서 변경하세요.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDemoDetails = { showDemoDetailsState.value = true },
                    onBack = { finish() },
                    onEditProfile = {
                        Toast.makeText(this, "프로필 수정 준비 중", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        checkOverlayPermission()
        checkAndRequestPermissions()
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(startServiceRunnable)
        stopAllServicesAsync()
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        stopAllServicesAsync()
    }

    override fun onPause() {
        super.onPause()
        // onPause는 비워둠 (권한 팝업 오작동 방지)
    }

    override fun onStop() {
        super.onStop()
        // 앱이 완전히 내려갔을 때 1초 뒤 감시 시작
        handler.postDelayed(startServiceRunnable, 1000)
    }

    private fun loadSettings() {
        val settings = AppSettingsRepository.snapshot(this)
        isVoiceOnState.value = settings.isVoiceDetectionEnabled
        isShockOnState.value = settings.isShockDetectionEnabled
        isDemoOnState.value = settings.isDemoModeEnabled
        isSosSuspendedState.value = settings.isSosBackgroundSuspended
        demoBeepLevelState.value = settings.demoBeepLevel
        demoHighToneLevelState.value = settings.demoHighToneLevel
        demoVibrateLevelState.value = settings.demoVibrateLevel
        demoEasLevelState.value = settings.demoEasLevel
    }

    private fun startServicesIfEnabled() {
        val settings = AppSettingsRepository.snapshot(this)
        if (settings.isSosBackgroundSuspended) return
        val isDemoOn = settings.isDemoModeEnabled
        val isVoiceOn = settings.isVoiceDetectionEnabled || isDemoOn
        val isShockOn = settings.isShockDetectionEnabled || isDemoOn

        if (isVoiceOn) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceSafe(VoiceService::class.java)
            }
        }

        if (isShockOn) {
            startServiceSafe(SensorService::class.java)
        }
    }

    // [핵심 수정] 서비스를 끄는 작업을 백그라운드 스레드로 보냄
    private fun stopAllServicesAsync() {
        // Main 스레드가 AI Lock에 걸리지 않도록 IO 스레드에서 종료 명령 수행
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 로그도 여기서 찍음
                Log.d("MainActivity", "🛑 [Async] 감시 서비스 중단 요청")
                stopService(Intent(applicationContext, VoiceService::class.java))
                stopService(Intent(applicationContext, SensorService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startServiceSafe(serviceClass: Class<*>) {
        val intent = Intent(applicationContext, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun clearSosSuspensionIfNeeded() {
        val settings = AppSettingsRepository.snapshot(this)
        if (!settings.isSosBackgroundSuspended) return
        AppSettingsRepository.setSosBackgroundSuspended(this, false)
        AppSettingsRepository.clearSosBackup(this)
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
