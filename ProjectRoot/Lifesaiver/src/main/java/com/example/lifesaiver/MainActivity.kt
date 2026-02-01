package com.example.lifesaiver

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
import com.example.lifesaiver.core.service.RescueService
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.UiEvent
import com.example.lifesaiver.ui.theme.LifesaiverTheme
import com.example.lifesaiver.wakeup.SensorService
import com.example.lifesaiver.wakeup.VoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // 앱 준비 상태 (로딩 화면 제어용)
    private var isReady by mutableStateOf(false)

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    // [1단계 결과] 권한 요청 -> 오버레이 체크로 이동
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkOverlayPermission()
    }

    // [2단계 결과] 오버레이 설정 -> 블루투스 체크로 이동
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndEnableBluetooth()
    }

    // [3단계 결과] 블루투스 켜기 -> 서비스 시작(완료)으로 이동
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "블루투스가 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "재난 통신을 위해 블루투스가 필요합니다.", Toast.LENGTH_LONG).show()
        }
        refreshServices()
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

        // [시작] 권한 체크 로직 실행
        checkAllPermissions()

        setContent {
            LifesaiverTheme(darkTheme = true, dynamicColor = false) {
                // 준비 완료(isReady = true)일 때만 메인 앱 화면 표시
                if (isReady) {
                    val uiState by viewModel.uiState.collectAsState()

                    LifesaiverApp(
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
                        isMicOn = uiState.isMicOn,
                        isDisconnecting = uiState.isDisconnecting,
                        isRescueSignalActive = uiState.isRescueSignalActive,
                        messages = uiState.messages,
                        signatureLogs = uiState.signatureLogs,
                        profileLogs = uiState.profileLogs,
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
                        onClearDeviceMonitoring = { viewModel.clearDeviceMonitoring() }
                    )
                } else {
                    // [로딩 화면] 준비 중일 때 검은 화면에 로딩바
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

    override fun onResume() {
        super.onResume()
        // 앱 복귀 시 서비스 상태 재확인 (이미 완료된 경우에만)
        if (isReady) {
            refreshServices()
        }
    }

    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    // --- [순차 권한 체크 로직] ---

    // 1단계: 필수 권한 체크
    private fun checkAllPermissions() {
        isReady = false // 재검사 시 로딩 상태로

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
            requestPermissionLauncher.launch(notGranted)
        } else {
            viewModel.onPermissionsResult(true)
            checkOverlayPermission()
        }
    }

    // 2단계: 오버레이 권한 체크
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
                checkAndEnableBluetooth()
            }
        } else {
            checkAndEnableBluetooth()
        }
    }

    // 3단계: 블루투스 활성화 체크
    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter == null) {
            // 미지원 기기는 통과
            refreshServices()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                // 시스템 팝업 ("블루투스를 켜시겠습니까?") 호출
                enableBluetoothLauncher.launch(enableBtIntent)
            } catch (e: SecurityException) {
                Log.e("Bluetooth", "Permission error", e)
                refreshServices()
            }
        } else {
            refreshServices()
        }
    }

    // 4단계: 최종 완료 (서비스 시작 & 화면 표시)
    private fun refreshServices() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isVoiceOn = prefs.getBoolean("voice_detection", false)
        val isShockOn = prefs.getBoolean("shock_detection", false)

        // 음성 감지 서비스
        if (isVoiceOn) {
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

        // [완료] 화면 표시 시작
        isReady = true
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
