package com.example.lifesaiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.presentation.screen.BlackSaverScreen
import com.example.lifesaiver.presentation.screen.PermissionViewModel
import com.example.lifesaiver.ui.navigation.AppNavHost
import com.example.lifesaiver.ui.navigation.AppRoute
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.rememberAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun LifesaiverApp(
    hasPermissions: Boolean,
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean, // 구조 신호 상태
    messages: List<ChatMessage>,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onStopAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit
) {
    val scale = rememberAppScale()

    // [상태 1] 마지막으로 화면을 터치한 시간 저장
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // [상태 2] 절전 화면 표시 여부
    var isSaverVisible by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf(AppRoute.ModeGate.route) }
    val autoSaverTimeoutMs = if (
        currentRoute == AppRoute.SurvivorPTT.route ||
        currentRoute == AppRoute.RescuerPTT.route
    ) {
        60_000L
    } else {
        10_000L
    }

    // [로직 1] SOS가 켜져 있고, 화면이 켜져있다면(절전X) -> 타이머 체크
    LaunchedEffect(isRescueSignalActive, isSaverVisible, autoSaverTimeoutMs) {
        if (isRescueSignalActive && !isSaverVisible) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                // 마지막 터치로부터 일정 시간이 지났는지 확인
                if (currentTime - lastInteractionTime >= autoSaverTimeoutMs) {
                    isSaverVisible = true // 절전 모드 진입
                }
                delay(1000L) // 1초마다 검사
            }
        } else if (!isRescueSignalActive) {
            // SOS 끄면 절전 모드도 해제
            isSaverVisible = false
        }
    }

    // [로직 2] SOS 시작 시 타이머 초기화
    LaunchedEffect(isRescueSignalActive) {
        if (isRescueSignalActive) {
            lastInteractionTime = System.currentTimeMillis()
            isSaverVisible = false
        }
    }

    CompositionLocalProvider(LocalAppScale provides scale) {
        if (!hasPermissions) {
            val permissionViewModel: PermissionViewModel = viewModel()
            val permissionState by permissionViewModel.uiState.collectAsState()
            PermissionRequiredScreen(
                uiState = permissionState,
                onRequestPermissions = onRequestPermissions
            )
            return@CompositionLocalProvider
        }

        // [전역 터치 감지] Box에 pointerInput을 달아서 모든 터치를 감시
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // 자식 뷰(버튼 등)가 터치를 가져가기 전에 먼저 감지 (Initial 패스)
                            val event = awaitPointerEvent(PointerEventPass.Initial)

                            // 화면을 누르는 동작이 발생하면
                            if (event.changes.any { it.changedToDown() }) {
                                // 절전 화면이 꺼져있을 때만 타이머 갱신 (절전 중일 땐 더블탭으로만 해제)
                                if (!isSaverVisible) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
        ) {
            // 1. 메인 앱 화면
            AppNavHost(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                isMicOn = isMicOn,
                isDisconnecting = isDisconnecting,
                isRescueSignalActive = isRescueSignalActive,
                messages = messages,
                onStartAutoConnect = onStartAutoConnect,
                onStopAutoConnect = onStopAutoConnect,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onSendMessage = onSendMessage,
                onDisconnect = onDisconnect,
                onStartRescueSignal = onStartRescueSignal,
                onStopRescueSignal = onStopRescueSignal,
                onRouteChanged = { route -> currentRoute = route }
            )

            // 2. 절전 모드 오버레이 (SOS 켜짐 + 10초간 터치 없음)
            if (isRescueSignalActive && isSaverVisible) {
                BlackSaverScreen(
                    onUnlock = {
                        // 더블 탭 시: 터치 시간 갱신 + 화면 켜기
                        lastInteractionTime = System.currentTimeMillis()
                        isSaverVisible = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    uiState: com.example.lifesaiver.presentation.screen.PermissionUiState,
    onRequestPermissions: () -> Unit
) {
    val scale = LocalAppScale.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Black)
            .padding(scaledDp(24, scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = uiState.title,
            color = AppColors.White,
            fontSize = scaledSp(20, scale),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.description,
            color = AppColors.Gray500,
            fontSize = scaledSp(14, scale),
            modifier = Modifier.padding(top = scaledDp(8, scale), bottom = scaledDp(16, scale))
        )
        Button(onClick = onRequestPermissions) {
            Text(text = uiState.actionLabel)
        }
    }
}
