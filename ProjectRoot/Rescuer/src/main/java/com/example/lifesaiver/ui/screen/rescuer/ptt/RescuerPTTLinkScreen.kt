package com.example.lifesaiver.ui.screen.rescuer.ptt

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.example.lifesaiver.R
import com.example.lifesaiver.core.log.ConnectionLog
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.ui.components.DistanceTrack
import com.example.lifesaiver.core.location.DistanceMeasurementSource
import com.example.lifesaiver.core.location.DistanceTrend
import com.example.lifesaiver.ui.components.MeshEdge
import com.example.lifesaiver.ui.components.MeshNode
import com.example.lifesaiver.ui.components.PowerSavingLayer
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.components.tripleClickable
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

enum class RssiFeedbackMode(val label: String) {
    OFF("알림 끔"),
    VIBRATION("진동"),
    SOUND("소리"),
    BOTH("진동+소리")
}

enum class RssiFeedbackLevel(val label: String) {
    LOW("약"),
    MEDIUM("중"),
    HIGH("강")
}

@Composable
fun RescuerPTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    callStatusLabel: String,
    callDecisionLabel: String? = null,
    isInCall: Boolean,
    isCalling: Boolean = false,
    isConnected: Boolean,
    isSpeakerphoneOn: Boolean = true,
    distanceMeters: Float?,
    distanceTrend: DistanceTrend,
    distanceSource: DistanceMeasurementSource,
    onRequestCall: () -> Unit,
    onEndCall: () -> Unit,
    onBack: () -> Unit,
    onPanicClear: () -> Unit,
    onToggleSpeakerphone: () -> Unit = {},
    rssiFeedbackMode: RssiFeedbackMode = RssiFeedbackMode.BOTH,
    rssiFeedbackLevel: RssiFeedbackLevel = RssiFeedbackLevel.MEDIUM,
    onCycleRssiFeedbackMode: () -> Unit = {},
    onCycleRssiFeedbackLevel: () -> Unit = {},
    remoteControlEnabled: Boolean = false,
    onSendRemoteWake: () -> Unit = {},
    onSendRemoteBeep: () -> Unit = {},
    onSendRemoteVibrate: () -> Unit = {},
    onSendRemoteHighTone: () -> Unit = {},
    onSendRemoteStop: () -> Unit = {},
    remoteRepeatIntervalMs: Long = 2_500L
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    var showDebugModal by remember { mutableStateOf(false) }
    var stickyBeep by remember { mutableStateOf(false) }
    var stickyVibrate by remember { mutableStateOf(false) }
    var stickyHighTone by remember { mutableStateOf(false) }
    var isBeepRepeating by remember { mutableStateOf(false) }
    var isVibrateRepeating by remember { mutableStateOf(false) }
    var isHighToneRepeating by remember { mutableStateOf(false) }
    val connectionLogs by ConnectionLog.logs.collectAsState()
    val meshDisplayCount = meshPeerCount.coerceAtLeast(0)
    val hasMeshPeers = meshDisplayCount > 0
    val isLinkActive = isConnected || hasMeshPeers

    LaunchedEffect(remoteControlEnabled) {
        if (!remoteControlEnabled) {
            stickyBeep = false
            stickyVibrate = false
            stickyHighTone = false
            isBeepRepeating = false
            isVibrateRepeating = false
            isHighToneRepeating = false
        }
    }
    LaunchedEffect(stickyBeep, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyBeep) return@LaunchedEffect
        while (remoteControlEnabled && stickyBeep) {
            onSendRemoteBeep()
            delay(remoteRepeatIntervalMs)
        }
    }
    LaunchedEffect(stickyVibrate, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyVibrate) return@LaunchedEffect
        while (remoteControlEnabled && stickyVibrate) {
            onSendRemoteVibrate()
            delay(remoteRepeatIntervalMs)
        }
    }
    LaunchedEffect(stickyHighTone, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyHighTone) return@LaunchedEffect
        while (remoteControlEnabled && stickyHighTone) {
            onSendRemoteHighTone()
            delay(remoteRepeatIntervalMs)
        }
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                // 절전 해제 컨텐츠가 없으면 원래처럼 단순 처리라도 유지
                isForceExit = !isPowerSaving,
                onRequestExitPowerSaving = { setPowerSaving(false) }
            )
            TopIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "이전",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(12, scale), top = scaledDp(12, scale))
            )
            Text(
                text = "LIFESAIVER",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(60, scale), top = scaledDp(18, scale))
                    .tripleClickable(onTripleClick = onPanicClear)
            )
            Text(
                text = "디버그",
                color = AppColors.Gray400,
                fontSize = scaledSp(11, scale),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = scaledDp(20, scale), top = scaledDp(16, scale))
                    .clickable { showDebugModal = true }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(32, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(scaledDp(30, scale)))
                DistanceTrack(
                    distanceMeters = distanceMeters,
                    trend = distanceTrend,
                    maxMeters = 30f,
                    measurementSource = distanceSource,
                    modifier = Modifier.padding(top = scaledDp(12, scale))
                )

                Spacer(modifier = Modifier.height(scaledDp(40, scale)))
                Text(
                    text = when {
                        hasMeshPeers -> "메쉬 연결됨"
                        isConnected -> "생존자 연결됨"
                        else -> "생존자 연결 대기 중"
                    },
                    color = if (isLinkActive) AppColors.Green else AppColors.Gray500,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(scaledDp(74, scale)))
                Box(contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = {
                            if (isInCall) onEndCall() else onRequestCall()
                        },
                        enabled = isInCall || !isCalling,
                        modifier = Modifier.size(scaledDp(86, scale)),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isInCall) AppColors.Red else AppColors.Green,
                            disabledContainerColor = AppColors.Green.copy(alpha = 0.4f),
                            contentColor = AppColors.White,
                            disabledContentColor = AppColors.White.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = if (isInCall) "통화 종료" else "통화 요청",
                            modifier = Modifier.size(scaledDp(40, scale))
                        )
                    }
                    if (isCalling && !isInCall) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(scaledDp(104, scale)),
                            color = AppColors.Green,
                            strokeWidth = scaledDp(2, scale)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                Text(
                    text = when {
                        isCalling && !isInCall -> "통화 요청 전송 중..."
                        isInCall -> "통화 중 · 버튼을 눌러 종료"
                        else -> "통화 요청"
                    },
                    color = when {
                        isCalling && !isInCall -> AppColors.Green
                        isInCall -> AppColors.Red
                        else -> AppColors.Green
                    },
                    fontSize = scaledSp(13, scale),
                    fontWeight = FontWeight.Bold
                )
                if (isInCall) {
                    Spacer(modifier = Modifier.height(scaledDp(10, scale)))
                    OutlinedButton(onClick = onToggleSpeakerphone) {
                        Text(
                            text = if (isSpeakerphoneOn) "스피커 끄기" else "스피커 켜기",
                            fontSize = scaledSp(12, scale),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = scaledDp(520, scale))
                        .padding(horizontal = scaledDp(8, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RSSI 탐지음 ${rssiFeedbackMode.label} · 강도 ${rssiFeedbackLevel.label}",
                        color = AppColors.Gray400,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
                    ) {
                        OutlinedButton(
                            onClick = onCycleRssiFeedbackMode,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "알림 모드 변경",
                                fontSize = scaledSp(11, scale)
                            )
                        }
                        OutlinedButton(
                            onClick = onCycleRssiFeedbackLevel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "강도 변경",
                                fontSize = scaledSp(11, scale)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                    ) {
                        RepeatActionButton(
                            label = "절전 해제",
                            enabled = remoteControlEnabled,
                            onTap = onSendRemoteWake,
                            isRepeating = false,
                            modifier = Modifier.weight(1f)
                        )
                        RepeatActionButton(
                            label = "비프음",
                            enabled = remoteControlEnabled,
                            onTap = onSendRemoteBeep,
                            isStickyActive = stickyBeep,
                            onStickyStateChanged = { stickyBeep = it },
                            isRepeating = isBeepRepeating,
                            onActiveStateChanged = { isBeepRepeating = it },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            enabled = remoteControlEnabled,
                            onClick = {
                                stickyBeep = false
                                stickyVibrate = false
                                stickyHighTone = false
                                onSendRemoteStop()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("송출 중지", fontSize = scaledSp(11, scale))
                        }
                    }
                    Spacer(modifier = Modifier.height(scaledDp(4, scale)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                    ) {
                        RepeatActionButton(
                            label = "저주파",
                            enabled = remoteControlEnabled,
                            onTap = onSendRemoteVibrate,
                            isStickyActive = stickyVibrate,
                            onStickyStateChanged = { stickyVibrate = it },
                            isRepeating = isVibrateRepeating,
                            onActiveStateChanged = { isVibrateRepeating = it },
                            modifier = Modifier.weight(1f)
                        )
                        RepeatActionButton(
                            label = "고주파",
                            enabled = remoteControlEnabled,
                            onTap = onSendRemoteHighTone,
                            isStickyActive = stickyHighTone,
                            onStickyStateChanged = { stickyHighTone = it },
                            isRepeating = isHighToneRepeating,
                            onActiveStateChanged = { isHighToneRepeating = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                    val repeatingItems = buildList {
                        if (isBeepRepeating) add("비프음")
                        if (isVibrateRepeating) add("저주파")
                        if (isHighToneRepeating) add("고주파")
                    }
                    Text(
                        text = if (repeatingItems.isNotEmpty()) {
                            "반복 송출 중: ${repeatingItems.joinToString(", ")} · [송출 중지]로 해제"
                        } else {
                            "안내: 비프음/저주파/고주파를 3초 길게 누르면 반복 고정"
                        },
                        color = if (repeatingItems.isNotEmpty()) AppColors.Green else AppColors.Gray500,
                        fontSize = scaledSp(10, scale),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(scaledDp(36, scale)))
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = scaledDp(24, scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(scaledDp(36, scale))
                            .padding(start = scaledDp(14, scale)),
                        contentAlignment = Alignment.Center
                    ) {
                        SignalBars(
                            strength = if (isConnected) 4 else 1,
                            variant = SignalVariant.Green,
                            modifier = Modifier.graphicsLayer(rotationX = 180f)
                        )
                    }
                    Spacer(modifier = Modifier.size(scaledDp(36, scale)))
                }
            }
            if (showDebugModal) {
                Dialog(onDismissRequest = { showDebugModal = false }) {
                    Surface(
                        color = AppColors.Gray900,
                        shape = RoundedCornerShape(scaledDp(18, scale)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(24, scale))
                    ) {
                        Column(
                            modifier = Modifier.padding(scaledDp(18, scale)),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "디버그",
                                color = AppColors.White,
                                fontSize = scaledSp(16, scale),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(10, scale)))
                            Text(
                                text = "직접 ${connectedCount}명 · 메쉬 ${meshDisplayCount}명",
                                color = AppColors.Gray400,
                                fontSize = scaledSp(12, scale)
                            )
                            val scanAvg = bleDebugStats.scanRssiAvg?.let { "$it dBm" } ?: "-"
                            val connAvg = bleDebugStats.connectionRssiAvg?.let { "$it dBm" } ?: "-"
                            Text(
                                text = "RSSI scan $scanAvg (${bleDebugStats.scanRssiCount}) · conn $connAvg (${bleDebugStats.connectionRssiCount})",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(11, scale),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "통화 상태: $callStatusLabel",
                                color = AppColors.Gray400,
                                fontSize = scaledSp(11, scale),
                                fontWeight = FontWeight.Medium
                            )
                            if (!callDecisionLabel.isNullOrBlank()) {
                                Text(
                                    text = "결정: $callDecisionLabel",
                                    color = AppColors.Gray500,
                                    fontSize = scaledSp(10, scale)
                                )
                            }
                            Text(
                                text = "pending ${bleDebugStats.pendingCount} · attempts ${bleDebugStats.attemptTracked}/${bleDebugStats.maxAttempts}",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, scale)
                            )
                            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
                            Text(
                                text = "통신 로그",
                                color = AppColors.White,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Bold
                            )
                            val displayedLogs = connectionLogs.takeLast(12)
                            if (displayedLogs.isEmpty()) {
                                Text(
                                    text = "로그 없음",
                                    color = AppColors.Gray500,
                                    fontSize = scaledSp(10, scale)
                                )
                            } else {
                                displayedLogs.forEach { line ->
                                    Text(
                                        text = line,
                                        color = AppColors.Gray400,
                                        fontSize = scaledSp(10, scale)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(scaledDp(16, scale)))
                            Text(
                                text = "닫기",
                                color = AppColors.Green,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { showDebugModal = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .size(scaledDp(36, scale))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(scaledDp(18, scale)),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AppColors.White)
            )
        }
    }
}

@Composable
private fun RepeatActionButton(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    isStickyActive: Boolean = false,
    onStickyStateChanged: (Boolean) -> Unit = {},
    isRepeating: Boolean,
    onActiveStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentTap by rememberUpdatedState(onTap)
    val currentStickyStateChanged by rememberUpdatedState(onStickyStateChanged)
    val currentActiveStateChanged by rememberUpdatedState(onActiveStateChanged)
    var suppressTap by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed, enabled, isStickyActive) {
        if (!enabled) {
            if (isStickyActive) {
                currentStickyStateChanged(false)
            }
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        if (isStickyActive) {
            currentActiveStateChanged(true)
            return@LaunchedEffect
        }
        if (!isPressed) {
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        delay(3_000L)
        if (!isPressed || isStickyActive) return@LaunchedEffect
        // Consume release click once so sticky mode does not immediately toggle off.
        suppressTap = true
        currentStickyStateChanged(true)
        currentActiveStateChanged(true)
    }
    TextButton(
        enabled = enabled,
        onClick = {
            if (suppressTap) {
                suppressTap = false
                return@TextButton
            }
            if (isStickyActive) {
                currentStickyStateChanged(false)
                currentActiveStateChanged(false)
                return@TextButton
            }
            currentTap()
        },
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isRepeating) AppColors.GreenSoft else Color.Transparent,
            contentColor = if (isRepeating) AppColors.Green else AppColors.White
        ),
        modifier = modifier
    ) {
        Text(
            text = if (isRepeating) "$label · 반복중" else label,
            fontSize = scaledSp(11, LocalAppScale.current),
            fontWeight = if (isRepeating) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private data class MeshGraphUiState(
    val nodes: List<MeshNode>,
    val edges: List<MeshEdge>
)

private fun buildMeshGraphState(
    snapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>
): MeshGraphUiState {
    if (snapshot.nodes.isEmpty()) {
        val selfId = myPeerId.trim().ifBlank { "self" }
        val selfLabel = myNickname.trim().ifBlank { selfId }
        return MeshGraphUiState(
            nodes = listOf(
                MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel)
            ),
            edges = emptyList()
        )
    }

    val selfId = myPeerId
    val adjacency = mutableMapOf<String, MutableSet<String>>()
    snapshot.edges.forEach { edge ->
        adjacency.getOrPut(edge.a) { mutableSetOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableSetOf() }.add(edge.a)
    }

    val hops = mutableMapOf<String, Int>()
    val queue = ArrayDeque<String>()
    hops[selfId] = 0
    queue.add(selfId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val nextHop = (hops[current] ?: 0) + 1
        adjacency[current].orEmpty().forEach { neighbor ->
            if (hops.containsKey(neighbor)) return@forEach
            hops[neighbor] = nextHop
            queue.add(neighbor)
        }
    }

    val nodes = mutableListOf<MeshNode>()
    val selfLabel = myNickname.trim().ifBlank { myPeerId.trim().ifBlank { "self" } }
    nodes.add(MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel))

    snapshot.nodes.forEach { node ->
        if (node.peerId == selfId) return@forEach
        val hop = (hops[node.peerId] ?: 2).coerceAtLeast(1)
        val signal = when (hop) {
            1 -> 0.8f
            2 -> 0.55f
            else -> 0.4f
        }
        val label = node.nickname?.takeIf { it.isNotBlank() }
            ?: peerNicknames[node.peerId]
            ?: node.peerId
        nodes.add(
            MeshNode(
                id = node.peerId,
                hop = hop,
                signal = signal,
                label = label
            )
        )
    }

    val edges = snapshot.edges.map { edge ->
        MeshEdge(
            a = edge.a,
            b = edge.b,
            isConfirmed = edge.isConfirmed,
            confirmedBy = edge.confirmedBy
        )
    }

    return MeshGraphUiState(nodes = nodes, edges = edges)
}
