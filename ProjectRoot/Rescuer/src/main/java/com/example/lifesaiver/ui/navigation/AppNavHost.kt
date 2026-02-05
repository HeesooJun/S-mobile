package com.example.lifesaiver.ui.navigation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.snapshotFlow
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
import com.example.lifesaiver.core.call.CallTransportType
import com.example.lifesaiver.core.call.RealTimeCallManager
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.core.profile.ProfileStore
import com.example.lifesaiver.core.profile.SurvivorProfile
import com.example.lifesaiver.core.service.CallAudioService
import com.example.lifesaiver.core.ble.BleRSSILocating
import com.example.lifesaiver.core.location.DistanceMeasurementSource
import com.example.lifesaiver.core.location.HybridDistanceManager
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.screen.CallViewModel
import com.example.lifesaiver.presentation.screen.DistanceViewModel
import com.example.lifesaiver.presentation.screen.EmergencyBeaconViewModel
import com.example.lifesaiver.presentation.screen.RescueChatViewModel
import com.example.lifesaiver.protocol.model.CallHandshakeAction
import com.example.lifesaiver.protocol.model.CallHandshakeState
import com.example.lifesaiver.protocol.model.DeviceControlCommand
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.ui.screen.survivor.ptt.PTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.chat.RescuerChatScreen
import com.example.lifesaiver.ui.screen.rescuer.db.RescuerSurvivorDbScreen
import com.example.lifesaiver.ui.screen.rescuer.emergency.EmergencyBeaconScreen as RescuerEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.rescuer.mesh.RescuerMeshMapScreen
import com.example.lifesaiver.ui.screen.rescuer.ptt.RssiFeedbackLevel
import com.example.lifesaiver.ui.screen.rescuer.ptt.RssiFeedbackMode
import com.example.lifesaiver.ui.screen.rescuer.ptt.RescuerPTTLinkScreen
import com.example.lifesaiver.ui.screen.rescuer.standby.RescuerStandbyScreen
import com.example.lifesaiver.ui.screen.survivor.standby.StandbyStatusScreen
import com.example.lifesaiver.ui.screen.survivor.chat.RescueChatScreen
import com.example.lifesaiver.ui.screen.survivor.emergency.EmergencyBeaconScreen as SurvivorEmergencyBeaconScreen
import com.example.lifesaiver.ui.screen.survivor.profile.SurvivorProfileScreen
import com.example.lifesaiver.ui.screen.survivor.ptt.SurvivorCallRequest
import com.example.lifesaiver.wakeup.SensorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.pow
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
    var pendingSosRoute by remember { mutableStateOf<String?>(null) }
    var sosStartedAt by remember { mutableStateOf(0L) }
    var sttResetToken by remember { mutableStateOf(0L) }
    var sttEnabled by remember { mutableStateOf(false) }
    var autoAcceptedPeerId by remember { mutableStateOf<String?>(null) }
    var awareReadyAckPeerId by remember { mutableStateOf<String?>(null) }
    var callingTargetPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var callAttemptStartedAtMs by rememberSaveable { mutableStateOf(0L) }
    var pendingNavigateRoute by remember { mutableStateOf<String?>(null) }
    val callAttemptTimeoutMs = 15_000L
    val minSosDurationMs = 1_000L
    val audioEngine = remember(appContext) { RealtimeAudioStreamEngine(appContext) }
    val localOpusSupported = remember(audioEngine) { audioEngine.isOpusSupported() }
    val forceDirectOnly = false
    val autoAcceptCalls = true
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
    val callDebugState by callViewModel.debugState.collectAsState()
    val pendingTarget by callViewModel.pendingTarget.collectAsState()
    var selectedTargetPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var directChatPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTargetSurvivor by remember { mutableStateOf<SurvivorProfile?>(null) }
    var knownSurvivorPeerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var survivorSignalTrackingReady by remember { mutableStateOf(false) }
    var lastUwbSyncRequestAtMs by rememberSaveable { mutableStateOf(0L) }
    var lastAwareRttReportAtMs by rememberSaveable { mutableStateOf(0L) }
    var lastAwareRttReportCm by rememberSaveable { mutableStateOf<Int?>(null) }
    var rssiFeedbackMode by rememberSaveable { mutableStateOf(RssiFeedbackMode.OFF.name) }
    var rssiFeedbackLevel by rememberSaveable { mutableStateOf(RssiFeedbackLevel.MEDIUM.name) }
    var lastRssiGuidanceAtMs by rememberSaveable { mutableStateOf(0L) }
    val rssiToneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 95) }
    fun resolvePeerSupportsUwb(peerId: String?): Boolean {
        if (peerId.isNullOrBlank()) return false
        return appState.survivors.firstOrNull { it.peerId == peerId }?.isUwb == true
    }
    fun resolvePeerSupportsAware(peerId: String?): Boolean {
        if (peerId.isNullOrBlank()) return false
        val fromSurvivor = appState.survivors.firstOrNull { it.peerId == peerId }?.isWifiAware == true
        return fromSurvivor || (appState.callPeerId == peerId && appState.callPeerWifiAware == true)
    }
    val distanceTargetPeerId = targetSurvivor?.peerId ?: selectedTargetPeerId ?: selectedTargetSurvivor?.peerId
    val distanceTargetSupportsUwb = resolvePeerSupportsUwb(distanceTargetPeerId)
    val currentRoute = backStackEntry?.destination?.route
    val shouldRunDistanceTracking = isInCall ||
        currentRoute == AppRoute.RescuerPTT.route ||
        currentRoute == AppRoute.RescuerSurvivorDb.route
    val distanceViewModelFactory = remember(appContext) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val bleLocating = BleRSSILocating(appContext, targetPeerIdHex = null)
                val hybridManager = HybridDistanceManager(
                    context = appContext,
                    bleLocating = bleLocating,
                    wifiRanger = appViewModel.wifiAwareRanger,
                    uwbRanger = appViewModel.uwbRanger
                )
                return DistanceViewModel(hybridManager) as T
            }
        }
    }
    val distanceViewModel: DistanceViewModel = viewModel(
        viewModelStoreOwner = context as ViewModelStoreOwner,
        factory = distanceViewModelFactory
    )
    LaunchedEffect(Unit) {
        // Keep RTT enabled in both idle and in-call states.
        appViewModel.wifiAwareRanger.setRttEnabled(true)
    }
    LaunchedEffect(shouldRunDistanceTracking) {
        appViewModel.setBleRssiActiveMode(shouldRunDistanceTracking)
        if (shouldRunDistanceTracking) {
            distanceViewModel.start()
        } else {
            distanceViewModel.stop()
        }
    }
    LaunchedEffect(isConnected, meshPeerCount, isInCall) {
        if (!isConnected && meshPeerCount <= 0 && !isInCall) {
            rssiFeedbackMode = RssiFeedbackMode.OFF.name
            rssiFeedbackLevel = RssiFeedbackLevel.MEDIUM.name
            lastRssiGuidanceAtMs = 0L
        }
    }
    val navigateSingleRoute: (String) -> Unit = nav@{ targetRoute ->
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (pendingNavigateRoute != null || currentRoute == targetRoute) return@nav
        pendingNavigateRoute = targetRoute
        if (!navController.popBackStack(targetRoute, inclusive = false)) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }
    fun resetRssiFeedbackDefaults() {
        rssiFeedbackMode = RssiFeedbackMode.OFF.name
        rssiFeedbackLevel = RssiFeedbackLevel.MEDIUM.name
        lastRssiGuidanceAtMs = 0L
    }
    fun sendRemoteStopIfNeeded(peerId: String?) {
        if (peerId.isNullOrBlank()) return
        appViewModel.sendDeviceControl(
            targetPeerIdHex = peerId,
            command = DeviceControlCommand.STOP_ALERTS,
            durationMs = 300,
            intensity = 0
        )
    }
    fun endCurrentRescuerCall() {
        val targetPeerId = targetSurvivor?.peerId ?: appState.callPeerId.orEmpty()
        if (targetPeerId.isNotBlank()) {
            sendRemoteStopIfNeeded(targetPeerId)
            val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
            appViewModel.sendCallHandshake(
                targetPeerIdHex = targetPeerId,
                action = CallHandshakeAction.END,
                callerName = profileState.name.ifBlank { "구조자" },
                wifiAwareSupported = localWifiAware,
                wifiDirectSupported = localWifiDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearLocalCallState(targetPeerId)
        }
        callViewModel.endCall()
        callingTargetPeerId = null
        callAttemptStartedAtMs = 0L
        resetRssiFeedbackDefaults()
    }
    fun requestRescuerCall(survivor: SurvivorProfile) {
        if (callingTargetPeerId != null) {
            val pendingPeerId = callingTargetPeerId.orEmpty()
            if (pendingPeerId == survivor.peerId) {
                Toast.makeText(context, "통화 연결 시도 중입니다.", Toast.LENGTH_SHORT).show()
                return
            }
            if (pendingPeerId.isNotBlank()) {
                val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
                val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                appViewModel.sendCallHandshake(
                    targetPeerIdHex = pendingPeerId,
                    action = CallHandshakeAction.END,
                    callerName = profileState.name.ifBlank { "구조자" },
                    wifiAwareSupported = localWifiAware,
                    wifiDirectSupported = localWifiDirect,
                    useOpus = localOpusSupported
                )
                appViewModel.clearLocalCallState(pendingPeerId)
            }
            callViewModel.clearPendingCall()
            callingTargetPeerId = null
            callAttemptStartedAtMs = 0L
        }
        val currentPeerId = targetSurvivor?.peerId.orEmpty()
        if (isInCall) {
            if (currentPeerId.isNotBlank() && currentPeerId != survivor.peerId) {
                endCurrentRescuerCall()
            } else {
                Toast.makeText(context, "이미 통화 중입니다.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (!appViewModel.ensureWifiAwarePermissions()) return
        val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
        val targetPeerId = survivor.peerId.ifBlank { directPeerIds.firstOrNull().orEmpty() }
        val peerSupportsUwb = resolvePeerSupportsUwb(targetPeerId)
        if (!peerSupportsUwb) {
            appViewModel.stopUwbSession()
            lastUwbSyncRequestAtMs = 0L
        }
        if (targetPeerId.isBlank()) {
            Toast.makeText(context, "연결된 BLE 피어가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        selectedTargetPeerId = targetPeerId
        val hasBleLink = directPeerIds.contains(targetPeerId)
        val peerWifiAware = if (forceDirectOnly) false else survivor.isWifiAware || hasBleLink
        val peerDirectAddress = appState.peerDirectAddresses[targetPeerId]
        val peerWifiDirect = survivor.isWifiDirect || hasBleLink || !peerDirectAddress.isNullOrBlank()
        val canUseAware = localWifiAware && peerWifiAware
        val canUseDirect = localWifiDirect && peerWifiDirect
        if (!canUseAware && !canUseDirect) {
            Toast.makeText(context, "통화 불가: Aware/Direct 미지원", Toast.LENGTH_SHORT).show()
            return
        }
        val initialState = if (canUseAware) {
            CallHandshakeState.AWARE_TRY
        } else {
            CallHandshakeState.DIRECT_TRY
        }
        appViewModel.sendCallHandshake(
            targetPeerIdHex = targetPeerId,
            action = CallHandshakeAction.START,
            callerName = profileState.name.ifBlank { "구조자" },
            wifiAwareSupported = localWifiAware,
            wifiDirectSupported = localWifiDirect,
            useOpus = localOpusSupported,
            state = initialState
        )
        callViewModel.requestCall(
            survivor.copy(
                peerId = targetPeerId,
                isWifiAware = peerWifiAware,
                isWifiDirect = peerWifiDirect
            )
        )
        selectedTargetSurvivor = survivor.copy(
            peerId = targetPeerId,
            isWifiAware = peerWifiAware,
            isWifiDirect = peerWifiDirect
        )
        callingTargetPeerId = targetPeerId
        callAttemptStartedAtMs = System.currentTimeMillis()
        Toast.makeText(context, "통화 요청 전송됨", Toast.LENGTH_SHORT).show()
    }
    fun resolveSelectedSurvivorForCall(): SurvivorProfile? {
        val selectedPeerId = selectedTargetPeerId.orEmpty()
        if (selectedPeerId.isNotBlank()) {
            val selected = appState.survivors.firstOrNull { it.peerId == selectedPeerId }
            if (selected != null) return selected
            val cachedSelected = selectedTargetSurvivor
            if (cachedSelected != null && cachedSelected.peerId == selectedPeerId) {
                return cachedSelected
            }
            return null
        }
        selectedTargetSurvivor?.let { return it }
        return appState.survivors.singleOrNull()
    }

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
        if (route == pendingNavigateRoute) {
            pendingNavigateRoute = null
        }
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
            val targetRoute = pendingSosRoute ?: AppRoute.SurvivorPTT.route
            pendingSosRoute = null
            navigateSingleRoute(targetRoute)
        }
    }

    LaunchedEffect(isConnected, isRescueSignalActive, backStackEntry) {
        val currentRoute = backStackEntry?.destination?.route
        if (isRescueSignalActive && !isConnected) {
            val isRescuerRoute = currentRoute == AppRoute.RescuerStandby.route ||
                currentRoute == AppRoute.RescuerPTT.route ||
                currentRoute == AppRoute.RescuerChat.route ||
                currentRoute == AppRoute.RescuerEmergency.route ||
                currentRoute == AppRoute.RescuerSurvivorDb.route ||
                currentRoute == AppRoute.RescuerMeshMap.route
            val targetRoute = if (isRescuerRoute) {
                AppRoute.RescuerEmergency.route
            } else {
                AppRoute.SurvivorEmergency.route
            }
            if (!pendingSosNavigation) {
                pendingSosNavigation = true
                pendingSosRoute = if (isRescuerRoute) {
                    AppRoute.RescuerSurvivorDb.route
                } else {
                    AppRoute.SurvivorPTT.route
                }
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
    DisposableEffect(rssiToneGenerator) {
        onDispose {
            runCatching { rssiToneGenerator.release() }
        }
    }

    LaunchedEffect(appState.incomingCallPeerId, isInCall, backStackEntry) {
        val currentRoute = backStackEntry?.destination?.route
        val hasIncomingCall = appState.incomingCallPeerId != null
        val isRescuerRoute = currentRoute == AppRoute.RescuerStandby.route ||
            currentRoute == AppRoute.RescuerPTT.route ||
            currentRoute == AppRoute.RescuerChat.route ||
            currentRoute == AppRoute.RescuerEmergency.route ||
            currentRoute == AppRoute.RescuerSurvivorDb.route ||
            currentRoute == AppRoute.RescuerMeshMap.route
        if (hasIncomingCall && !isInCall && !isRescuerRoute) {
            if (currentRoute != AppRoute.SurvivorPTT.route) {
                navController.navigate(AppRoute.SurvivorPTT.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(distanceTargetPeerId) {
        distanceViewModel.setTargetPeerId(distanceTargetPeerId)
    }

    LaunchedEffect(callDebugState.activeTransport) {
        distanceViewModel.setActiveTransport(callDebugState.activeTransport)
    }

    LaunchedEffect(distanceTargetPeerId, distanceTargetSupportsUwb) {
        val localUwb = appViewModel.isUwbSupportedLocally()
        val hasTargetPeer = !distanceTargetPeerId.isNullOrBlank()
        // Keep UWB tracking path open while capability handshake catches up.
        val peerUwb = hasTargetPeer && (distanceTargetSupportsUwb || localUwb)
        distanceViewModel.setUwbCapability(localSupported = localUwb, peerSupported = peerUwb)
        if (!hasTargetPeer) {
            appViewModel.stopUwbSession()
            lastUwbSyncRequestAtMs = 0L
        }
    }

    LaunchedEffect(
        appState.survivors,
        backStackEntry?.destination?.route,
        isInCall,
        callingTargetPeerId
    ) {
        val selected = selectedTargetPeerId ?: return@LaunchedEffect
        appState.survivors.firstOrNull { it.peerId == selected }?.let { liveSelected ->
            selectedTargetSurvivor = liveSelected
            return@LaunchedEffect
        }
        val currentRoute = backStackEntry?.destination?.route
        val keepSelection =
            currentRoute == AppRoute.RescuerPTT.route ||
                isInCall ||
                callingTargetPeerId != null
        if (!keepSelection) {
            selectedTargetPeerId = null
            selectedTargetSurvivor = null
        }
    }

    LaunchedEffect(appState.survivors, backStackEntry?.destination?.route, isInCall) {
        val peerIds = appState.survivors
            .map { it.peerId }
            .filter { it.isNotBlank() }
            .toSet()
        if (!survivorSignalTrackingReady) {
            knownSurvivorPeerIds = peerIds
            survivorSignalTrackingReady = true
            return@LaunchedEffect
        }
        val hasNewSurvivorSignal = (peerIds - knownSurvivorPeerIds).isNotEmpty()
        knownSurvivorPeerIds = peerIds
        if (!hasNewSurvivorSignal || isInCall) return@LaunchedEffect
        val currentRoute = backStackEntry?.destination?.route
        val shouldAutoOpenDb = currentRoute == AppRoute.RescuerStandby.route ||
            currentRoute == AppRoute.RescuerPTT.route ||
            currentRoute == AppRoute.RescuerChat.route ||
            currentRoute == AppRoute.RescuerMeshMap.route
        if (shouldAutoOpenDb) {
            navigateSingleRoute(AppRoute.RescuerSurvivorDb.route)
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
            lastRssiGuidanceAtMs = 0L
        }
    }

    LaunchedEffect(
        appState.incomingCallPeerId,
        appState.incomingCallName,
        appState.incomingCallWifiAware,
        appState.incomingCallWifiDirect,
        appState.incomingCallUseOpus,
        appState.incomingCallDirectAddress,
        isInCall,
        appState.hasPermissions
    ) {
        if (!autoAcceptCalls) return@LaunchedEffect
        val peerId = appState.incomingCallPeerId ?: return@LaunchedEffect
        if (autoAcceptedPeerId == peerId || isInCall) return@LaunchedEffect
        val currentRoute = backStackEntry?.destination?.route
        if (currentRoute?.startsWith("survivor_") != true) return@LaunchedEffect
        val name = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자"
        val peerIsUwb = appState.survivors.firstOrNull { it.peerId == peerId }?.isUwb ?: false
        val profile = SurvivorProfile(
            name = name,
            isWifiAware = appState.incomingCallWifiAware,
            isWifiDirect = appState.incomingCallWifiDirect,
            isUwb = peerIsUwb,
            peerId = peerId
        )
        if (!appViewModel.ensureWifiAwarePermissions()) return@LaunchedEffect
        val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
        val canUseAware = localWifiAware && profile.isWifiAware
        val canUseDirect = localWifiDirect && profile.isWifiDirect
        if (!canUseAware && !canUseDirect) {
            Toast.makeText(context, "통화 불가: Aware/Direct 미지원", Toast.LENGTH_SHORT).show()
            appViewModel.sendCallHandshake(
                targetPeerIdHex = peerId,
                action = CallHandshakeAction.END,
                callerName = profileState.name.ifBlank { "생존자" },
                wifiAwareSupported = localWifiAware,
                wifiDirectSupported = localWifiDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearIncomingCall(peerId)
            return@LaunchedEffect
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
            survivor = profile,
            localWifiAwareSupported = localWifiAware,
            localWifiDirectSupported = localWifiDirect,
            peerWifiAwareSupported = if (forceDirectOnly) false else profile.isWifiAware,
            peerWifiDirectSupported = profile.isWifiDirect,
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
                wifiAwareSupported = localWifiAware,
                wifiDirectSupported = localWifiDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearIncomingCall(peerId)
            return@LaunchedEffect
        }
        autoAcceptedPeerId = peerId
        if (currentRoute != AppRoute.SurvivorPTT.route) {
            navigateSingleRoute(AppRoute.SurvivorPTT.route)
        }
    }

    LaunchedEffect(appState.incomingCallPeerId) {
        if (appState.incomingCallPeerId == null) {
            autoAcceptedPeerId = null
        }
    }

    LaunchedEffect(appState.callPeerId, isInCall) {
        if (appState.callPeerId == null && isInCall) {
            callViewModel.endCall()
            callingTargetPeerId = null
            callAttemptStartedAtMs = 0L
        }
    }

    LaunchedEffect(isInCall, callDebugState.activeTransport, callDebugState.lastDecision) {
        if (!isInCall) return@LaunchedEffect
        if (callDebugState.activeTransport != CallTransportType.NONE) return@LaunchedEffect
        if (callDebugState.lastDecision != "stopped") return@LaunchedEffect
        val peerId = targetSurvivor?.peerId ?: appState.callPeerId.orEmpty()
        if (peerId.isNotBlank()) {
            val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
            appViewModel.sendCallHandshake(
                targetPeerIdHex = peerId,
                action = CallHandshakeAction.END,
                callerName = profileState.name.ifBlank { "구조자" },
                wifiAwareSupported = localWifiAware,
                wifiDirectSupported = localWifiDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearLocalCallState(peerId)
        }
        Toast.makeText(context, "통화 세션이 종료되었습니다.", Toast.LENGTH_SHORT).show()
        callViewModel.endCall()
        callingTargetPeerId = null
        callAttemptStartedAtMs = 0L
        resetRssiFeedbackDefaults()
    }

    LaunchedEffect(isInCall, targetSurvivor?.peerId, callDebugState.activeTransport, callDebugState.wifiAware.isReady) {
        val peerId = targetSurvivor?.peerId
        if (!isInCall || peerId.isNullOrBlank()) {
            awareReadyAckPeerId = null
            return@LaunchedEffect
        }
        val awareReady =
            callDebugState.activeTransport == CallTransportType.WIFI_AWARE &&
                callDebugState.wifiAware.isReady
        if (!awareReady || awareReadyAckPeerId == peerId) return@LaunchedEffect
        val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
        appViewModel.sendCallHandshake(
            targetPeerIdHex = peerId,
            action = CallHandshakeAction.ACK,
            callerName = profileState.name.ifBlank { "구조자" },
            wifiAwareSupported = localWifiAware,
            wifiDirectSupported = localWifiDirect,
            useOpus = localOpusSupported,
            state = CallHandshakeState.AWARE_OK
        )
        awareReadyAckPeerId = peerId
    }

    LaunchedEffect(
        callingTargetPeerId,
        isInCall,
        appState.isCallConnected,
        targetSurvivor?.peerId,
        appState.callPeerId,
        backStackEntry?.destination?.route
    ) {
        val targetPeerId = callingTargetPeerId ?: return@LaunchedEffect
        val connected =
            isInCall &&
                appState.isCallConnected &&
                (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
        if (!connected) return@LaunchedEffect
        callingTargetPeerId = null
        callAttemptStartedAtMs = 0L
        val currentRoute = backStackEntry?.destination?.route
        if (currentRoute != AppRoute.RescuerPTT.route) {
            navigateSingleRoute(AppRoute.RescuerPTT.route)
        }
    }

    LaunchedEffect(callingTargetPeerId, callAttemptStartedAtMs) {
        val targetPeerId = callingTargetPeerId ?: return@LaunchedEffect
        val completedInTime = withTimeoutOrNull(callAttemptTimeoutMs) {
            snapshotFlow {
                val connected =
                    isInCall &&
                        appState.isCallConnected &&
                        (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
                val canceled = callingTargetPeerId != targetPeerId
                connected || canceled
            }.first { done -> done }
        } != null
        val connectedNow =
            isInCall &&
                appState.isCallConnected &&
                (targetSurvivor?.peerId == targetPeerId || appState.callPeerId == targetPeerId)
        if (callingTargetPeerId != targetPeerId || connectedNow || completedInTime) {
            return@LaunchedEffect
        }
        val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
        appViewModel.sendCallHandshake(
            targetPeerIdHex = targetPeerId,
            action = CallHandshakeAction.END,
            callerName = profileState.name.ifBlank { "구조자" },
            wifiAwareSupported = localWifiAware,
            wifiDirectSupported = localWifiDirect,
            useOpus = localOpusSupported
        )
        appViewModel.clearLocalCallState(targetPeerId)
        callViewModel.clearPendingCall()
        callViewModel.endCall()
        callingTargetPeerId = null
        callAttemptStartedAtMs = 0L
        resetRssiFeedbackDefaults()
        Toast.makeText(context, "통화 연결 시간 초과(15초)", Toast.LENGTH_SHORT).show()
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
        val peerAware = if (forceDirectOnly) false else appState.callPeerWifiAware ?: pending.isWifiAware
        val peerDirect = pending.isWifiDirect || (appState.callPeerWifiDirect ?: false)
        val localAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
        val localDirect = appViewModel.isWifiDirectSupportedLocally()
        val useOpus = localOpusSupported && (appState.callPeerUseOpus ?: false)
        val targetDirectAddress = appState.callPeerDirectAddress ?: appState.peerDirectAddresses[peerId]
        val started = callViewModel.startRealTimeCall(
            survivor = pending,
            localWifiAwareSupported = localAware,
            localWifiDirectSupported = localDirect,
            peerWifiAwareSupported = peerAware,
            peerWifiDirectSupported = peerDirect,
            isServer = true,
            useOpus = useOpus,
            targetDirectAddress = targetDirectAddress,
            localPeerId = myPeerId,
            targetPeerId = peerId
        )
        if (!started) {
            Toast.makeText(context, "통화 연결 실패", Toast.LENGTH_SHORT).show()
            appViewModel.sendCallHandshake(
                targetPeerIdHex = peerId,
                action = CallHandshakeAction.END,
                callerName = profileState.name.ifBlank { "구조자" },
                wifiAwareSupported = localAware,
                wifiDirectSupported = localDirect,
                useOpus = localOpusSupported
            )
            appViewModel.clearLocalCallState(peerId)
            if (callingTargetPeerId == peerId) {
                callingTargetPeerId = null
                callAttemptStartedAtMs = 0L
            }
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
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                bleDebugStats = bleDebugStats,
                onPrev = { navController.popBackStack() },
                onProfile = { navigateSingleRoute(AppRoute.SurvivorProfile.route) },
                onSos = {
                    pendingSosNavigation = true
                    pendingSosRoute = AppRoute.SurvivorPTT.route
                    sosStartedAt = System.currentTimeMillis()
                    navigateSingleRoute(AppRoute.SurvivorEmergency.route)
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
            val pendingRequest = if (autoAcceptCalls) {
                null
            } else {
                appState.incomingCallPeerId?.let { peerId ->
                    val name = appState.incomingCallName?.ifBlank { "구조자" } ?: "구조자"
                    SurvivorCallRequest(
                        callerName = name,
                        wifiAware = appState.incomingCallWifiAware,
                        wifiDirect = appState.incomingCallWifiDirect,
                        useOpus = appState.incomingCallUseOpus
                    )
                }
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
                isSpeakerphoneOn = callDebugState.audio.speakerphoneEnabled,
                callPeerName = targetSurvivor?.name,
                pendingCall = pendingRequest,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onBack = { navController.popBackStack() },
                onDisconnect = {
                    if (isInCall) {
                        val targetPeerId = targetSurvivor?.peerId.orEmpty()
                        if (targetPeerId.isNotBlank()) {
                            sendRemoteStopIfNeeded(targetPeerId)
                            val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
                            val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                            appViewModel.sendCallHandshake(
                                targetPeerIdHex = targetPeerId,
                                action = CallHandshakeAction.END,
                                callerName = profileState.name.ifBlank { "생존자" },
                                wifiAwareSupported = localWifiAware,
                                wifiDirectSupported = localWifiDirect,
                                useOpus = localOpusSupported
                            )
                            appViewModel.clearLocalCallState(targetPeerId)
                        }
                        callViewModel.endCall()
                    }
                    resetRssiFeedbackDefaults()
                    onDisconnect()
                    navigateSingleRoute(AppRoute.RescuerStandby.route)
                },
                onChat = { navigateSingleRoute(AppRoute.SurvivorChat.route) },
                onProfile = { navigateSingleRoute(AppRoute.SurvivorProfile.route) },
                onPanicClear = onClearDeviceMonitoring,
                onToggleSpeakerphone = { callViewModel.toggleSpeakerphone() },
                onOpenUserList = { navigateSingleRoute(AppRoute.RescuerSurvivorDb.route) },
                onAcceptCall = {
                    val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                    if (!appViewModel.ensureWifiAwarePermissions()) return@PTTLinkScreen
                    val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
                    val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                    val peerWifiAware = if (forceDirectOnly) false else appState.incomingCallWifiAware
                    val peerWifiDirect = appState.incomingCallWifiDirect
                    val canUseAware = localWifiAware && peerWifiAware
                    val canUseDirect = localWifiDirect && peerWifiDirect
                    if (!canUseAware && !canUseDirect) {
                        Toast.makeText(context, "통화 불가: Aware/Direct 미지원", Toast.LENGTH_SHORT).show()
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
                            isUwb = appState.survivors.firstOrNull { it.peerId == peerId }?.isUwb ?: false,
                            peerId = peerId
                        ),
                        localWifiAwareSupported = localWifiAware,
                        localWifiDirectSupported = localWifiDirect,
                        peerWifiAwareSupported = peerWifiAware,
                        peerWifiDirectSupported = peerWifiDirect,
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
                            wifiAwareSupported = localWifiAware,
                            wifiDirectSupported = localWifiDirect,
                            useOpus = localOpusSupported
                        )
                        appViewModel.clearIncomingCall(peerId)
                    }
                },
                onDeclineCall = {
                    val peerId = appState.incomingCallPeerId ?: return@PTTLinkScreen
                    val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
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
                meshPeerCount = meshPeerCount,
                bleDebugStats = bleDebugStats,
                onPrev = { activity?.finish() },
                onGoPTT = { navigateSingleRoute(AppRoute.RescuerPTT.route) },
                onSos = { navigateSingleRoute(AppRoute.RescuerEmergency.route) }
            )
        }

        composable(AppRoute.RescuerPTT.route) {
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            val distanceState by distanceViewModel.uiState.collectAsState()
            val liveAwareRttMeters by appViewModel.wifiAwareRanger.rttDistance.collectAsState()
            val callTransportReady = when (callDebugState.activeTransport) {
                CallTransportType.WIFI_AWARE -> callDebugState.wifiAware.isReady
                CallTransportType.WIFI_DIRECT -> callDebugState.wifiDirect.isReady
                CallTransportType.NONE -> false
            }
            val callTransportLabel = when (callDebugState.activeTransport) {
                CallTransportType.WIFI_AWARE -> "Wi-Fi Aware"
                CallTransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                CallTransportType.NONE -> "없음"
            }
            val callStatusLabel = if (!isInCall) {
                "대기"
            } else if (callTransportReady) {
                "연결됨 ($callTransportLabel)"
            } else {
                "연결 중 ($callTransportLabel)"
            }
            val pttTargetPeerId = selectedTargetPeerId
                ?: selectedTargetSurvivor?.peerId
                ?: targetSurvivor?.peerId
                ?: appState.callPeerId
            val pttTargetSupportsAware = resolvePeerSupportsAware(pttTargetPeerId)
            val canAttemptUwbOnDetail =
                appViewModel.isUwbSupportedLocally() && !pttTargetPeerId.isNullOrBlank()
            val preferRttOnDetail =
                !forceDirectOnly &&
                    appViewModel.isWifiAwareSupportedLocally() &&
                    pttTargetSupportsAware
            LaunchedEffect(pttTargetPeerId, canAttemptUwbOnDetail, isInCall) {
                if (!isInCall && canAttemptUwbOnDetail && !pttTargetPeerId.isNullOrBlank()) {
                    appViewModel.requestUwbSession(pttTargetPeerId)
                }
            }
            LaunchedEffect(
                pttTargetPeerId,
                canAttemptUwbOnDetail,
                distanceState.measurementSource,
                distanceState.distanceMeters
            ) {
                if (!canAttemptUwbOnDetail || pttTargetPeerId.isNullOrBlank()) return@LaunchedEffect
                val hasUwbDistance =
                    distanceState.measurementSource == DistanceMeasurementSource.UWB &&
                        distanceState.distanceMeters != null
                if (hasUwbDistance) return@LaunchedEffect
                val now = System.currentTimeMillis()
                if (now - lastUwbSyncRequestAtMs < 3_000L) return@LaunchedEffect
                lastUwbSyncRequestAtMs = now
                appViewModel.requestUwbSession(pttTargetPeerId)
            }
            val isCallingOnPtt = callingTargetPeerId != null &&
                (pttTargetPeerId == null || callingTargetPeerId == pttTargetPeerId)
            val peerRttDistanceMeters = appState.callPeerRttCm?.toFloat()?.div(100f)
            val pttPeerRssi = pttTargetPeerId?.let { appState.peerRssi[it] }
            val rssiFallbackDistanceMeters = pttPeerRssi?.let { estimateDistanceMetersFromRssi(it) }
            val isAwareCallActive = isInCall && callDebugState.activeTransport == CallTransportType.WIFI_AWARE
            val awareRttDistanceMeters = liveAwareRttMeters ?: peerRttDistanceMeters
            LaunchedEffect(
                isInCall,
                callDebugState.activeTransport,
                callDebugState.wifiAware.isReady,
                pttTargetPeerId,
                liveAwareRttMeters
            ) {
                val peerId = pttTargetPeerId
                if (!isInCall || peerId.isNullOrBlank()) {
                    lastAwareRttReportAtMs = 0L
                    lastAwareRttReportCm = null
                    return@LaunchedEffect
                }
                val awareReady =
                    callDebugState.activeTransport == CallTransportType.WIFI_AWARE &&
                        callDebugState.wifiAware.isReady
                if (!awareReady) return@LaunchedEffect
                val rttMeters = liveAwareRttMeters ?: return@LaunchedEffect
                val rttCm = (rttMeters * 100f).roundToInt().coerceIn(0, 0xFFFF)
                val now = System.currentTimeMillis()
                val lastCm = lastAwareRttReportCm
                val changedEnough = lastCm == null || abs(rttCm - lastCm) >= 30
                if (!changedEnough && now - lastAwareRttReportAtMs < 4_000L) return@LaunchedEffect
                if (now - lastAwareRttReportAtMs < 1_200L) return@LaunchedEffect
                val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
                val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                appViewModel.sendCallHandshake(
                    targetPeerIdHex = peerId,
                    action = CallHandshakeAction.ACK,
                    callerName = profileState.name.ifBlank { "구조자" },
                    wifiAwareSupported = localWifiAware,
                    wifiDirectSupported = localWifiDirect,
                    useOpus = localOpusSupported,
                    state = CallHandshakeState.AWARE_OK,
                    rttCm = rttCm
                )
                lastAwareRttReportCm = rttCm
                lastAwareRttReportAtMs = now
            }
            val hasUwbDistance =
                distanceState.measurementSource == DistanceMeasurementSource.UWB &&
                    distanceState.distanceMeters != null
            val displayDistanceMeters = when {
                hasUwbDistance -> distanceState.distanceMeters
                // Prefer RTT only when it is actually measured; otherwise keep RSSI fallback.
                preferRttOnDetail && awareRttDistanceMeters != null -> awareRttDistanceMeters
                // Capability is only a hint. Final selection must prefer measured values.
                isAwareCallActive && awareRttDistanceMeters != null -> awareRttDistanceMeters
                distanceState.distanceMeters != null -> distanceState.distanceMeters
                isInCall && peerRttDistanceMeters != null -> peerRttDistanceMeters
                rssiFallbackDistanceMeters != null -> rssiFallbackDistanceMeters
                else -> null
            }
            val displayDistanceSource = when {
                hasUwbDistance -> DistanceMeasurementSource.UWB
                preferRttOnDetail && awareRttDistanceMeters != null -> DistanceMeasurementSource.RTT
                isAwareCallActive && awareRttDistanceMeters != null -> DistanceMeasurementSource.RTT
                isAwareCallActive && displayDistanceMeters == null -> DistanceMeasurementSource.RTT
                distanceState.distanceMeters != null -> distanceState.measurementSource
                isInCall && peerRttDistanceMeters != null -> DistanceMeasurementSource.RTT
                rssiFallbackDistanceMeters != null -> DistanceMeasurementSource.RSSI
                else -> distanceState.measurementSource
            }
            val resolvedRssiMode = runCatching {
                RssiFeedbackMode.valueOf(rssiFeedbackMode)
            }.getOrElse {
                RssiFeedbackMode.BOTH
            }
            val resolvedRssiLevel = runCatching {
                RssiFeedbackLevel.valueOf(rssiFeedbackLevel)
            }.getOrElse {
                RssiFeedbackLevel.MEDIUM
            }
            val remoteControlEnabled = !pttTargetPeerId.isNullOrBlank()
            val remoteDurationMs = when (resolvedRssiLevel) {
                RssiFeedbackLevel.LOW -> 1_600
                RssiFeedbackLevel.MEDIUM -> 2_900
                RssiFeedbackLevel.HIGH -> 4_200
            }
            // Keep a perceptible pause between repeated remote alerts so they do not overlap unnaturally.
            val remoteRepeatIntervalMs = (remoteDurationMs + 1_400L).coerceIn(2_600L, 8_000L)
            val remoteIntensity = when (resolvedRssiLevel) {
                RssiFeedbackLevel.LOW -> 1
                RssiFeedbackLevel.MEDIUM -> 2
                RssiFeedbackLevel.HIGH -> 3
            }
            LaunchedEffect(
                pttTargetPeerId,
                displayDistanceMeters,
                displayDistanceSource,
                resolvedRssiMode,
                resolvedRssiLevel
            ) {
                if (pttTargetPeerId.isNullOrBlank()) {
                    lastRssiGuidanceAtMs = 0L
                    return@LaunchedEffect
                }
                if (resolvedRssiMode == RssiFeedbackMode.OFF) return@LaunchedEffect
                val distance = displayDistanceMeters ?: return@LaunchedEffect
                if (displayDistanceSource != DistanceMeasurementSource.RSSI) return@LaunchedEffect
                val now = System.currentTimeMillis()
                val clampedDistance = distance.coerceIn(0f, 10f)
                val normalizedProximity = (10f - clampedDistance) / 10f
                val baseIntervalMs = (1_600f - (normalizedProximity * 1_520f)).roundToInt().toLong()
                val adjustedIntervalMs = when (resolvedRssiLevel) {
                    RssiFeedbackLevel.LOW -> (baseIntervalMs * 1.25f).roundToInt().toLong()
                    RssiFeedbackLevel.MEDIUM -> baseIntervalMs
                    RssiFeedbackLevel.HIGH -> (baseIntervalMs * 0.82f).roundToInt().toLong()
                }.coerceIn(
                    when (resolvedRssiMode) {
                        RssiFeedbackMode.SOUND -> 55L
                        RssiFeedbackMode.BOTH -> 95L
                        else -> 220L
                    },
                    2_400L
                )
                if (now - lastRssiGuidanceAtMs < adjustedIntervalMs) return@LaunchedEffect
                lastRssiGuidanceAtMs = now
                if (resolvedRssiMode == RssiFeedbackMode.VIBRATION || resolvedRssiMode == RssiFeedbackMode.BOTH) {
                    triggerRescuerRssiVibration(context, resolvedRssiLevel)
                }
                if (resolvedRssiMode == RssiFeedbackMode.SOUND || resolvedRssiMode == RssiFeedbackMode.BOTH) {
                    val toneType = when {
                        clampedDistance <= 1.2f -> ToneGenerator.TONE_CDMA_HIGH_SS
                        clampedDistance <= 3f -> ToneGenerator.TONE_CDMA_HIGH_L
                        clampedDistance <= 6f -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                        else -> ToneGenerator.TONE_PROP_BEEP
                    }
                    val baseToneDurationMs = when (resolvedRssiLevel) {
                        RssiFeedbackLevel.LOW -> 45
                        RssiFeedbackLevel.MEDIUM -> 60
                        RssiFeedbackLevel.HIGH -> 78
                    }
                    val nearBoostMs = (normalizedProximity * 70f).roundToInt()
                    val toneDurationMs = if (clampedDistance <= 0.9f) {
                        (adjustedIntervalMs * 0.94f).roundToInt().coerceIn(110, 320)
                    } else {
                        (baseToneDurationMs + nearBoostMs).coerceIn(45, 220)
                    }
                    rssiToneGenerator.startTone(toneType, toneDurationMs)
                }
            }
            RescuerPTTLinkScreen(
                batteryLevel = batteryLevel,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                myPeerId = myPeerId,
                myNickname = myNickname,
                peerNicknames = peerNicknames,
                meshGraphSnapshot = meshGraphSnapshot,
                meshVisualEvents = meshVisualEvents,
                bleDebugStats = bleDebugStats,
                callStatusLabel = callStatusLabel,
                callDecisionLabel = callDebugState.lastDecision,
                isInCall = isInCall,
                isCalling = isCallingOnPtt,
                isConnected = isConnected,
                isSpeakerphoneOn = callDebugState.audio.speakerphoneEnabled,
                distanceMeters = displayDistanceMeters,
                distanceTrend = distanceState.trend,
                distanceSource = displayDistanceSource,
                onRequestCall = {
                    val survivor = resolveSelectedSurvivorForCall()
                    if (survivor == null) {
                        Toast.makeText(context, "생존자를 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
                        navigateSingleRoute(AppRoute.RescuerSurvivorDb.route)
                    } else {
                        if (survivor.peerId.isNotBlank()) {
                            selectedTargetPeerId = survivor.peerId
                        }
                        requestRescuerCall(survivor)
                    }
                },
                onEndCall = { endCurrentRescuerCall() },
                onBack = { navigateSingleRoute(AppRoute.RescuerSurvivorDb.route) },
                onPanicClear = onClearDeviceMonitoring,
                onToggleSpeakerphone = {
                    val nextSpeakerEnabled = !callDebugState.audio.speakerphoneEnabled
                    callViewModel.setSpeakerphoneEnabled(nextSpeakerEnabled)
                    applyRescuerSpeakerRoute(context, nextSpeakerEnabled)
                },
                rssiFeedbackMode = resolvedRssiMode,
                rssiFeedbackLevel = resolvedRssiLevel,
                onCycleRssiFeedbackMode = {
                    rssiFeedbackMode = when (resolvedRssiMode) {
                        RssiFeedbackMode.OFF -> RssiFeedbackMode.VIBRATION.name
                        RssiFeedbackMode.VIBRATION -> RssiFeedbackMode.SOUND.name
                        RssiFeedbackMode.SOUND -> RssiFeedbackMode.BOTH.name
                        RssiFeedbackMode.BOTH -> RssiFeedbackMode.OFF.name
                    }
                },
                onCycleRssiFeedbackLevel = {
                    rssiFeedbackLevel = when (resolvedRssiLevel) {
                        RssiFeedbackLevel.LOW -> RssiFeedbackLevel.MEDIUM.name
                        RssiFeedbackLevel.MEDIUM -> RssiFeedbackLevel.HIGH.name
                        RssiFeedbackLevel.HIGH -> RssiFeedbackLevel.LOW.name
                    }
                },
                remoteControlEnabled = remoteControlEnabled,
                remoteRepeatIntervalMs = remoteRepeatIntervalMs,
                onSendRemoteWake = {
                    val peerId = pttTargetPeerId ?: return@RescuerPTTLinkScreen
                    appViewModel.sendDeviceControl(
                        targetPeerIdHex = peerId,
                        command = DeviceControlCommand.WAKE_SCREEN,
                        durationMs = 1_200,
                        intensity = remoteIntensity
                    )
                },
                onSendRemoteBeep = {
                    val peerId = pttTargetPeerId ?: return@RescuerPTTLinkScreen
                    appViewModel.sendDeviceControl(
                        targetPeerIdHex = peerId,
                        command = DeviceControlCommand.BEEP,
                        durationMs = remoteDurationMs,
                        intensity = remoteIntensity
                    )
                },
                onSendRemoteVibrate = {
                    val peerId = pttTargetPeerId ?: return@RescuerPTTLinkScreen
                    appViewModel.sendDeviceControl(
                        targetPeerIdHex = peerId,
                        command = DeviceControlCommand.VIBRATE,
                        durationMs = remoteDurationMs,
                        intensity = remoteIntensity
                    )
                },
                onSendRemoteHighTone = {
                    val peerId = pttTargetPeerId ?: return@RescuerPTTLinkScreen
                    appViewModel.sendDeviceControl(
                        targetPeerIdHex = peerId,
                        command = DeviceControlCommand.HIGH_TONE,
                        durationMs = remoteDurationMs,
                        intensity = remoteIntensity,
                        frequencyHz = 17_500
                    )
                },
                onSendRemoteStop = {
                    sendRemoteStopIfNeeded(pttTargetPeerId)
                }
            )
        }

        composable(AppRoute.RescuerSurvivorDb.route) {
            LaunchedEffect(Unit) {
                onStartAutoConnect()
            }
            val dbDistanceState by distanceViewModel.uiState.collectAsState()
            val dbDistanceTargetPeerId = selectedTargetPeerId ?: targetSurvivor?.peerId ?: appState.callPeerId
            RescuerSurvivorDbScreen(
                survivors = appState.survivors,
                peerRssiMap = appState.peerRssi,
                peerBatteryMap = appState.peerBatteryLevels,
                onDisconnectClick = {
                    val targetPeerId = callingTargetPeerId ?: targetSurvivor?.peerId.orEmpty()
                    if (targetPeerId.isNotBlank()) {
                        sendRemoteStopIfNeeded(targetPeerId)
                        val localWifiAware = if (forceDirectOnly) false else appViewModel.isWifiAwareSupportedLocally()
                        val localWifiDirect = appViewModel.isWifiDirectSupportedLocally()
                        appViewModel.sendCallHandshake(
                            targetPeerIdHex = targetPeerId,
                            action = CallHandshakeAction.END,
                            callerName = profileState.name.ifBlank { "구조자" },
                            wifiAwareSupported = localWifiAware,
                            wifiDirectSupported = localWifiDirect,
                            useOpus = localOpusSupported
                        )
                        appViewModel.clearLocalCallState(targetPeerId)
                    }
                    callViewModel.clearPendingCall()
                    callViewModel.endCall()
                    callingTargetPeerId = null
                    callAttemptStartedAtMs = 0L
                    resetRssiFeedbackDefaults()
                    onDisconnect()
                    navigateSingleRoute(AppRoute.RescuerStandby.route)
                },
                onCallClick = { survivor ->
                    selectedTargetPeerId = survivor.peerId.ifBlank { selectedTargetPeerId }
                    selectedTargetSurvivor = survivor
                    val supportsUwb = resolvePeerSupportsUwb(survivor.peerId)
                    val localSupportsUwb = appViewModel.isUwbSupportedLocally()
                    val hasTargetPeer = survivor.peerId.isNotBlank()
                    distanceViewModel.setTargetPeerId(survivor.peerId.ifBlank { null })
                    distanceViewModel.setUwbCapability(
                        localSupported = localSupportsUwb,
                        peerSupported = hasTargetPeer && (supportsUwb || localSupportsUwb)
                    )
                    if (localSupportsUwb && hasTargetPeer) {
                        appViewModel.requestUwbSession(survivor.peerId)
                    } else if (!supportsUwb) {
                        appViewModel.stopUwbSession()
                        lastUwbSyncRequestAtMs = 0L
                    }
                    navigateSingleRoute(AppRoute.RescuerPTT.route)
                    requestRescuerCall(survivor)
                },
                onEndCall = { endCurrentRescuerCall() },
                onOpenMeshMap = { navigateSingleRoute(AppRoute.RescuerMeshMap.route) },
                onOpenGroupChat = {
                    directChatPeerId = null
                    navigateSingleRoute(AppRoute.RescuerChat.route)
                },
                onOpenDirectChat = { survivor ->
                    val peerId = survivor.peerId
                    if (peerId.isNotBlank()) {
                        directChatPeerId = peerId
                        navigateSingleRoute(AppRoute.RescuerChat.route)
                    }
                },
                selectedTargetPeerId = selectedTargetPeerId,
                onSelectTarget = { survivor ->
                    selectedTargetPeerId = survivor.peerId
                    selectedTargetSurvivor = survivor
                    val supportsUwb = resolvePeerSupportsUwb(survivor.peerId)
                    val localSupportsUwb = appViewModel.isUwbSupportedLocally()
                    val hasTargetPeer = survivor.peerId.isNotBlank()
                    distanceViewModel.setTargetPeerId(survivor.peerId.ifBlank { null })
                    distanceViewModel.setUwbCapability(
                        localSupported = localSupportsUwb,
                        peerSupported = hasTargetPeer && (supportsUwb || localSupportsUwb)
                    )
                    if (localSupportsUwb && hasTargetPeer) {
                        appViewModel.requestUwbSession(survivor.peerId)
                    } else {
                        appViewModel.stopUwbSession()
                        lastUwbSyncRequestAtMs = 0L
                    }
                    navigateSingleRoute(AppRoute.RescuerPTT.route)
                },
                activeSurvivor = targetSurvivor,
                isInCall = isInCall,
                callingPeerId = callingTargetPeerId,
                callPeerId = appState.callPeerId,
                callPeerRttCm = appState.callPeerRttCm,
                activeDistancePeerId = dbDistanceTargetPeerId,
                activeDistanceMeters = dbDistanceState.distanceMeters,
                activeDistanceSource = dbDistanceState.measurementSource
            )
        }

        composable(AppRoute.RescuerMeshMap.route) {
            RescuerMeshMapScreen(
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                myPeerId = myPeerId,
                myNickname = myNickname,
                peerNicknames = peerNicknames,
                meshGraphSnapshot = meshGraphSnapshot,
                meshVisualEvents = meshVisualEvents,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.RescuerChat.route) {
            val directPeerId = directChatPeerId
            val isDirectRoom = !directPeerId.isNullOrBlank()
            val directPeerName = appState.survivors
                .firstOrNull { it.peerId == directPeerId }
                ?.name
                ?.ifBlank { null }
                ?: directPeerId?.let { peerNicknames[it]?.ifBlank { null } }
                ?: "대상"
            val chatMessages = if (isDirectRoom) {
                messages.filter { message ->
                    (message.isMine && message.recipientPeerId == directPeerId) ||
                        (!message.isMine &&
                            message.senderPeerId == directPeerId &&
                            message.recipientPeerId == myPeerId)
                }
            } else {
                messages.filter { it.recipientPeerId == null }
            }
            RescuerChatScreen(
                roomTitle = if (isDirectRoom) "1:1 채팅 · $directPeerName" else "전체 채팅",
                meshPeerCount = meshPeerCount,
                messages = chatMessages,
                signatureLogs = signatureLogs,
                profileLogs = profileLogs,
                onClearSignatureLogs = onClearSignatureLogs,
                onClearProfileLogs = onClearProfileLogs,
                onSendProfileTest = onSendProfileTest,
                onPrev = { navController.popBackStack() },
                onSend = { text ->
                    if (isDirectRoom && !directPeerId.isNullOrBlank()) {
                        appViewModel.onSendDirectMessage(directPeerId, text)
                    } else {
                        onSendMessage(text)
                    }
                }
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

private fun estimateDistanceMetersFromRssi(rssi: Int): Float {
    val txPower = -59
    val pathLossExponent = 2.0
    return 10.0.pow((txPower - rssi) / (10 * pathLossExponent)).toFloat()
}

private fun triggerRescuerRssiVibration(
    context: Context,
    level: RssiFeedbackLevel
) {
    val durationMs = when (level) {
        RssiFeedbackLevel.LOW -> 180L
        RssiFeedbackLevel.MEDIUM -> 300L
        RssiFeedbackLevel.HIGH -> 420L
    }
    val amplitude = when (level) {
        RssiFeedbackLevel.LOW -> 120
        RssiFeedbackLevel.MEDIUM -> 210
        RssiFeedbackLevel.HIGH -> 255
    }
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

private fun applyRescuerSpeakerRoute(context: Context, speakerEnabled: Boolean) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetType = if (speakerEnabled) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        val targetDevice = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (targetDevice != null) {
            runCatching { audioManager.setCommunicationDevice(targetDevice) }
        } else if (!speakerEnabled) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
    }
    runCatching { audioManager.isSpeakerphoneOn = speakerEnabled }
    if (!speakerEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        if (earpiece != null) {
            runCatching { audioManager.setCommunicationDevice(earpiece) }
        }
    }
}
