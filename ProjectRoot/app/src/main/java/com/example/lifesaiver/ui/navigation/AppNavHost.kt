package com.example.lifesaiver.ui.navigation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.ModeGateViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.ui.screen.mode.ModeGateScreen
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.chat.RescuerChatScreen
import com.example.lifesaiver.ui.screen.rescuer.db.RescuerSurvivorDbScreen
import com.example.lifesaiver.ui.screen.rescuer.emergency.EmergencyBeaconScreen as RescuerEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.rescuer.ptt.RescuerPTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.standby.RescuerStandbyScreen
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.survivor.profile.SurvivorProfileScreen
import com.example.lifesaiver.wakeup.SensorService

@Composable
fun AppNavHost(
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    bleDebugStats: BleDebugStats,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    onStartAutoConnect: () -> Unit,
    onStopAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit,
    onRouteChanged: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val appContext = context.applicationContext
    val profileStore = remember(context) { ProfileStore(context) }
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())
    var pendingSosNavigation by remember { mutableStateOf(false) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var isSensorMonitoring by remember { mutableStateOf(false) }
    var sttEnabled by remember { mutableStateOf(false) }

    fun startSensorService() {
        val intent = Intent(appContext, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopSensorService() {
        val intent = Intent(appContext, SensorService::class.java)
        appContext.stopService(intent)
    }

    fun requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(appContext)) return

        Toast.makeText(
            appContext,
            "화면 자동 켜짐을 위해 '다른 앱 위에 표시' 권한을 허용해주세요.",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
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
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(backStackEntry) {
        val route = backStackEntry?.destination?.route ?: AppRoute.ModeGate.route
        onRouteChanged(route)
        if (route == AppRoute.SurvivorStandby.route) {
            // 센서 트리거로 들어온 경우에만 STT를 켜기 위해 reset token만 갱신
            sttResetToken = System.currentTimeMillis()
        }
    }

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
                isSensorMonitoring = isSensorMonitoring,
                onYes = {
                    sttEnabled = false
                    if (profileState.isComplete) {
                        onStartAutoConnect()
                        navController.navigate(AppRoute.SurvivorStandby.route)
                    } else {
                        navController.navigate(AppRoute.SurvivorProfile.route)
                    }
                },
                onNo = { activity?.finish() },
                onRescuerMode = {
                    sttEnabled = false
                    onStartAutoConnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                },
                onToggleSensorMonitor = {
                    if (isSensorMonitoring) {
                        stopSensorService()
                        isSensorMonitoring = false
                    } else {
                        startSensorService()
                        requestOverlayPermissionIfNeeded()
                        isSensorMonitoring = true
                        activity?.moveTaskToBack(true)
                    }
                }
            )
        }

        composable(AppRoute.SurvivorStandby.route) {
            StandbyStatusScreen(
                batteryLevel = batteryLevel,
                sttResetToken = sttResetToken,
                sttEnabled = sttEnabled,
                onPrev = { navController.popBackStack() },
                onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                onSos = {
                    onStartRescueSignal()
                    pendingSosNavigation = true
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorEmergency.route)
                }
            )
        }

        composable(AppRoute.SurvivorProfile.route) {
            SurvivorProfileScreen(
                profileStore = profileStore,
                onSaved = {
                    onStartAutoConnect()
                    navController.navigate(AppRoute.SurvivorStandby.route) {
                        popUpTo(AppRoute.SurvivorProfile.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.SurvivorEmergency.route) {
            val emergencyViewModel: EmergencyBeaconViewModel = viewModel()
            val emergencyState by emergencyViewModel.uiState.collectAsState()
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            SurvivorEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                uiState = emergencyState,
                onPrev = {
                    pendingSosNavigation = false
                    onStopAutoConnect()
                    navController.popBackStack()
                },
                onNext = {
                    pendingSosNavigation = false
                    navController.navigate(AppRoute.SurvivorPTT.route)
                }
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
                bleDebugStats = bleDebugStats,
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
                roomTitle = "전체 채팅",
                meshPeerCount = meshPeerCount,
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
                connectedCount = connectedCount,
                onPrev = { navController.navigate(AppRoute.ModeGate.route) },
                onGoPTT = { navController.navigate(AppRoute.RescuerPTT.route) },
                onSos = { navController.navigate(AppRoute.RescuerEmergency.route) }
            )
        }

        composable(AppRoute.RescuerPTT.route) {
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            RescuerPTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                isConnected = isConnected,
                isMicOn = isMicOn,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    onDisconnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                },
                onChat = { navController.navigate(AppRoute.RescuerChat.route) },
                onOpenSurvivorDb = { navController.navigate(AppRoute.RescuerSurvivorDb.route) }
            )
        }

        composable(AppRoute.RescuerSurvivorDb.route) {
            RescuerSurvivorDbScreen(
                survivors = emptyList(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.RescuerChat.route) {
            RescuerChatScreen(
                roomTitle = "전체 채팅",
                meshPeerCount = meshPeerCount,
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
