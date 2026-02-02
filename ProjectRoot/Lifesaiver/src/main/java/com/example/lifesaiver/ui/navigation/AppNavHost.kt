package com.example.lifesaiver.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.ui.components.ptt.PttBottomBar
import com.example.lifesaiver.ui.components.ptt.PttBottomTab
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.survivor.profile.SurvivorProfileScreen
import com.example.lifesaiver.ui.screen.settings.SettingsScreen
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.wakeup.SensorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import androidx.core.content.ContextCompat

@Composable
fun AppNavHost(
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    onStartAutoConnect: () -> Unit,
    onStopAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit,
    onPulseRescueSignal: () -> Unit,
    onSendProfileTest: () -> Unit,
    onSendProfileUpdate: (SurvivorProfile) -> Unit,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onClearDeviceMonitoring: () -> Unit,
    isVoiceDetectionEnabled: Boolean,
    isShockDetectionEnabled: Boolean,
    onSetVoiceDetection: (Boolean) -> Unit,
    onSetShockDetection: (Boolean) -> Unit,
    onRouteChanged: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scale = LocalAppScale.current
    val context = LocalContext.current

    val profileStore = remember(context) { ProfileStore(context) }
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())
    var pendingSosNavigation by remember { mutableStateOf(false) }
    var sosStartedAt by remember { mutableStateOf(0L) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var sttEnabled by remember { mutableStateOf(false) }
    val minSosDurationMs = 1_000L
    val currentRoute = backStackEntry?.destination?.route
    val footerEnabledRoutes = setOf(
        AppRoute.SurvivorPTT.route,
        AppRoute.SurvivorChat.route,
        AppRoute.Settings.route
    )
    val swipeRoutes = listOf(
        AppRoute.SurvivorPTT.route,
        AppRoute.SurvivorChat.route,
        AppRoute.Settings.route
    )
    val shouldShowFooter = currentRoute in footerEnabledRoutes
    val navigateBottomTab: (String) -> Unit = { targetRoute ->
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }
    val navigateBySwipe: (Int) -> Unit = swipe@{ delta ->
        val route = navController.currentBackStackEntry?.destination?.route ?: return@swipe
        val currentIndex = swipeRoutes.indexOf(route)
        if (currentIndex == -1) return@swipe
        val targetRoute = swipeRoutes.getOrNull(currentIndex + delta) ?: return@swipe
        navigateBottomTab(targetRoute)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != SensorService.ACTION_SENSOR_TRIGGERED) return
                sttEnabled = true
                sttResetToken = System.currentTimeMillis()
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute != AppRoute.SurvivorStandby.route) {
                    navController.navigate(AppRoute.SurvivorStandby.route) {
                        launchSingleTop = true
                    }
                }
            }
        }
        val filter = IntentFilter(SensorService.ACTION_SENSOR_TRIGGERED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(backStackEntry) {
        val route = backStackEntry?.destination?.route ?: AppRoute.SurvivorProfile.route
        onRouteChanged(route)
        if (route == AppRoute.SurvivorStandby.route) {
            sttResetToken = System.currentTimeMillis()
        }
    }

    LaunchedEffect(profileState.isComplete, backStackEntry) {
        val currentRoute = backStackEntry?.destination?.route
        if (profileState.isComplete &&
            currentRoute == AppRoute.SurvivorProfile.route &&
            navController.previousBackStackEntry == null
        ) {
            navController.navigate(AppRoute.SurvivorStandby.route) {
                popUpTo(AppRoute.SurvivorProfile.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(isConnected, pendingSosNavigation, sosStartedAt) {
        if (pendingSosNavigation && isConnected) {
            val elapsed = System.currentTimeMillis() - sosStartedAt
            if (elapsed < minSosDurationMs) {
                delay(minSosDurationMs - elapsed)
            }
            pendingSosNavigation = false
            navController.navigate(AppRoute.SurvivorPTT.route)
        }
    }

    LaunchedEffect(isConnected, isRescueSignalActive, backStackEntry) {
        val currentRoute = backStackEntry?.destination?.route
        if (isRescueSignalActive && !isConnected) {
            val targetRoute = AppRoute.SurvivorEmergency.route
            if (!pendingSosNavigation) {
                pendingSosNavigation = true
                sosStartedAt = System.currentTimeMillis()
            }
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    if (currentRoute == AppRoute.SurvivorPTT.route) {
                        popUpTo(currentRoute) { inclusive = true }
                    } else {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.SurvivorProfile.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (shouldShowFooter) scaledDp(58, scale) else scaledDp(0, scale))
                .pointerInput(shouldShowFooter, currentRoute) {
                    if (!shouldShowFooter) return@pointerInput
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDragX += dragAmount
                        },
                        onDragEnd = {
                            val threshold = size.width * 0.18f
                            when {
                                totalDragX < -threshold -> navigateBySwipe(+1)
                                totalDragX > threshold -> navigateBySwipe(-1)
                            }
                        }
                    )
                }
        ) {
            composable(AppRoute.SurvivorStandby.route) {
                StandbyStatusScreen(
                    batteryLevel = batteryLevel,
                    sttResetToken = sttResetToken,
                    sttEnabled = sttEnabled,
                    onPrev = { navController.popBackStack() },
                    onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                    onSos = {
                        pendingSosNavigation = true
                        sosStartedAt = System.currentTimeMillis()
                        navController.navigate(AppRoute.SurvivorEmergency.route)
                    },
                    onSettings = { navController.navigate(AppRoute.Settings.route) }
                )
            }

            composable(AppRoute.SurvivorProfile.route) {
                SurvivorProfileScreen(
                    profileStore = profileStore,
                    onSaved = {
                        val prevRoute = navController.previousBackStackEntry?.destination?.route
                        if (prevRoute == null) {
                            navController.navigate(AppRoute.SurvivorStandby.route) {
                                popUpTo(AppRoute.SurvivorProfile.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onSendProfileUpdate = onSendProfileUpdate,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppRoute.SurvivorEmergency.route) {
                val emergencyViewModel: EmergencyBeaconViewModel = viewModel()
                val emergencyState by emergencyViewModel.uiState.collectAsState()
                val stopAndBack = {
                    pendingSosNavigation = false
                    onStopAutoConnect()
                    onStopRescueSignal()
                    navController.popBackStack()
                    Unit
                }
                LaunchedEffect(Unit) {
                    if (!isRescueSignalActive) {
                        onStartRescueSignal()
                    }
                    onStartAutoConnect()
                }
                BackHandler {
                    stopAndBack()
                }
                SurvivorEmergencyBeaconScreen(
                    batteryLevel = batteryLevel,
                    uiState = emergencyState,
                    onPrev = stopAndBack,
                )
            }

            composable(AppRoute.SurvivorPTT.route) {
                LaunchedEffect(Unit) {
                    onStartAutoConnect()
                }
                PTTLinkScreen(
                    batteryLevel = batteryLevel,
                    connectedCount = connectedCount,
                    meshPeerCount = meshPeerCount,
                    directPeerIds = directPeerIds,
                    myPeerId = myPeerId,
                    myNickname = myNickname,
                    peerNicknames = peerNicknames,
                    meshGraphSnapshot = meshGraphSnapshot,
                    meshVisualEvents = meshVisualEvents,
                    bleDebugStats = bleDebugStats,
                    isConnected = isConnected,
                    onBack = { navController.popBackStack() },
                    onDisconnect = {
                        onDisconnect()
                        navController.navigate(AppRoute.SurvivorStandby.route) {
                            popUpTo(AppRoute.SurvivorStandby.route) { inclusive = true }
                        }
                    },
                    onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                    onPanicClear = onClearDeviceMonitoring,
                    onSettings = { navigateBottomTab(AppRoute.Settings.route) }
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    isVoiceOn = isVoiceDetectionEnabled,
                    isShockOn = isShockDetectionEnabled,
                    onVoiceToggle = onSetVoiceDetection,
                    onShockToggle = onSetShockDetection,
                    onBack = { navController.popBackStack() },
                    onEditProfile = {
                        navController.navigate(AppRoute.SurvivorProfile.route)
                    }
                )
            }

            composable(AppRoute.SurvivorChat.route) {
                val chatViewModel: RescueChatViewModel = viewModel()
                val chatState by chatViewModel.uiState.collectAsState()
                RescueChatScreen(
                    roomTitle = "전체 채팅",
                    meshPeerCount = meshPeerCount,
                    messages = messages,
                    signatureLogs = signatureLogs,
                    profileLogs = profileLogs,
                    peerNodes = meshGraphSnapshot.nodes,
                    isMicOn = isMicOn,
                    onMicPress = onMicPress,
                    onMicRelease = onMicRelease,
                    onClearSignatureLogs = onClearSignatureLogs,
                    onClearProfileLogs = onClearProfileLogs,
                    onSendProfileTest = onSendProfileTest,
                    inputValue = chatState.inputValue,
                    onInputChange = { chatViewModel.onInputChange(it) },
                    onSendClick = {
                        chatViewModel.consumeSend()?.let { text -> onSendMessage(text) }
                    }
                )
            }
        }

        if (shouldShowFooter) {
            val selectedTab = when (currentRoute) {
                AppRoute.SurvivorChat.route -> PttBottomTab.Chat
                AppRoute.Settings.route -> PttBottomTab.Settings
                else -> PttBottomTab.Home
            }
            PttBottomBar(
                selectedTab = selectedTab,
                onHome = { navigateBottomTab(AppRoute.SurvivorPTT.route) },
                onChat = { navigateBottomTab(AppRoute.SurvivorChat.route) },
                onSettings = { navigateBottomTab(AppRoute.Settings.route) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(scaledDp(58, scale))
                    .padding(bottom = scaledDp(4, scale))
            )
        }
    }
}
