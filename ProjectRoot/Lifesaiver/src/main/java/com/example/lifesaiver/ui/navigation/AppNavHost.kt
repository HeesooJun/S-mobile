package com.example.lifesaiver.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.survivor.profile.SurvivorProfileScreen
import com.example.lifesaiver.ui.screen.settings.SettingsScreen
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
    onRouteChanged: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current

    // [핵심 수정] Activity에 연결된 AppViewModel을 여기서 직접 가져옴
    // 이렇게 하면 LifesaiverApp 코드를 수정하지 않아도 데이터를 공유할 수 있음
    val appViewModel: AppViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
    val appUiState by appViewModel.uiState.collectAsState()

    val profileStore = remember(context) { ProfileStore(context) }
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())
    var pendingSosNavigation by remember { mutableStateOf(false) }
    var sosStartedAt by remember { mutableStateOf(0L) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var sttEnabled by remember { mutableStateOf(false) }
    val minSosDurationMs = 1_000L

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

    NavHost(
        navController = navController,
        startDestination = AppRoute.SurvivorProfile.route
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
                isMicOn = isMicOn,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    onDisconnect()
                    navController.navigate(AppRoute.SurvivorStandby.route) {
                        popUpTo(AppRoute.SurvivorStandby.route) { inclusive = true }
                    }
                },
                onChat = { navController.navigate(AppRoute.SurvivorChat.route) },
                onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                onPanicClear = onClearDeviceMonitoring,
                onSettings = { navController.navigate(AppRoute.Settings.route) }
            )
        }

        // ▼▼▼ SettingsScreen 네비게이션 등록 (수정됨) ▼▼▼
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                // 위에서 가져온 appViewModel의 상태와 함수를 전달
                isVoiceOn = appUiState.isVoiceDetectionEnabled,
                isShockOn = appUiState.isShockDetectionEnabled,
                onVoiceToggle = { enabled -> appViewModel.setVoiceDetection(enabled) },
                onShockToggle = { enabled -> appViewModel.setShockDetection(enabled) },
                onBack = { navController.popBackStack() },
                onEditProfile = {
                    navController.navigate(AppRoute.SurvivorProfile.route)
                }
            )
        }
        // ▲▲▲ 추가된 부분 ▲▲▲

        composable(AppRoute.SurvivorChat.route) {
            val chatViewModel: RescueChatViewModel = viewModel()
            val chatState by chatViewModel.uiState.collectAsState()
            RescueChatScreen(
                roomTitle = "전체 채팅",
                meshPeerCount = meshPeerCount,
                messages = messages,
                signatureLogs = signatureLogs,
                profileLogs = profileLogs,
                onClearSignatureLogs = onClearSignatureLogs,
                onClearProfileLogs = onClearProfileLogs,
                onSendProfileTest = onSendProfileTest,
                onPrev = { navController.popBackStack() },
                inputValue = chatState.inputValue,
                onInputChange = { chatViewModel.onInputChange(it) },
                onSendClick = {
                    chatViewModel.consumeSend()?.let { text -> onSendMessage(text) }
                }
            )
        }
    }
}
