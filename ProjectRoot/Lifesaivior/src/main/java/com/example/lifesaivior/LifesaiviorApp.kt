package com.example.lifesaivior

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.presentation.BleDebugStats
import com.example.lifesaivior.presentation.AppViewModel
import com.example.lifesaivior.presentation.screen.BlackSaverScreen
import com.example.lifesaivior.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaivior.protocol.security.SignatureLogEntry
import com.example.lifesaivior.presentation.MeshVisualEvent
import com.example.lifesaivior.ui.components.EmergencySignalDropdown
import com.example.lifesaivior.ui.navigation.AppNavHost
import com.example.lifesaivior.ui.navigation.AppRoute
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.rememberAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

private const val LOCAL_SIGNAL_DURATION_MS = 2_900
private const val LOCAL_SIGNAL_INTENSITY = 2
private const val LOCAL_SIGNAL_HIGH_TONE_HZ = 17_500
private const val LOCAL_SIGNAL_REPEAT_GAP_MS = 1_400L
private const val LOCAL_SIGNAL_REPEAT_MIN_MS = 2_600L

@Composable
fun LifesaiviorApp(
    // hasPermissions 변수는 이제 화면 분기용이 아니라, 단순히 상태 표시용(예: 설정 화면에서 스위치 상태)으로만 씁니다.
    hasPermissions: Boolean,
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    appViewModel: AppViewModel,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    isRescueSignalActive: Boolean,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onStopAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendProfileTest: () -> Unit,
    onSendProfileUpdate: (com.example.lifesaivior.core.profile.SurvivorProfile) -> Unit,
    onDisconnect: () -> Unit,
    onStartRescueSignal: () -> Unit,
    onStopRescueSignal: () -> Unit,
    onPulseRescueSignal: () -> Unit,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onClearDeviceMonitoring: () -> Unit,
    isVoiceDetectionEnabled: Boolean,
    isShockDetectionEnabled: Boolean,
    isDemoModeEnabled: Boolean,
    onSetVoiceDetection: (Boolean) -> Unit,
    onSetShockDetection: (Boolean) -> Unit,
    onSetDemoMode: (Boolean) -> Unit
) {
    val scale = rememberAppScale()

    // [상태 1] 마지막 터치 시간 (절전 모드용)
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // [상태 2] 절전 화면 활성화 여부
    var isSaverVisible by remember { mutableStateOf(false) }

    // 현재 보고 있는 화면 경로 추적 (SOS 화면일 때만 절전 모드 작동)
    var currentRoute by remember { mutableStateOf(AppRoute.SurvivorProfile.route) }

    val autoSaverEnabled =
        isRescueSignalActive && currentRoute == AppRoute.SurvivorEmergency.route
    val autoSaverTimeoutMs = 60_000L // 60초
    val density = LocalDensity.current

    var isSignalPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var stickyBeep by rememberSaveable { mutableStateOf(false) }
    var stickyVibrate by rememberSaveable { mutableStateOf(false) }
    var stickyHighTone by rememberSaveable { mutableStateOf(false) }
    var isBeepRepeating by remember { mutableStateOf(false) }
    var isVibrateRepeating by remember { mutableStateOf(false) }
    var isHighToneRepeating by remember { mutableStateOf(false) }
    var torchSosEnabled by remember { mutableStateOf(appViewModel.isTorchSosEnabled()) }
    val repeatIntervalMs = maxOf(
        LOCAL_SIGNAL_DURATION_MS.toLong() + LOCAL_SIGNAL_REPEAT_GAP_MS,
        LOCAL_SIGNAL_REPEAT_MIN_MS
    )
    val isStopHighlighted = isBeepRepeating || isVibrateRepeating || isHighToneRepeating
    val swipeTriggerPx = with(density) { scaledDp(52, scale).toPx() }
    val panelExpandedState by rememberUpdatedState(isSignalPanelExpanded)

    LaunchedEffect(isBeepRepeating) {
        while (isBeepRepeating) {
            appViewModel.triggerLocalBeep(LOCAL_SIGNAL_DURATION_MS, LOCAL_SIGNAL_INTENSITY)
            delay(repeatIntervalMs)
        }
    }

    LaunchedEffect(isVibrateRepeating) {
        while (isVibrateRepeating) {
            appViewModel.triggerLocalVibrate(LOCAL_SIGNAL_DURATION_MS, LOCAL_SIGNAL_INTENSITY)
            delay(repeatIntervalMs)
        }
    }

    LaunchedEffect(isHighToneRepeating) {
        while (isHighToneRepeating) {
            appViewModel.triggerLocalHighTone(
                LOCAL_SIGNAL_DURATION_MS,
                LOCAL_SIGNAL_INTENSITY,
                LOCAL_SIGNAL_HIGH_TONE_HZ
            )
            delay(repeatIntervalMs)
        }
    }

    // [로직 1] 절전 타이머 로직
    LaunchedEffect(autoSaverEnabled, isSaverVisible, autoSaverTimeoutMs) {
        if (autoSaverEnabled && !isSaverVisible) {
            while (true) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastInteractionTime >= autoSaverTimeoutMs) {
                    isSaverVisible = true
                }
                delay(1000L)
            }
        } else if (!autoSaverEnabled) {
            isSaverVisible = false
        }
    }

    // [로직 2] SOS 시작 시 타이머 초기화
    LaunchedEffect(autoSaverEnabled) {
        if (autoSaverEnabled) {
            lastInteractionTime = System.currentTimeMillis()
            isSaverVisible = false
        }
    }

    LaunchedEffect(isSaverVisible) {
        if (isSaverVisible) {
            isSignalPanelExpanded = false
        }
    }

    fun requestSaverVisible() {
        if (!autoSaverEnabled) return
        lastInteractionTime = System.currentTimeMillis()
        isSaverVisible = true
    }

    fun stopAllLocalSignals() {
        stickyBeep = false
        stickyVibrate = false
        stickyHighTone = false
        isBeepRepeating = false
        isVibrateRepeating = false
        isHighToneRepeating = false
        appViewModel.stopLocalAlerts()
        if (torchSosEnabled) {
            appViewModel.setTorchSosEnabled(false)
            torchSosEnabled = false
        }
    }

    CompositionLocalProvider(LocalAppScale provides scale) {
        // [전역 터치 감지]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                if (!isSaverVisible) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
                .pointerInput(isSaverVisible, isSignalPanelExpanded) {
                    if (isSaverVisible) return@pointerInput
                    awaitPointerEventScope {
                        var tracking = false
                        var startExpanded = false
                        var totalY = 0f
                        var pointerId: androidx.compose.ui.input.pointer.PointerId? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!tracking) {
                                val downChange = event.changes.firstOrNull { it.changedToDown() }
                                if (downChange != null) {
                                    startExpanded = panelExpandedState
                                    tracking = true
                                    pointerId = downChange.id
                                    totalY = 0f
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                            if (change == null) {
                                tracking = false
                                pointerId = null
                                continue
                            }
                            totalY += (change.position.y - change.previousPosition.y)
                            if (!startExpanded && totalY > swipeTriggerPx) {
                                isSignalPanelExpanded = true
                                tracking = false
                                pointerId = null
                                continue
                            }
                            if (startExpanded && totalY < -swipeTriggerPx) {
                                isSignalPanelExpanded = false
                                tracking = false
                                pointerId = null
                                continue
                            }
                            if (!change.pressed) {
                                tracking = false
                                pointerId = null
                            }
                        }
                    }
                }
        ) {
            // 1. 메인 앱 네비게이션
            AppNavHost(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                connectedCount = connectedCount,
                meshPeerCount = meshPeerCount,
                directPeerIds = directPeerIds,
                myPeerId = myPeerId,
                myNickname = myNickname,
                peerNicknames = peerNicknames,
                meshGraphSnapshot = meshGraphSnapshot,
                meshVisualEvents = meshVisualEvents,
                bleDebugStats = bleDebugStats,
                appViewModel = appViewModel,
                isMicOn = isMicOn,
                isDisconnecting = isDisconnecting,
                isRescueSignalActive = isRescueSignalActive,
                messages = messages,
                signatureLogs = signatureLogs,
                profileLogs = profileLogs,
                onClearSignatureLogs = onClearSignatureLogs,
                onClearProfileLogs = onClearProfileLogs,
                onStartAutoConnect = onStartAutoConnect,
                onStopAutoConnect = onStopAutoConnect,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                onSendMessage = onSendMessage,
                onSendProfileTest = onSendProfileTest,
                onSendProfileUpdate = onSendProfileUpdate,
                onDisconnect = onDisconnect,
                onStartRescueSignal = onStartRescueSignal,
                onStopRescueSignal = onStopRescueSignal,
                onPulseRescueSignal = onPulseRescueSignal,
                onClearDeviceMonitoring = onClearDeviceMonitoring,
                isVoiceDetectionEnabled = isVoiceDetectionEnabled,
                isShockDetectionEnabled = isShockDetectionEnabled,
                isDemoModeEnabled = isDemoModeEnabled,
                onSetVoiceDetection = onSetVoiceDetection,
                onSetShockDetection = onSetShockDetection,
                onSetDemoMode = onSetDemoMode,
                onRouteChanged = { route -> currentRoute = route },
                onRequestPowerSaveScreen = { requestSaverVisible() }
            )

            if (!isSaverVisible) {
                EmergencySignalDropdown(
                    isExpanded = isSignalPanelExpanded,
                    onExpandedChange = { isSignalPanelExpanded = it },
                    isBeepRepeating = isBeepRepeating,
                    onBeepTap = {
                        appViewModel.triggerLocalBeep(LOCAL_SIGNAL_DURATION_MS, LOCAL_SIGNAL_INTENSITY)
                    },
                    isBeepSticky = stickyBeep,
                    onBeepStickyChange = { stickyBeep = it },
                    onBeepRepeatingChange = { isBeepRepeating = it },
                    isVibrateRepeating = isVibrateRepeating,
                    onVibrateTap = {
                        appViewModel.triggerLocalVibrate(LOCAL_SIGNAL_DURATION_MS, LOCAL_SIGNAL_INTENSITY)
                    },
                    isVibrateSticky = stickyVibrate,
                    onVibrateStickyChange = { stickyVibrate = it },
                    onVibrateRepeatingChange = { isVibrateRepeating = it },
                    isHighToneRepeating = isHighToneRepeating,
                    onHighToneTap = {
                        appViewModel.triggerLocalHighTone(
                            LOCAL_SIGNAL_DURATION_MS,
                            LOCAL_SIGNAL_INTENSITY,
                            LOCAL_SIGNAL_HIGH_TONE_HZ
                        )
                    },
                    isHighToneSticky = stickyHighTone,
                    onHighToneStickyChange = { stickyHighTone = it },
                    onHighToneRepeatingChange = { isHighToneRepeating = it },
                    isStopHighlighted = isStopHighlighted,
                    onStopAll = { stopAllLocalSignals() },
                    torchSosEnabled = torchSosEnabled,
                    onToggleTorchSos = {
                        val next = !torchSosEnabled
                        torchSosEnabled = appViewModel.setTorchSosEnabled(next)
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // 2. 절전 모드 오버레이 (조건 충족 시 최상단에 표시)
            if (autoSaverEnabled && isSaverVisible) {
                BlackSaverScreen(
                    onUnlock = {
                        lastInteractionTime = System.currentTimeMillis()
                        isSaverVisible = false
                    }
                )
            }
        }
    }
}
