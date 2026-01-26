package com.example.lifesaiver.ui.navigation

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
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
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onToggleMic: () -> Unit,
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
            ModeGateScreen(
                batteryLevel = batteryLevel,
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

        // ----------------------------
        // survivor
        // ----------------------------
        composable(AppRoute.SurvivorStandby.route) {
            StandbyStatusScreen(
                batteryLevel = batteryLevel,
                onPrev = { navController.popBackStack() },
                onSos = {
                    pendingSosNavigation = true
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorEmergency.route)
                }
            )
        }

        composable(AppRoute.SurvivorEmergency.route) {
            SurvivorEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
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
                onToggleMic = onToggleMic,
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
