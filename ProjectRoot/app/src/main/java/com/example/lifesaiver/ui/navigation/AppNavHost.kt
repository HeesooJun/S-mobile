package com.example.lifesaiver.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
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
    onToggleMic: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val roomTitle = remember { "김싸피의 채팅방" }
    var pendingSosNavigation by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

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
            ModeGateScreen(
                batteryLevel = batteryLevel,
                onYes = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.StandbyStatus.route)
                },
                onNo = {
                    activity?.finish()
                },
                onRescuerMode = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.PTTLink.route)
                }
            )
        }
        composable(AppRoute.StandbyStatus.route) {
            StandbyStatusScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onSos = {
                    pendingSosNavigation = true
                    onStartAutoConnect()
                    navController.navigate(AppRoute.EmergencyBeacon.route)
                }
            )
        }
        composable(AppRoute.EmergencyBeacon.route) {
            EmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.PTTLink.route) }
            )
        }
        composable(AppRoute.PTTLink.route) {
            PTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = if (isConnected) 2 else 0,
                isConnected = isConnected,
                isMicOn = isMicOn,
                onToggleMic = onToggleMic,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    onDisconnect()
                    navController.navigate(AppRoute.ModeGate.route) {
                        popUpTo(AppRoute.ModeGate.route) { inclusive = true }
                    }
                },
                onChat = { navController.navigate(AppRoute.RescueChat.route) }
            )
        }
        composable(AppRoute.RescueChat.route) {
            RescueChatScreen(
                roomTitle = roomTitle,
                messages = messages,
                onPrev = { navController.popBackStack() },
                onSend = onSendMessage
            )
        }
    }
}
