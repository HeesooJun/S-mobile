package com.example.lifesaivior

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.lifesaivior.core.service.RescueService
import com.example.lifesaivior.presentation.AppViewModel
import com.example.lifesaivior.presentation.UiEvent
import com.example.lifesaivior.ui.theme.LifesaiviorTheme
import com.example.lifesaivior.wakeup.SensorService
import com.example.lifesaivior.wakeup.VoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // 앱 준비 상태 (true가 되면 LifesaiviorApp을 보여줌)
    private var isReady by mutableStateOf(false)

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    // [1단계 결과] 권한 요청 -> 오버레이 체크로 이동
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 요청 직후 실제 권한 상태를 ViewModel에 동기화
        viewModel.refreshPermissions()
        // 거부했더라도 일단 다음 단계(오버레이)로 진행 (필수 안내는 나중에)
        checkOverlayPermission()
    }

    // [2단계 결과] 오버레이 설정 -> 블루투스 체크로 이동
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndEnableBluetooth()
    }

    // [3단계 결과] 블루투스 켜기 -> 준비 완료(User Info)로 이동
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 블루투스 결과와 상관없이 설정 종료 처리
        finishSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 윈도우 설정 (전체화면 등)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 크래시 핸들러
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val errorMsg = "오류: ${throwable.message}"
            Log.e("CRASH_HANDLER", errorMsg, throwable)
            runOnUiThread {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        // [핵심] 앱 켜자마자 권한 체크 시작 (PermissionViewModel UI 없음)
        checkAllPermissions()
        handleAutoSosIntent(intent)

        setContent {
            LifesaiviorTheme(darkTheme = true, dynamicColor = false) {
                // 권한/블루투스 체크가 다 끝나면 isReady = true가 되고
                // LifesaiviorApp이 실행됨. (닉네임 설정 안 되어있으면 거기서 입력창이 뜸)
                if (isReady) {
                    val uiState by viewModel.uiState.collectAsState()

                    LifesaiviorApp(
                        hasPermissions = uiState.hasPermissions,
                        batteryLevel = uiState.batteryLevel,
                        isConnected = uiState.isConnected,
                        connectedCount = uiState.connectedCount,
                        meshPeerCount = uiState.meshPeerCount,
                        directPeerIds = uiState.directPeerIds,
                        myPeerId = uiState.myPeerId,
                        myNickname = uiState.myNickname,
                        peerNicknames = uiState.peerNicknames,
                        meshGraphSnapshot = uiState.meshGraphSnapshot,
                        meshVisualEvents = viewModel.meshVisualEvents,
                        bleDebugStats = uiState.bleDebug,
                        appViewModel = viewModel,
                        isMicOn = uiState.isMicOn,
                        isDisconnecting = uiState.isDisconnecting,
                        isRescueSignalActive = uiState.isRescueSignalActive,
                        messages = uiState.messages,
                        signatureLogs = uiState.signatureLogs,
                        profileLogs = uiState.profileLogs,
                        // 권한 재요청이 필요할 때 호출될 함수
                        onRequestPermissions = { checkAllPermissions() },
                        onStartAutoConnect = { viewModel.onStartAutoConnect() },
                        onStopAutoConnect = { viewModel.onStopAutoConnect() },
                        onMicPress = { viewModel.onMicPress() },
                        onMicRelease = { viewModel.onMicRelease() },
                        onSendMessage = { text -> viewModel.onSendMessage(text) },
                        onSendProfileTest = { viewModel.sendProfileTestPacket() },
                        onSendProfileUpdate = { profile -> viewModel.sendProfileUpdate(profile) },
                        onDisconnect = { viewModel.onDisconnect() },
                        onStartRescueSignal = { viewModel.startRescueSignal() },
                        onStopRescueSignal = { viewModel.stopRescueSignal() },
                        onPulseRescueSignal = { viewModel.pulseRescueSignal() },
                        onClearSignatureLogs = { viewModel.clearSignatureLogs() },
                        onClearProfileLogs = { viewModel.clearProfileLogs() },
                        onClearDeviceMonitoring = { viewModel.clearDeviceMonitoring() },
                        isVoiceDetectionEnabled = uiState.isVoiceDetectionEnabled,
                        isShockDetectionEnabled = uiState.isShockDetectionEnabled,
                        isDemoModeEnabled = uiState.isDemoModeEnabled,
                        onSetVoiceDetection = { enabled -> viewModel.setVoiceDetection(enabled) },
                        onSetShockDetection = { enabled -> viewModel.setShockDetection(enabled) },
                        onSetDemoMode = { enabled -> viewModel.setDemoMode(enabled) }
                    )
                } else {
                    // [로딩 화면] 체크하는 동안 검은 화면에 로딩바 (이 위로 팝업들이 뜸)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                if (event is UiEvent.Toast) {
                    Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoSosIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 이미 준비 완료된 상태면 서비스 상태만 리프레시
        if (isReady) {
            refreshServices()
        }
    }

    // --- [자동 권한 체크 로직] ---

    // 1단계: 필수 권한 체크
    private fun checkAllPermissions() {
        isReady = false // 체크 중엔 로딩 상태

        val permissions = viewModel.requiredPermissions.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.READ_PHONE_STATE)

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (notGranted.isNotEmpty()) {
            // 권한 없으면 바로 시스템 팝업 띄움
            requestPermissionLauncher.launch(notGranted)
        } else {
            // 권한 있으면 다음 단계(오버레이)로
            viewModel.onPermissionsResult(true)
            checkOverlayPermission()
        }
    }

    private fun handleAutoSosIntent(intent: Intent?) {
        if (intent?.action != SensorService.ACTION_SENSOR_TRIGGERED) return
        val reason = intent.getStringExtra("triggerReason")
        viewModel.onAutoSosTriggered(reason)
    }

    // 2단계: 오버레이 권한 체크
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "비상 알림을 위해 '다른 앱 위에 표시' 설정이 필요합니다.", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                checkAndEnableBluetooth()
            }
        } else {
            checkAndEnableBluetooth()
        }
    }

    // 3단계: 블루투스 활성화 체크
    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            finishSetup()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                // 블루투스 켜기 팝업 띄움
                enableBluetoothLauncher.launch(enableBtIntent)
            } catch (e: SecurityException) {
                Log.e("Bluetooth", "Permission error", e)
                finishSetup()
            }
        } else {
            finishSetup()
        }
    }

    // 4단계: 최종 완료 (서비스 시작 & LifesaiviorApp 표시)
    private fun finishSetup() {
        // 권한 다이얼로그/설정 화면 복귀 후 상태를 다시 반영
        viewModel.refreshPermissions()
        refreshServices()
        // [중요] 여기서 true가 되면 LifesaiviorApp이 렌더링됨
        // LifesaiviorApp 내부에서 닉네임 유무에 따라 '정보 입력' vs '대기 화면' 분기 처리
        isReady = true
    }

    private fun refreshServices() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDemoOn = prefs.getBoolean("demo_mode", false)
        val isVoiceOn = prefs.getBoolean("voice_detection", false) || isDemoOn
        val isShockOn = prefs.getBoolean("shock_detection", false) || isDemoOn

        if (isVoiceOn) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceSafe(VoiceService::class.java)
            }
        } else {
            stopService(Intent(this, VoiceService::class.java))
        }

        if (isShockOn) {
            startServiceSafe(SensorService::class.java)
        } else {
            stopService(Intent(this, SensorService::class.java))
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

    override fun onDestroy() {
        if (isFinishing) {
            val intent = Intent(this, RescueService::class.java).apply {
                action = RescueService.ACTION_SHUTDOWN
            }
            startService(intent)
        }
        super.onDestroy()
    }
}
