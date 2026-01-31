package com.example.lifesaiver

import android.Manifest
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.lifesaiver.core.service.RescueService
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.UiEvent
import com.example.lifesaiver.ui.theme.LifesaiverTheme
import com.example.lifesaiver.wakeup.SensorService
import com.example.lifesaiver.wakeup.VoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // [추가 1] 일반 권한 요청 콜백
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 권한 거부된 게 있어도 일단 오버레이 체크로 넘어가서 진행 (필수 기능 안내는 UI에서)
        checkOverlayPermission()
    }

    // [추가 2] 오버레이 권한 설정 화면 콜백
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // (선택) 키가드가 있을 때 해제 요청 (보안 설정 없을 경우 바로 풀림)
            // val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            // keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // 구버전 안드로이드 대응
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // 화면이 켜진 상태를 계속 유지하고 싶다면 추가 (배터리 소모 주의)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1. 전체 화면 설정 (기존 코드 유지)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 2. 크래시 핸들러 (기존 코드 유지)
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val errorMsg = "오류: ${throwable.message}"
            Log.e("CRASH_HANDLER", errorMsg, throwable)
            runOnUiThread {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        // [수정] 3. 권한 체크 및 서비스 시작 로직 통합
        // 기존의 viewModel.refreshPermissions() 대신 통합 함수 호출
        checkAllPermissions()

        setContent {
            LifesaiverTheme(darkTheme = true, dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()

                // [중요] 기존 UI 로직 유지
                // requestPermissions 요청 시 우리가 만든 통합 함수를 호출하도록 연결
                LifesaiverApp(
                    hasPermissions = uiState.hasPermissions, // ViewModel 상태 사용
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
                    isMicOn = uiState.isMicOn,
                    isDisconnecting = uiState.isDisconnecting,
                    isRescueSignalActive = uiState.isRescueSignalActive,
                    messages = uiState.messages,
                    signatureLogs = uiState.signatureLogs,
                    profileLogs = uiState.profileLogs,
                    onRequestPermissions = { checkAllPermissions() }, // 여기 수정됨
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
                    onClearDeviceMonitoring = { viewModel.clearDeviceMonitoring() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is UiEvent.Toast -> toast(event.message)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 앱으로 돌아왔을 때 서비스 상태 갱신
        refreshServices()
    }

    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // --- [통합 권한 로직 시작] ---

    // 1단계: 모든 필요 권한(블루투스 + 웨이크업) 통합 체크
    private fun checkAllPermissions() {
        // ViewModel에 정의된 권한(블루투스, 위치 등) 가져오기
        val permissions = viewModel.requiredPermissions.toMutableList()

        // [추가] 웨이크업 기능에 필요한 권한 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO) // 음성 감지용
        permissions.add(Manifest.permission.READ_PHONE_STATE) // 고립 감지용

        // 아직 허용되지 않은 권한만 필터링
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (notGranted.isNotEmpty()) {
            // 권한 요청 팝업 띄우기 -> 결과는 requestPermissionLauncher로
            requestPermissionLauncher.launch(notGranted)
        } else {
            // 이미 다 허용됨 -> 오버레이 체크로
            viewModel.onPermissionsResult(true) // ViewModel에 알림
            checkOverlayPermission()
        }
    }

    // 2단계: 다른 앱 위에 표시 권한 체크
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "비상 알림을 위해 '다른 앱 위에 표시' 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                refreshServices()
            }
        } else {
            refreshServices()
        }
    }

    // 3단계: 서비스 실행 (Voice/Sensor)
    private fun refreshServices() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isVoiceOn = prefs.getBoolean("voice_detection", false)
        val isShockOn = prefs.getBoolean("shock_detection", false)

        // 음성 감지 서비스
        if (isVoiceOn) {
            // 마이크 권한이 있을 때만 안전하게 실행
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceSafe(VoiceService::class.java)
            }
        } else {
            stopService(Intent(this, VoiceService::class.java))
        }

        // 충격 감지 서비스
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

    // --- [통합 권한 로직 끝] ---

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
