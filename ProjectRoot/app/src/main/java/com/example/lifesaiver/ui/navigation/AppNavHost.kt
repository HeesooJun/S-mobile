package com.example.lifesaiver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.ModeGateViewModel
import com.example.lifesaiver.presentation.screen.PTTLinkViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.presentation.screen.StandbyStatusViewModel
import com.example.lifesaiver.ui.screen.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.emergency.EmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.mode.ModeGateScreen
import com.example.lifesaiver.ui.screen.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.standby.StandbyStatusScreen

@Composable
fun AppNavHost(
    batteryLevel: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    // [추가 1] 구조 신호 활성화 상태 (LifesaiverApp 오류 해결)
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    // [추가 2] 구조 신호 제어 함수들 (LifesaiverApp 오류 해결)
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit
) {
    val navController = rememberNavController()
    val roomTitle = remember { "김싸피의 채팅방" }
    var pendingSosNavigation by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected, pendingSosNavigation) {
        if (pendingSosNavigation && isConnected) {
            pendingSosNavigation = false
            navController.navigate(AppRoute.PTTLink.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.ModeGate.route
    ) {
        composable(AppRoute.ModeGate.route) {
            val modeGateViewModel: ModeGateViewModel = viewModel()
            val modeGateState by modeGateViewModel.uiState.collectAsState()
            ModeGateScreen(
                batteryLevel = batteryLevel,
                uiState = modeGateState,
                onYes = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.StandbyStatus.route)
                },
                onNo = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.StandbyStatus.route)
                },
                onRescuerMode = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.PTTLink.route)
                }
            )
        }
        composable(AppRoute.StandbyStatus.route) {
            val standbyViewModel: StandbyStatusViewModel = viewModel()
            val standbyState by standbyViewModel.uiState.collectAsState()
            StandbyStatusScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onSos = {
                    // [수정] SOS 버튼 누르면 구조 신호 시작 후 화면 이동
                    onStartRescueSignal()
                    pendingSosNavigation = true
                    navController.navigate(AppRoute.EmergencyBeacon.route)
                },
                uiState = standbyState,
                sensorItems = standbyViewModel.sensorItems,
                // [추가 3] StandbyStatusScreen에도 전달 (화면 버튼 상태 반영용)
                isRescueSignalActive = isRescueSignalActive,
                onStartRescueSignal = onStartRescueSignal,
                onStopRescueSignal = onStopRescueSignal,

                onSensorExpandedChange = { standbyViewModel.setSensorExpanded(it) },
                onSensorStatusChange = { type, status ->
                    standbyViewModel.updateSensorStatus(type, status)
                }
            )
        }
        composable(AppRoute.EmergencyBeacon.route) {
            val emergencyViewModel: EmergencyBeaconViewModel = viewModel()
            val emergencyState by emergencyViewModel.uiState.collectAsState()
            LaunchedEffect(isConnected) {
                if (!isConnected) {
                    onStartAutoConnect()
                }
            }
            EmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                uiState = emergencyState,
                onPrev = {
                    // [선택] 뒤로가기 시 구조 신호를 끄고 싶다면 주석 해제
                    // onStopRescueSignal()
                    navController.popBackStack()
                },
                onNext = { navController.navigate(AppRoute.PTTLink.route) }
            )
        }
        composable(AppRoute.PTTLink.route) {
            val pttViewModel: PTTLinkViewModel = viewModel()
            val pttState by pttViewModel.uiState.collectAsState()
            PTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = if (isConnected) 2 else 0,
                isConnected = isConnected,
                isMicOn = isMicOn,
                isDisconnecting = isDisconnecting,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    onDisconnect()
                    navController.navigate(AppRoute.ModeGate.route) {
                        popUpTo(AppRoute.ModeGate.route) { inclusive = true }
                    }
                },
                onChat = { navController.navigate(AppRoute.RescueChat.route) },
                uiState = pttState,
                sensorItems = pttViewModel.sensorItems,
                onSensorExpandedChange = { pttViewModel.setSensorExpanded(it) },
                onSensorStatusChange = { type, status ->
                    pttViewModel.updateSensorStatus(type, status)
                },
                onPowerToggle = { pttViewModel.togglePowerSaving() },
                onActionSelected = { action -> pttViewModel.onActionSelected(action) }
            )
        }
        composable(AppRoute.RescueChat.route) {
            val chatViewModel: RescueChatViewModel = viewModel()
            val chatState by chatViewModel.uiState.collectAsState()
            RescueChatScreen(
                roomTitle = roomTitle,
                messages = messages,
                onPrev = { navController.popBackStack() },
                inputValue = chatState.inputValue,
                onInputChange = { chatViewModel.onInputChange(it) },
                onSendClick = {
                    chatViewModel.consumeSend()?.let { text ->
                        onSendMessage(text)
                    }
                }
            )
        }
    }
}
