package com.example.lifesaivior

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.presentation.BleDebugStats
import com.example.lifesaivior.presentation.AppViewModel
import com.example.lifesaivior.presentation.screen.BlackSaverScreen
import com.example.lifesaivior.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaivior.protocol.security.SignatureLogEntry
import com.example.lifesaivior.presentation.MeshVisualEvent
import com.example.lifesaivior.ui.navigation.AppNavHost
import com.example.lifesaivior.ui.navigation.AppRoute
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.rememberAppScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun LifesaiviorApp(
    // hasPermissions 변수는 이제 화면 분기용이 아니라, 단순히 상태 표시용(예: 설정 화면에서 스위치 상태)으로만 씁니다.
    hasPermissions: Boolean,
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    appViewModel: AppViewModel,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onStopAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendProfileTest: () -> Unit,
    onSendProfileUpdate: (com.example.lifesaivior.core.profile.SurvivorProfile) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit,
    onPulseRescueSignal: () -> Unit,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onClearDeviceMonitoring: () -> Unit,
    isVoiceDetectionEnabled: Boolean,
    isShockDetectionEnabled: Boolean,
    onSetVoiceDetection: (Boolean) -> Unit,
    onSetShockDetection: (Boolean) -> Unit
) {
    val scale = rememberAppScale()

    // [상태 1] 마지막 터치 시간 (절전 모드용)
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // [상태 2] 절전 화면 활성화 여부
    var isSaverVisible by remember { mutableStateOf(false) }

    // 현재 보고 있는 화면 경로 추적 (SOS 화면일 때만 절전 모드 작동)
    var currentRoute by remember { mutableStateOf(AppRoute.SurvivorProfile.route) }

    val autoSaverEnabled =
        isRescueSignalActive && currentRoute == AppRoute.SurvivorEmergency.route
    val autoSaverTimeoutMs = 60_000L // 60초

    // [로직 1] 절전 타이머 로직
    LaunchedEffect(autoSaverEnabled, isSaverVisible, autoSaverTimeoutMs) {
        if (autoSaverEnabled && !isSaverVisible) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastInteractionTime >= autoSaverTimeoutMs) {
                    isSaverVisible = true
                }
                delay(1000L)
            }
        } else if (!autoSaverEnabled) {
            isSaverVisible = false
        }
    }

    // [로직 2] SOS 시작 시 타이머 초기화
    LaunchedEffect(autoSaverEnabled) {
        if (autoSaverEnabled) {
            lastInteractionTime = System.currentTimeMillis()
            isSaverVisible = false
        }
    }

    CompositionLocalProvider(LocalAppScale provides scale) {
        // [전역 터치 감지]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                if (!isSaverVisible) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
        ) {
            // 1. 메인 앱 네비게이션
            AppNavHost(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                directPeerIds = directPeerIds,
                myPeerId = myPeerId,
                myNickname = myNickname,
                peerNicknames = peerNicknames,
                meshGraphSnapshot = meshGraphSnapshot,
                meshVisualEvents = meshVisualEvents,
                bleDebugStats = bleDebugStats,
                appViewModel = appViewModel,
                isMicOn = isMicOn,
                isDisconnecting = isDisconnecting,
                isRescueSignalActive = isRescueSignalActive,
                messages = messages,
                signatureLogs = signatureLogs,
                profileLogs = profileLogs,
                onClearSignatureLogs = onClearSignatureLogs,
                onClearProfileLogs = onClearProfileLogs,
                onStartAutoConnect = onStartAutoConnect,
                onStopAutoConnect = onStopAutoConnect,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onSendMessage = onSendMessage,
                onSendProfileTest = onSendProfileTest,
                onSendProfileUpdate = onSendProfileUpdate,
                onDisconnect = onDisconnect,
                onStartRescueSignal = onStartRescueSignal,
                onStopRescueSignal = onStopRescueSignal,
                onPulseRescueSignal = onPulseRescueSignal,
                onClearDeviceMonitoring = onClearDeviceMonitoring,
                isVoiceDetectionEnabled = isVoiceDetectionEnabled,
                isShockDetectionEnabled = isShockDetectionEnabled,
                onSetVoiceDetection = onSetVoiceDetection,
                onSetShockDetection = onSetShockDetection,
                onRouteChanged = { route -> currentRoute = route }
            )

            // 2. 절전 모드 오버레이 (조건 충족 시 최상단에 표시)
            if (autoSaverEnabled && isSaverVisible) {
                BlackSaverScreen(
                    onUnlock = {
                        lastInteractionTime = System.currentTimeMillis()
                        isSaverVisible = false
                    }
                )
            }
        }
    }
}
