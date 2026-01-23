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
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
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
                    pendingSosNavigation = true
                    navController.navigate(AppRoute.EmergencyBeacon.route)
                },
                uiState = standbyState,
                sensorItems = standbyViewModel.sensorItems,
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
                onPrev = { navController.popBackStack() },
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
