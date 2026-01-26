package com.example.lifesaiver.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.ModeGateViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.ui.screen.mode.ModeGateScreen
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.chat.RescuerChatScreen
import com.example.lifesaiver.ui.screen.rescuer.emergency.EmergencyBeaconScreen as RescuerEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.rescuer.ptt.RescuerPTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.standby.RescuerStandbyScreen
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen

@Composable
fun AppNavHost(
    batteryLevel: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit
) {
    val navController = rememberNavController()
    val activity = LocalContext.current as? Activity
    var pendingSosNavigation by remember { mutableStateOf(false) }

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

        composable(AppRoute.SurvivorStandby.route) {
            StandbyStatusScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onSos = {
                    onStartRescueSignal()
                    pendingSosNavigation = true
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorEmergency.route)
                }
            )
        }

        composable(AppRoute.SurvivorEmergency.route) {
            val emergencyViewModel: EmergencyBeaconViewModel = viewModel()
            val emergencyState by emergencyViewModel.uiState.collectAsState()
            LaunchedEffect(isConnected) {
                if (!isConnected) {
                    onStartAutoConnect()
                }
            }
            SurvivorEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                uiState = emergencyState,
                onPrev = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.SurvivorPTT.route) }
            )
        }

        composable(AppRoute.SurvivorPTT.route) {
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
                onChat = { navController.navigate(AppRoute.SurvivorChat.route) }
            )
        }

        composable(AppRoute.SurvivorChat.route) {
            val chatViewModel: RescueChatViewModel = viewModel()
            val chatState by chatViewModel.uiState.collectAsState()
            RescueChatScreen(
                roomTitle = "Survivor Chat",
                messages = messages,
                onPrev = { navController.popBackStack() },
                inputValue = chatState.inputValue,
                onInputChange = { chatViewModel.onInputChange(it) },
                onSendClick = {
                    chatViewModel.consumeSend()?.let { text -> onSendMessage(text) }
                }
            )
        }

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
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    onDisconnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                },
                onChat = { navController.navigate(AppRoute.RescuerChat.route) }
            )
        }

        composable(AppRoute.RescuerChat.route) {
            RescuerChatScreen(
                roomTitle = "Rescuer Chat",
                messages = messages,
                onPrev = { navController.popBackStack() },
                onSend = onSendMessage
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
