package com.example.lifesaiver.ui.navigation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifesaiver.core.audio.RealtimeAudioStreamEngine
import com.example.lifesaiver.core.call.RealTimeCallManager
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.core.service.CallAudioService
import com.example.lifesaiver.core.ble.BleRSSILocating
import com.example.lifesaiver.core.location.HybridDistanceManager
import com.example.lifesaiver.core.wifi.WifiAwareRanger
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.screen.CallViewModel
import com.example.lifesaiver.presentation.screen.DistanceViewModel
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.protocol.model.CallHandshakeAction
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
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
import com.example.lifesaiver.ui.screen.survivor.ptt.SurvivorCallRequest
import com.example.lifesaiver.wakeup.SensorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

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
    val activity = context as? Activity
    val appContext = context.applicationContext
    val appViewModel: AppViewModel = viewModel(
        viewModelStoreOwner = context as ViewModelStoreOwner
    )
    val profileStore = remember(context) { ProfileStore(context) }
    val profileState by profileStore.profileFlow.collectAsState(initial = SurvivorProfile())
    val appState by appViewModel.uiState.collectAsState()
    var pendingSosNavigation by remember { mutableStateOf(false) }
    var sosStartedAt by remember { mutableStateOf(0L) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var sttEnabled by remember { mutableStateOf(false) }
    val minSosDurationMs = 1_000L
    val audioEngine = remember(appContext) { RealtimeAudioStreamEngine(appContext) }
    val localOpusSupported = remember(audioEngine) { audioEngine.isOpusSupported() }
    val callManager = remember(appViewModel) {
        RealTimeCallManager(
            audioEngine,
            appViewModel.wifiAwareRanger,
            appViewModel.wifiDirectRanger
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
    val pendingTarget by callViewModel.pendingTarget.collectAsState()
    var selectedTargetPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    val distanceViewModelFactory = remember(appContext) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val wifiRanger = WifiAwareRanger(appContext)
                val bleLocating = BleRSSILocating(appContext, targetPeerIdHex = null)
                val hybridManager = HybridDistanceManager(appContext, bleLocating, wifiRanger)
                return DistanceViewModel(hybridManager) as T
            }
        }
    }
    val distanceViewModel: DistanceViewModel = viewModel(
        viewModelStoreOwner = context as ViewModelStoreOwner,
        factory = distanceViewModelFactory
    )

    fun startCallAudioService() {
        val intent = Intent(appContext, CallAudioService::class.java).apply {
            action = CallAudioService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stopCallAudioService() {
        val intent = Intent(appContext, CallAudioService::class.java).apply {
            action = CallAudioService.ACTION_STOP
        }
        appContext.startService(intent)
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
        val route = backStackEntry?.destination?.route ?: AppRoute.RescuerStandby.route
        onRouteChanged(route)
        if (route == AppRoute.SurvivorStandby.route) {
            // 센서 트리거로 들어온 경우에만 STT를 켜기 위해 reset token만 갱신
            sttResetToken = System.currentTimeMillis()
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
            val isRescuerRoute = currentRoute == AppRoute.RescuerStandby.route ||
                currentRoute == AppRoute.RescuerPTT.route ||
                currentRoute == AppRoute.RescuerChat.route ||
                currentRoute == AppRoute.RescuerEmergency.route ||
                currentRoute == AppRoute.RescuerSurvivorDb.route
            val targetRoute = if (isRescuerRoute) {
                AppRoute.RescuerEmergency.route
            } else {
                AppRoute.SurvivorEmergency.route
            }
            if (!pendingSosNavigation) {
                pendingSosNavigation = true
                sosStartedAt = System.currentTimeMillis()
            }
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    if (currentRoute == AppRoute.SurvivorPTT.route ||
                        currentRoute == AppRoute.RescuerPTT.route
                    ) {
                        popUpTo(currentRoute) { inclusive = true }
                    } else {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedTargetPeerId) {
        distanceViewModel.setTargetPeerId(selectedTargetPeerId)
    }

    LaunchedEffect(appState.survivors) {
        val selected = selectedTargetPeerId
        if (selected != null && appState.survivors.none { it.peerId == selected }) {
            selectedTargetPeerId = null
        }
    }

    LaunchedEffect(isInCall) {
        if (isInCall) {
            appViewModel.refreshPermissions()
            if (!appState.hasPermissions) {
                appViewModel.onPermissionsResult(false)
                return@LaunchedEffect
            }
            startCallAudioService()
        } else {
            stopCallAudioService()
        }
    }

    LaunchedEffect(appState.callPeerId, isInCall) {
        if (appState.callPeerId == null && isInCall) {
            callViewModel.endCall()
        }
    }

    LaunchedEffect(
        appState.callPeerWifiAware,
        appState.callPeerWifiDirect,
        appState.callPeerUseOpus,
        appState.callPeerId,
        pendingTarget,
        isInCall
    ) {
        val pending = pendingTarget ?: return@LaunchedEffect
        if (isInCall) return@LaunchedEffect
        val peerId = appState.callPeerId ?: return@LaunchedEffect
        if (peerId != pending.peerId) return@LaunchedEffect
        if (!appViewModel.ensureWifiAwarePermissions()) return@LaunchedEffect
        val peerAware = appState.callPeerWifiAware ?: pending.isWifiAware
        val peerDirect = appState.callPeerWifiDirect ?: pending.isWifiDirect
        val localAware = appViewModel.isWifiAwareSupportedLocally()
        val localDirect = appViewModel.isWifiDirectSupportedLocally()
        val useOpus = localOpusSupported && (appState.callPeerUseOpus ?: false)
        val started = callViewModel.startRealTimeCall(
            survivor = pending,
            localWifiAwareSupported = localAware,
            localWifiDirectSupported = localDirect,
            peerWifiAwareSupported = peerAware,
            peerWifiDirectSupported = peerDirect,
            isServer = true,
            useOpus = useOpus
        )
        if (!started) {
            Toast.makeText(context, "Wi-Fi Direct 미지원: 통화 불가", Toast.LENGTH_SHORT).show()
        }
        callViewModel.clearPendingCall()
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.RescuerStandby.route
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
                onPrev = stopAndBack
            )
        }

        composable(AppRoute.SurvivorPTT.route) {
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            val pendingRequest = appState.incomingCallPeerId?.let { peerId ->
                val name = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자"
                SurvivorCallRequest(
                    callerName = name,
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
                isCallConnected = appState.isCallConnected,
                isInCall = isInCall,
                callPeerName = targetSurvivor?.name,
                pendingCall = pendingRequest,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    if (isInCall) {
                        val targetPeerId = targetSurvivor?.peerId.orEmpty()
                        if (targetPeerId.isNotBlank()) {
                            val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                            appViewModel.sendCallHandshake(
                                targetPeerIdHex = targetPeerId,
                                action = CallHandshakeAction.END,
                                callerName = profileState.name.ifBlank { "생존자" },
                                wifiAwareSupported = localWifiAware,
                                wifiDirectSupported = localWifiDirect,
                                useOpus = localOpusSupported
                            )
                        }
                        callViewModel.endCall()
                    }
                    onDisconnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                },
                onChat = { navController.navigate(AppRoute.SurvivorChat.route) },
                onProfile = { navController.navigate(AppRoute.SurvivorProfile.route) },
                onPanicClear = onClearDeviceMonitoring,
                onAcceptCall = {
                    val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                    if (!appViewModel.ensureWifiAwarePermissions()) return@PTTLinkScreen
                    val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                    val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                    val peerWifiAware = appState.incomingCallWifiAware
                    val peerWifiDirect = appState.incomingCallWifiDirect
                    val canUseDirect = localWifiDirect && peerWifiDirect
                    if (!canUseDirect) {
                        Toast.makeText(context, "통화 불가: Wi-Fi Direct 미지원", Toast.LENGTH_SHORT).show()
                        appViewModel.sendCallHandshake(
                            targetPeerIdHex = peerId,
                            action = CallHandshakeAction.END,
                            callerName = profileState.name.ifBlank { "생존자" },
                            wifiAwareSupported = localWifiAware,
                            wifiDirectSupported = localWifiDirect,
                            useOpus = localOpusSupported
                        )
                        appViewModel.clearIncomingCall(peerId)
                        return@PTTLinkScreen
                    }
                    appViewModel.sendCallHandshake(
                        targetPeerIdHex = peerId,
                        action = CallHandshakeAction.ACK,
                        callerName = profileState.name.ifBlank { "생존자" },
                        wifiAwareSupported = localWifiAware,
                        wifiDirectSupported = localWifiDirect,
                        useOpus = localOpusSupported
                    )
                    val useOpus = localOpusSupported && appState.incomingCallUseOpus
                    val started = callViewModel.startRealTimeCall(
                        survivor = SurvivorProfile(
                            name = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자",
                            isWifiAware = peerWifiAware,
                            isWifiDirect = peerWifiDirect,
                            peerId = peerId
                        ),
                        localWifiAwareSupported = localWifiAware,
                        localWifiDirectSupported = localWifiDirect,
                        peerWifiAwareSupported = peerWifiAware,
                        peerWifiDirectSupported = peerWifiDirect,
                        isServer = false,
                        useOpus = useOpus
                    )
                    if (!started) {
                        Toast.makeText(context, "통화 연결 실패", Toast.LENGTH_SHORT).show()
                        appViewModel.sendCallHandshake(
                            targetPeerIdHex = peerId,
                            action = CallHandshakeAction.END,
                            callerName = profileState.name.ifBlank { "생존자" },
                            wifiAwareSupported = localWifiAware,
                            wifiDirectSupported = localWifiDirect,
                            useOpus = localOpusSupported
                        )
                        appViewModel.clearIncomingCall(peerId)
                    }
                },
                onDeclineCall = {
                    val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                    val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                    val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                    appViewModel.sendCallHandshake(
                        targetPeerIdHex = peerId,
                        action = CallHandshakeAction.END,
                        callerName = profileState.name.ifBlank { "생존자" },
                        wifiAwareSupported = localWifiAware,
                        wifiDirectSupported = localWifiDirect,
                        useOpus = localOpusSupported
                    )
                    appViewModel.clearIncomingCall(peerId)
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

        composable(AppRoute.RescuerStandby.route) {
            RescuerStandbyScreen(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                connectedCount = connectedCount,
                onPrev = { activity?.finish() },
                onGoPTT = { navController.navigate(AppRoute.RescuerPTT.route) },
                onSos = { navController.navigate(AppRoute.RescuerEmergency.route) }
            )
        }

        composable(AppRoute.RescuerPTT.route) {
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            val distanceState by distanceViewModel.uiState.collectAsState()
            DisposableEffect(Unit) {
                distanceViewModel.start()
                onDispose {
                    distanceViewModel.stop()
                }
            }
            RescuerPTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                isConnected = isConnected,
                isMicOn = isMicOn,
                distanceMeters = distanceState.distanceMeters,
                distanceTrend = distanceState.trend,
                isPrecisionMode = distanceState.isPrecisionMode,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    if (isInCall) {
                        val targetPeerId = targetSurvivor?.peerId.orEmpty()
                        if (targetPeerId.isNotBlank()) {
                            val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                            appViewModel.sendCallHandshake(
                                targetPeerIdHex = targetPeerId,
                                action = CallHandshakeAction.END,
                                callerName = profileState.name.ifBlank { "구조자" },
                                wifiAwareSupported = localWifiAware,
                                wifiDirectSupported = localWifiDirect,
                                useOpus = localOpusSupported
                            )
                        }
                        callViewModel.endCall()
                    }
                    onDisconnect()
                    navController.navigate(AppRoute.RescuerStandby.route)
                },
                onChat = { navController.navigate(AppRoute.RescuerChat.route) },
                onOpenSurvivorDb = { navController.navigate(AppRoute.RescuerSurvivorDb.route) },
                onPanicClear = onClearDeviceMonitoring
            )
        }

        composable(AppRoute.RescuerSurvivorDb.route) {
            RescuerSurvivorDbScreen(
                survivors = appState.survivors,
                onBack = { navController.popBackStack() },
                onCallClick = { survivor ->
                    val currentPeerId = targetSurvivor?.peerId.orEmpty()
                    if (isInCall) {
                        if (currentPeerId.isNotBlank() && currentPeerId != survivor.peerId) {
                            val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                            appViewModel.sendCallHandshake(
                                targetPeerIdHex = currentPeerId,
                                action = CallHandshakeAction.END,
                                callerName = profileState.name.ifBlank { "구조자" },
                                wifiAwareSupported = localWifiAware,
                                wifiDirectSupported = localWifiDirect,
                                useOpus = localOpusSupported
                            )
                            callViewModel.endCall()
                        } else {
                            Toast.makeText(context, "이미 통화 중입니다.", Toast.LENGTH_SHORT).show()
                            return@RescuerSurvivorDbScreen
                        }
                    }
                    if (!appViewModel.ensureWifiAwarePermissions()) return@RescuerSurvivorDbScreen
                    val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                    val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                    val targetPeerId = survivor.peerId.ifBlank { directPeerIds.firstOrNull().orEmpty() }
                    if (targetPeerId.isBlank()) {
                        Toast.makeText(context, "연결된 BLE 피어가 없습니다.", Toast.LENGTH_SHORT).show()
                        return@RescuerSurvivorDbScreen
                    }
                    val hasBleLink = directPeerIds.contains(targetPeerId)
                    val peerWifiAware = survivor.isWifiAware || hasBleLink
                    val peerWifiDirect = survivor.isWifiDirect || hasBleLink
                    val canUseDirect = localWifiDirect && peerWifiDirect
                    if (!canUseDirect) {
                        Toast.makeText(context, "Wi-Fi Direct 미지원: 통화 불가", Toast.LENGTH_SHORT).show()
                        return@RescuerSurvivorDbScreen
                    }
                    appViewModel.sendCallHandshake(
                        targetPeerIdHex = targetPeerId,
                        action = CallHandshakeAction.START,
                        callerName = profileState.name.ifBlank { "구조자" },
                        wifiAwareSupported = localWifiAware,
                        wifiDirectSupported = localWifiDirect,
                        useOpus = localOpusSupported
                    )
                    callViewModel.requestCall(
                        survivor.copy(
                            peerId = targetPeerId,
                            isWifiAware = peerWifiAware,
                            isWifiDirect = peerWifiDirect
                        )
                    )
                    Toast.makeText(context, "통화 요청 전송됨", Toast.LENGTH_SHORT).show()
                },
                onEndCall = {
                    val targetPeerId = targetSurvivor?.peerId.orEmpty()
                    if (targetPeerId.isNotBlank()) {
                        val localWifiAware = appViewModel.isWifiAwareSupportedLocally()
                        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                        appViewModel.sendCallHandshake(
                            targetPeerIdHex = targetPeerId,
                            action = CallHandshakeAction.END,
                            callerName = profileState.name.ifBlank { "구조자" },
                            wifiAwareSupported = localWifiAware,
                            wifiDirectSupported = localWifiDirect,
                            useOpus = localOpusSupported
                        )
                    }
                    callViewModel.endCall()
                },
                selectedTargetPeerId = selectedTargetPeerId,
                onSelectTarget = { survivor ->
                    selectedTargetPeerId = if (selectedTargetPeerId == survivor.peerId) {
                        null
                    } else {
                        survivor.peerId
                    }
                },
                activeSurvivor = targetSurvivor,
                isInCall = isInCall
            )
        }

        composable(AppRoute.RescuerChat.route) {
            RescuerChatScreen(
                roomTitle = "전체 채팅",
                meshPeerCount = meshPeerCount,
                messages = messages,
                signatureLogs = signatureLogs,
                profileLogs = profileLogs,
                onClearSignatureLogs = onClearSignatureLogs,
                onClearProfileLogs = onClearProfileLogs,
                onSendProfileTest = onSendProfileTest,
                onPrev = { navController.popBackStack() },
                onSend = onSendMessage
            )
        }

        composable(AppRoute.RescuerEmergency.route) {
            val stopAndBack = {
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
            RescuerEmergencyBeaconScreen(
                batteryLevel = batteryLevel,
                onPrev = stopAndBack
            )
        }
    }
}
