package com.example.lifesaiver.ui.navigation

import android.app.Activity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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

// survivor
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen

// rescuer
import com.example.lifesaiver.ui.screen.rescuer.standby.RescuerStandbyScreen
import com.example.lifesaiver.ui.screen.rescuer.ptt.RescuerPTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.chat.RescuerChatScreen
import com.example.lifesaiver.ui.screen.rescuer.emergency.EmergencyBeaconScreen as RescuerEmergencyBeaconScreen

@Composable
fun AppNavHost(
    batteryLevel: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val activity = LocalContext.current as? Activity

    var pendingSosNavigation by remember { mutableStateOf(false) }

    // ✅ 생존자 SOS 흐름에서만: Emergency 띄워놓고 연결되면 PTT로
    LaunchedEffect(isConnected, pendingSosNavigation) {
        if (pendingSosNavigation && isConnected) {
            pendingSosNavigation = false
            navController.navigate(AppRoute.SurvivorPTT.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.ModeGate.route
    ) {
        // ----------------------------
        // 0) 모드 선택
        // ----------------------------
        composable(AppRoute.ModeGate.route) {
            val modeGateViewModel: ModeGateViewModel = viewModel()
            val modeGateState by modeGateViewModel.uiState.collectAsState()
            ModeGateScreen(
                batteryLevel = batteryLevel,
                uiState = modeGateState,
                onYes = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorStandby.route)
                },
                onNo = { activity?.finish() },
                onRescuerMode = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                }
            )
        }
        composable(AppRoute.StandbyStatus.route) {
            val standbyViewModel: StandbyStatusViewModel = viewModel()
            val standbyState by standbyViewModel.uiState.collectAsState()

        // ----------------------------
        // survivor
        // ----------------------------
        composable(AppRoute.SurvivorStandby.route) {
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
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorEmergency.route)
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

        composable(AppRoute.SurvivorEmergency.route) {
            SurvivorEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                uiState = emergencyState,
                onPrev = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.SurvivorPTT.route) }
            )
        }

        composable(AppRoute.SurvivorPTT.route) {
        composable(AppRoute.PTTLink.route) {
            val pttViewModel: PTTLinkViewModel = viewModel()
            val pttState by pttViewModel.uiState.collectAsState()
            PTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = if (isConnected) 2 else 0,
                isConnected = isConnected,
                isMicOn = isMicOn,
                onToggleMic = onToggleMic,
                onBack = { navController.popBackStack() },
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
                onChat = { navController.navigate(AppRoute.SurvivorChat.route) }
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

        composable(AppRoute.SurvivorChat.route) {
        composable(AppRoute.RescueChat.route) {
            val chatViewModel: RescueChatViewModel = viewModel()
            val chatState by chatViewModel.uiState.collectAsState()
            RescueChatScreen(
                roomTitle = "생존자 채팅",
                messages = messages,
                onPrev = { navController.popBackStack() },
                onSend = onSendMessage
            )
        }

        // ----------------------------
        // rescuer
        // ----------------------------
        composable(AppRoute.RescuerStandby.route) {
            RescuerStandbyScreen(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                connectedCount = if (isConnected) 2 else 0,
                onPrev = { navController.navigate(AppRoute.ModeGate.route) },
                onGoPTT = { navController.navigate(AppRoute.RescuerPTT.route) },
                onSos = { navController.navigate(AppRoute.RescuerEmergency.route) }
            )
        }

        composable(AppRoute.RescuerPTT.route) {
            RescuerPTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = if (isConnected) 2 else 0,
                isConnected = isConnected,
                isMicOn = isMicOn,
                onToggleMic = onToggleMic,
                onBack = { navController.popBackStack() },
                onDisconnect = { navController.navigate(AppRoute.RescuerStandby.route) },
                onChat = { navController.navigate(AppRoute.RescuerChat.route) }
            )
        }

        composable(AppRoute.RescuerChat.route) {
            RescuerChatScreen(
                roomTitle = "구조자 채팅",
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

        composable(AppRoute.RescuerEmergency.route) {
            RescuerEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.RescuerPTT.route) }
            )
        }
    }
}
