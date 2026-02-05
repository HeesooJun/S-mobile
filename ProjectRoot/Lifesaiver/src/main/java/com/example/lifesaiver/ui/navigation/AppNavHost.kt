package com.example.lifesaiver.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.call.CallTransportType
import com.example.lifesaiver.core.audio.RealtimeAudioStreamEngine
import com.example.lifesaiver.core.call.RealTimeCallManager
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.screen.CallViewModel
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.model.CallHandshakeAction
import com.example.lifesaiver.protocol.model.CallHandshakeState
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.ui.components.ptt.PttBottomBar
import com.example.lifesaiver.ui.components.ptt.PttBottomTab
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.survivor.ptt.SurvivorCallRequest
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.survivor.profile.SurvivorProfileScreen
import com.example.lifesaiver.ui.screen.settings.SettingsScreen
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.wakeup.SensorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val appContext = context.applicationContext
    val appViewModel: AppViewModel = viewModel(
        viewModelStoreOwner = context as ViewModelStoreOwner
    )
    val appState by appViewModel.uiState.collectAsState()
    val audioEngine = remember(appContext) { RealtimeAudioStreamEngine(appContext) }
    val localOpusSupported = remember(audioEngine) { audioEngine.isOpusSupported() }
    val callManager = remember(appViewModel) {
        RealTimeCallManager(
            audioEngine = audioEngine,
            wifiAwareRanger = appViewModel.wifiAwareRanger,
            wifiDirectRanger = appViewModel.wifiDirectRanger
        )
    }
    val callViewModel: CallViewModel = viewModel(
        viewModelStoreOwner = context as ViewModelStoreOwner,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(callManager) as T
            }
        }
    )
    val isInCall by callViewModel.isInCall.collectAsState()
    val targetSurvivor by callViewModel.targetSurvivor.collectAsState()
    val callDebugState by callViewModel.debugState.collectAsState()
    val activeCallTransportReady = when (callDebugState.activeTransport) {
        CallTransportType.WIFI_AWARE -> callDebugState.wifiAware.isReady
        CallTransportType.WIFI_DIRECT -> callDebugState.wifiDirect.isReady
        CallTransportType.NONE -> false
    }
    val liveAwareRttMeters by appViewModel.wifiAwareRanger.rttDistance.collectAsState()
    val awareLinkReady by appViewModel.wifiAwareRanger.isConnectionReady.collectAsState()
    LaunchedEffect(isInCall) {
        // Disable RTT during call to avoid NDP/RTT interference.
        appViewModel.wifiAwareRanger.setRttEnabled(!isInCall)
    }

    val profileStore = remember(context) { ProfileStore(context) }
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())
    var pendingSosNavigation by remember { mutableStateOf(false) }
    var sosStartedAt by remember { mutableStateOf(0L) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var sttEnabled by remember { mutableStateOf(false) }
    val minSosDurationMs = 1_000L
    var autoAcceptedPeerId by remember { mutableStateOf<String?>(null) }
    var connectingTargetPeerId by remember { mutableStateOf<String?>(null) }
    var lastAwareRttReportAtMs by remember { mutableStateOf(0L) }
    var lastAwareRttReportCm by remember { mutableStateOf<Int?>(null) }
    var forceExitPowerSavingToken by remember { mutableStateOf(0L) }
    var forceSetPowerSavingToken by remember { mutableStateOf(0L) }
    var forceSetPowerSavingEnabled by remember { mutableStateOf(false) }
    val callAttemptTimeoutMs = 15_000L
    val currentRoute = backStackEntry?.destination?.route
    var pendingBottomTabRoute by remember { mutableStateOf<String?>(null) }
    val autoSosToneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 90) }
    DisposableEffect(autoSosToneGenerator) {
        onDispose {
            runCatching { autoSosToneGenerator.release() }
        }
    }
    LaunchedEffect(
        isInCall,
        currentRoute,
        appState.directPeerIds,
        appState.survivors
    ) {
        if (isInCall) return@LaunchedEffect
        val routeAllowsAwareProbe =
            currentRoute == AppRoute.SurvivorStandby.route ||
                currentRoute == AppRoute.SurvivorStandbySettings.route ||
                currentRoute == AppRoute.SurvivorPTT.route ||
                currentRoute == AppRoute.SurvivorChat.route ||
                currentRoute == AppRoute.Settings.route
        // Keep survivor Aware probing active on core tabs so rescuer can discover reliably.
        appViewModel.wifiAwareRanger.updatePeerCapability(routeAllowsAwareProbe)
    }
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
    val navigateBottomTab: (String) -> Unit = tab@{ targetRoute ->
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (pendingBottomTabRoute != null || currentRoute == targetRoute) return@tab
        pendingBottomTabRoute = targetRoute
        if (!navController.popBackStack(targetRoute, inclusive = false)) {
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
    fun triggerAutoSosFeedback() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(320L, 220))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(320L)
                }
            }
        }
        runCatching {
            autoSosToneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 380)
        }
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
        if (route == pendingBottomTabRoute) {
            pendingBottomTabRoute = null
        }
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

    LaunchedEffect(appState.incomingCallPeerId, isInCall, backStackEntry) {
        val current = backStackEntry?.destination?.route
        if (appState.incomingCallPeerId != null && !isInCall && current != AppRoute.SurvivorPTT.route) {
            navController.navigate(AppRoute.SurvivorPTT.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(appState.callPeerId, isInCall) {
        if (isInCall && appState.callPeerId == null) {
            callViewModel.endCall()
            connectingTargetPeerId = null
        }
    }

    LaunchedEffect(
        connectingTargetPeerId,
        isInCall,
        activeCallTransportReady,
        targetSurvivor?.peerId,
        appState.callPeerId
    ) {
        val targetPeerId = connectingTargetPeerId ?: return@LaunchedEffect
        val connected =
            isInCall &&
                activeCallTransportReady &&
                (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
        if (connected) {
            connectingTargetPeerId = null
        }
    }

    LaunchedEffect(connectingTargetPeerId) {
        val targetPeerId = connectingTargetPeerId ?: return@LaunchedEffect
        val completedInTime = withTimeoutOrNull(callAttemptTimeoutMs) {
            snapshotFlow {
                val transportReady = when (callDebugState.activeTransport) {
                    CallTransportType.WIFI_AWARE -> callDebugState.wifiAware.isReady
                    CallTransportType.WIFI_DIRECT -> callDebugState.wifiDirect.isReady
                    CallTransportType.NONE -> false
                }
                val connected =
                    isInCall &&
                        transportReady &&
                        (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
                val canceled = connectingTargetPeerId != targetPeerId
                connected || canceled
            }.first { done -> done }
        } != null
        if (connectingTargetPeerId != targetPeerId) return@LaunchedEffect
        val connectedNow =
            isInCall &&
                activeCallTransportReady &&
                (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
        if (completedInTime || connectedNow) return@LaunchedEffect
        val localAware = appViewModel.isWifiAwareSupportedLocally()
        val localDirect = appViewModel.isWifiDirectSupportedLocally()
        appViewModel.sendCallHandshake(
            targetPeerIdHex = targetPeerId,
            action = CallHandshakeAction.END,
            callerName = profileState.name.ifBlank { "생존자" },
            wifiAwareSupported = localAware,
            wifiDirectSupported = localDirect,
            useOpus = localOpusSupported
        )
        appViewModel.clearLocalCallState(targetPeerId)
        callViewModel.endCall()
        connectingTargetPeerId = null
        Toast.makeText(context, "통화 연결 시간 초과(15초)", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(isInCall, appState.callPeerId, awareLinkReady, liveAwareRttMeters) {
        val peerId = appState.callPeerId ?: connectingTargetPeerId ?: targetSurvivor?.peerId
        if (!isInCall || peerId.isNullOrBlank()) {
            lastAwareRttReportAtMs = 0L
            lastAwareRttReportCm = null
            return@LaunchedEffect
        }
        if (!awareLinkReady) return@LaunchedEffect
        val rttMeters = liveAwareRttMeters ?: return@LaunchedEffect
        val rttCm = (rttMeters * 100f).roundToInt().coerceIn(0, 0xFFFF)
        val now = System.currentTimeMillis()
        val lastCm = lastAwareRttReportCm
        val changedEnough = lastCm == null || abs(rttCm - lastCm) >= 30
        if (!changedEnough && now - lastAwareRttReportAtMs < 4_000L) return@LaunchedEffect
        if (now - lastAwareRttReportAtMs < 1_200L) return@LaunchedEffect
        val localAware = appViewModel.isWifiAwareSupportedLocally()
        val localDirect = appViewModel.isWifiDirectSupportedLocally()
        appViewModel.sendCallHandshake(
            targetPeerIdHex = peerId,
            action = CallHandshakeAction.ACK,
            callerName = profileState.name.ifBlank { "생존자" },
            wifiAwareSupported = localAware,
            wifiDirectSupported = localDirect,
            useOpus = localOpusSupported,
            state = CallHandshakeState.AWARE_OK,
            rttCm = rttCm
        )
        lastAwareRttReportCm = rttCm
        lastAwareRttReportAtMs = now
    }

    fun acceptIncomingCall(peerId: String): Boolean {
        if (!appViewModel.ensureWifiAwarePermissions()) return false
        val localAware = appViewModel.isWifiAwareSupportedLocally()
        val localDirect = appViewModel.isWifiDirectSupportedLocally()
        val peerAware = appState.incomingCallWifiAware
        val peerDirect = appState.incomingCallWifiDirect
        appViewModel.sendCallHandshake(
            targetPeerIdHex = peerId,
            action = CallHandshakeAction.ACK,
            callerName = profileState.name.ifBlank { "생존자" },
            wifiAwareSupported = localAware,
            wifiDirectSupported = localDirect,
            useOpus = localOpusSupported
        )
        val useOpus = localOpusSupported && appState.incomingCallUseOpus
        val started = callViewModel.startRealTimeCall(
            survivor = SurvivorProfile(
                name = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자",
                isWifiAware = peerAware,
                isWifiDirect = peerDirect,
                isUwb = appState.survivors.firstOrNull { it.peerId == peerId }?.isUwb ?: false,
                peerId = peerId
            ),
            localWifiAwareSupported = localAware,
            localWifiDirectSupported = localDirect,
            peerWifiAwareSupported = peerAware,
            peerWifiDirectSupported = peerDirect,
            isServer = false,
            useOpus = useOpus,
            targetDirectAddress = appState.incomingCallDirectAddress,
            localPeerId = myPeerId,
            targetPeerId = peerId
        )
        if (!started) {
            Toast.makeText(context, "통화 연결 실패", Toast.LENGTH_SHORT).show()
            appViewModel.sendCallHandshake(
                targetPeerIdHex = peerId,
                action = CallHandshakeAction.END,
                callerName = profileState.name.ifBlank { "생존자" },
                wifiAwareSupported = localAware,
                wifiDirectSupported = localDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearIncomingCall(peerId)
            connectingTargetPeerId = null
            return false
        }
        connectingTargetPeerId = peerId
        appViewModel.clearIncomingCall(peerId)
        return true
    }

    LaunchedEffect(appState.incomingCallPeerId) {
        if (appState.incomingCallPeerId == null) {
            autoAcceptedPeerId = null
        }
    }

    LaunchedEffect(appState.incomingCallPeerId, isInCall, backStackEntry) {
        val peerId = appState.incomingCallPeerId ?: return@LaunchedEffect
        if (isInCall || autoAcceptedPeerId == peerId) return@LaunchedEffect
        val currentRoute = backStackEntry?.destination?.route
        if (currentRoute != AppRoute.SurvivorPTT.route) return@LaunchedEffect
        if (acceptIncomingCall(peerId)) {
            autoAcceptedPeerId = peerId
        }
    }
    LaunchedEffect(Unit) {
        appViewModel.remotePowerSaveExitEvents.collect { token ->
            forceExitPowerSavingToken = token
        }
    }
    LaunchedEffect(Unit) {
        appViewModel.remotePowerSaveSetEvents.collect { enabled ->
            forceSetPowerSavingEnabled = enabled
            forceSetPowerSavingToken = System.currentTimeMillis()
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
                    sttResetToken = sttResetToken,
                    sttEnabled = sttEnabled,
                    onSos = { autoTriggered ->
                        if (autoTriggered) {
                            triggerAutoSosFeedback()
                            onPulseRescueSignal()
                        }
                        pendingSosNavigation = true
                        sosStartedAt = System.currentTimeMillis()
                        navController.navigate(AppRoute.SurvivorEmergency.route)
                    },
                    onSettings = {
                        // Standby 설정은 독립 라우트로 열어 시스템 뒤로가기로 Standby로 복귀되게 한다.
                        pendingSosNavigation = false
                        navController.navigate(AppRoute.SurvivorStandbySettings.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(AppRoute.SurvivorStandbySettings.route) {
                SettingsScreen(
                    isVoiceOn = isVoiceDetectionEnabled,
                    isShockOn = isShockDetectionEnabled,
                    profileName = profileState.name,
                    profileGender = profileState.gender,
                    profileBirthDate = profileState.birthDate,
                    profileNotes = profileState.notes,
                    onVoiceToggle = onSetVoiceDetection,
                    onShockToggle = onSetShockDetection,
                    onBack = { navController.popBackStack() },
                    onEditProfile = {
                        navController.navigate(AppRoute.SurvivorProfile.route)
                    }
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
                val pendingRequest = appState.incomingCallPeerId?.let {
                    SurvivorCallRequest(
                        callerName = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자",
                        wifiAware = appState.incomingCallWifiAware,
                        wifiDirect = appState.incomingCallWifiDirect,
                        useOpus = appState.incomingCallUseOpus
                    )
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
                    isCallConnected = activeCallTransportReady,
                    isInCall = isInCall,
                    callPeerName = targetSurvivor?.name,
                    pendingCall = pendingRequest,
                    forceExitPowerSavingToken = forceExitPowerSavingToken,
                    forceSetPowerSavingToken = forceSetPowerSavingToken,
                    forceSetPowerSavingEnabled = forceSetPowerSavingEnabled,
                    onBack = { navController.popBackStack() },
                    onDisconnect = {
                        if (isInCall) {
                            val targetPeerId = targetSurvivor?.peerId ?: appState.callPeerId.orEmpty()
                            if (targetPeerId.isNotBlank()) {
                                val localAware = appViewModel.isWifiAwareSupportedLocally()
                                val localDirect = appViewModel.isWifiDirectSupportedLocally()
                                appViewModel.sendCallHandshake(
                                    targetPeerIdHex = targetPeerId,
                                    action = CallHandshakeAction.END,
                                    callerName = profileState.name.ifBlank { "생존자" },
                                    wifiAwareSupported = localAware,
                                    wifiDirectSupported = localDirect,
                                    useOpus = localOpusSupported
                                )
                                appViewModel.clearLocalCallState(targetPeerId)
                            }
                            callViewModel.endCall()
                            connectingTargetPeerId = null
                        }
                        onDisconnect()
                        navController.navigate(AppRoute.SurvivorStandby.route) {
                            popUpTo(AppRoute.SurvivorStandby.route) { inclusive = true }
                        }
                    },
                    onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                    onPanicClear = onClearDeviceMonitoring,
                    onPowerSavingChanged = { enabled ->
                        appViewModel.updateLocalPowerSavingState(enabled)
                    },
                    onSettings = { navigateBottomTab(AppRoute.Settings.route) },
                    onAcceptCall = {
                        val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                        acceptIncomingCall(peerId)
                    },
                    onDeclineCall = {
                        val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                        val localAware = appViewModel.isWifiAwareSupportedLocally()
                        val localDirect = appViewModel.isWifiDirectSupportedLocally()
                        appViewModel.sendCallHandshake(
                            targetPeerIdHex = peerId,
                            action = CallHandshakeAction.END,
                            callerName = profileState.name.ifBlank { "생존자" },
                            wifiAwareSupported = localAware,
                            wifiDirectSupported = localDirect,
                            useOpus = localOpusSupported
                        )
                        appViewModel.clearIncomingCall(peerId)
                    },
                    onEndCall = {
                        val targetPeerId = targetSurvivor?.peerId ?: appState.callPeerId.orEmpty()
                        if (targetPeerId.isNotBlank()) {
                            val localAware = appViewModel.isWifiAwareSupportedLocally()
                            val localDirect = appViewModel.isWifiDirectSupportedLocally()
                            appViewModel.sendCallHandshake(
                                targetPeerIdHex = targetPeerId,
                                action = CallHandshakeAction.END,
                                callerName = profileState.name.ifBlank { "생존자" },
                                wifiAwareSupported = localAware,
                                wifiDirectSupported = localDirect,
                                useOpus = localOpusSupported
                            )
                            appViewModel.clearLocalCallState(targetPeerId)
                        }
                        callViewModel.endCall()
                        connectingTargetPeerId = null
                    }
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    isVoiceOn = isVoiceDetectionEnabled,
                    isShockOn = isShockDetectionEnabled,
                    profileName = profileState.name,
                    profileGender = profileState.gender,
                    profileBirthDate = profileState.birthDate,
                    profileNotes = profileState.notes,
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
                    onPrev = { navController.popBackStack() },
                    onSettings = { navigateBottomTab(AppRoute.Settings.route) },
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
