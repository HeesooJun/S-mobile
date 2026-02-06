package com.example.lifesaivior.ui.screen.survivor.ptt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.lifesaivior.R
import com.example.lifesaivior.core.log.ConnectionLog
import com.example.lifesaivior.presentation.BleDebugStats
import com.example.lifesaivior.presentation.MeshVisualEvent
import com.example.lifesaivior.ui.components.BatteryIndicator
import com.example.lifesaivior.ui.components.MeshNode
import com.example.lifesaivior.ui.components.MeshEdge
import com.example.lifesaivior.ui.components.MeshMap
import com.example.lifesaivior.ui.components.MicButton
import com.example.lifesaivior.ui.components.tripleClickable
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.components.SignalBars
import com.example.lifesaivior.ui.components.SignalVariant
import com.example.lifesaivior.ui.components.PowerSavingLayer
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    isConnected: Boolean,
    isMicOn: Boolean,
    isCallConnected: Boolean,
    isInCall: Boolean,
    isSpeakerphoneOn: Boolean = true,
    callPeerName: String? = null,
    pendingCall: SurvivorCallRequest? = null,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    onProfile: () -> Unit,
    onPanicClear: () -> Unit,
    onToggleSpeakerphone: () -> Unit = {},
    onAcceptCall: () -> Unit = {},
    onDeclineCall: () -> Unit = {},
    onOpenUserList: (() -> Unit)? = null
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    val (expandedAction, setExpandedAction) = remember { mutableStateOf<ActionType?>(null) }
    val (showDoubleTapHint, setShowDoubleTapHint) = remember { mutableStateOf(false) }
    var showMeshMap by remember { mutableStateOf(false) }
    var showDebugModal by remember { mutableStateOf(false) }
    val connectionLogs by ConnectionLog.logs.collectAsState()
    val showActionLabelsAlways = true
    val meshDisplayCount = meshPeerCount.coerceAtLeast(0)
    val displayConnectedCount = meshDisplayCount
    val hasMeshPeers = meshDisplayCount > 0
    val isLinkActive = isConnected || hasMeshPeers
    val meshGraphState = remember(meshGraphSnapshot, myPeerId, myNickname, peerNicknames) {
        buildMeshGraphState(
            snapshot = meshGraphSnapshot,
            myPeerId = myPeerId,
            myNickname = myNickname,
            peerNicknames = peerNicknames
        )
    }
    val canShowMeshMap = onOpenUserList == null

    LaunchedEffect(expandedAction) {
        if (
            expandedAction == ActionType.Chat ||
            expandedAction == ActionType.Disconnect ||
            expandedAction == ActionType.Power ||
            expandedAction == ActionType.Count
        ) {
            setShowDoubleTapHint(true)
            delay(3500)
            setShowDoubleTapHint(false)
        } else {
            setShowDoubleTapHint(false)
        }
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                // 완전 해제 개념이 없으면 아래처럼 단순 처리해도 됨
                isForceExit = !isPowerSaving,
                onRequestExitPowerSaving = { setPowerSaving(false) }
            )
            Text(
                text = "LIFESAIVIOR",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(20, scale), top = scaledDp(18, scale))
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
                Spacer(modifier = Modifier.weight(0.5f))
                BatteryIndicator(level = batteryLevel)
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                Text(
                    text = "$batteryLevel%",
                    color = AppColors.White,
                    fontSize = scaledSp(54, scale),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "약 36시간 대기 가능",
                    color = AppColors.Gray500,
                    fontSize = scaledSp(12, scale)
                )
                Spacer(modifier = Modifier.height(scaledDp(80, scale)))
                MicButton(
                    isActive = isMicOn && !isInCall,
                    size = scaledDp(80, scale),
                    onPress = { if (!isInCall) onMicPress() },
                    onRelease = { if (!isInCall) onMicRelease() }
                )
                Spacer(modifier = Modifier.height(scaledDp(20, scale)))
                val callLabel = callPeerName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Text(
                    text = if (isInCall) {
                        if (isCallConnected) "실시간 통화 연결됨$callLabel" else "실시간 통화 연결 중$callLabel"
                    } else {
                        when {
                            hasMeshPeers -> "메쉬 연결됨"
                            isConnected -> "구조자 연결됨"
                            else -> "구조자 연결 대기 중"
                        }
                    },
                    color = if (isInCall) {
                        if (isCallConnected) AppColors.Green else AppColors.Yellow
                    } else {
                        if (isLinkActive) AppColors.Green else AppColors.Gray500
                    },
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
                if (!isInCall && pendingCall != null) {
                    Spacer(modifier = Modifier.height(scaledDp(16, scale)))
                    CallRequestCard(
                        callerName = pendingCall.callerName,
                        onAccept = onAcceptCall,
                        onDecline = onDeclineCall
                    )
                }
                Spacer(modifier = Modifier.height(scaledDp(18, scale)))
                Spacer(modifier = Modifier.weight(0.6f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(8, scale)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpandableAction(
                            iconRes = R.drawable.ic_power_off,
                            label = "절전 모드",
                            isExpanded = expandedAction == ActionType.Power,
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setPowerSaving(!isPowerSaving)
                                setExpandedAction(ActionType.Power)
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.ic_sound,
                            label = if (isSpeakerphoneOn) "스피커 ON" else "스피커 OFF",
                            isExpanded = expandedAction == ActionType.Speaker,
                            iconSizeOverride = scaledDp(34, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setExpandedAction(ActionType.Speaker)
                                onToggleSpeakerphone()
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.connection_lost,
                            label = "연결 끊기",
                            isExpanded = expandedAction == ActionType.Disconnect,
                            iconSizeOverride = scaledDp(44, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (expandedAction == ActionType.Disconnect) {
                                    setExpandedAction(null)
                                    onDisconnect()
                                } else {
                                    setExpandedAction(ActionType.Disconnect)
                                }
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.ic_chat,
                            label = "채팅",
                            isExpanded = expandedAction == ActionType.Chat,
                            iconSizeOverride = scaledDp(38, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (expandedAction == ActionType.Chat) {
                                    setExpandedAction(null)
                                    onChat()
                                } else {
                                    setExpandedAction(ActionType.Chat)
                                }
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.connection_filled,
                            label = if (onOpenUserList == null) {
                                "사용자 $displayConnectedCount"
                            } else {
                                "사용자 DB $displayConnectedCount"
                            },
                            isExpanded = expandedAction == ActionType.Count,
                            iconSizeOverride = scaledDp(32, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (onOpenUserList != null) {
                                    onOpenUserList()
                                } else if (expandedAction == ActionType.Count) {
                                    setExpandedAction(null)
                                    showMeshMap = true
                                } else {
                                    setExpandedAction(ActionType.Count)
                                }
                            }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .height(scaledDp(32, scale))
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        AnimatedVisibility(
                            visible = showDoubleTapHint,
                            enter = fadeIn() + slideInVertically { it / 3 },
                            exit = fadeOut() + slideOutVertically { it / 3 }
                        ) {
                            Text(
                                text = "한번 더 눌러주세요",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.offset(y = scaledDp(6, scale))
                            )
                        }
                    }
                }

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
                    ProfileActionButton(
                        label = "내정보",
                        onClick = onProfile
                    )
                }
            }

            if (showMeshMap && canShowMeshMap) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black.copy(alpha = 0.9f))
                ) {
                    MeshMap(
                        nodes = meshGraphState.nodes,
                        edges = meshGraphState.edges,
                        visualEvents = meshVisualEvents,
                        modifier = Modifier.fillMaxSize()
                    )
                    TopIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "닫기",
                        onClick = { showMeshMap = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = scaledDp(16, scale), top = scaledDp(16, scale))
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = scaledDp(18, scale)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "메쉬 현황",
                            color = AppColors.White,
                            fontSize = scaledSp(16, scale),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "직접 ${connectedCount}명 · 메쉬 ${meshDisplayCount}명",
                            color = AppColors.Gray400,
                            fontSize = scaledSp(12, scale)
                        )
                    }
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
                            val callLabel = callPeerName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                            val callStatus = if (isInCall) {
                                if (isCallConnected) "연결됨$callLabel" else "연결 중$callLabel"
                            } else {
                                "대기"
                            }
                            Text(
                                text = "통화 상태: $callStatus",
                                color = AppColors.Gray400,
                                fontSize = scaledSp(11, scale),
                                fontWeight = FontWeight.Medium
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
private fun ExpandableAction(
    iconRes: Int,
    label: String,
    isExpanded: Boolean,
    iconSizeOverride: Dp? = null,
    showLabelAlways: Boolean = false,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val baseSize = scaledDp(48, scale)
    val labelHeight = scaledDp(16, scale)
    val totalHeight = baseSize + labelHeight
    val iconSize = iconSizeOverride ?: scaledDp(36, scale)
    Column(
        modifier = Modifier
            .width(baseSize)
            .height(totalHeight)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(baseSize)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AppColors.White)
            )
        }
        Box(
            modifier = Modifier
                .height(labelHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (showLabelAlways || isExpanded) {
                Text(
                    text = label,
                    color = AppColors.White,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class ActionType {
    Power,
    Speaker,
    Disconnect,
    Chat,
    Count
}

data class SurvivorCallRequest(
    val callerName: String,
    val wifiAware: Boolean,
    val wifiDirect: Boolean,
    val useOpus: Boolean
)

@Composable
private fun CallRequestCard(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scale = LocalAppScale.current
    Surface(
        color = AppColors.Gray900,
        shape = RoundedCornerShape(scaledDp(18, scale))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(14, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$callerName 님이 통화를 요청했습니다.",
                color = AppColors.White,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(scaledDp(10, scale)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(scaledDp(12, scale))
            ) {
                OutlinedButton(onClick = onDecline) {
                    Text("거절")
                }
                Button(onClick = onAccept) {
                    Text("수락")
                }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    label: String,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    Row(
        modifier = Modifier
            .background(
                color = AppColors.Gray800,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(
                start = scaledDp(12, scale),
                end = scaledDp(12, scale),
                top = scaledDp(8, scale),
                bottom = scaledDp(8, scale)
            )
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
    ) {
        Text(
            text = label,
            color = AppColors.Gray400,
            fontSize = scaledSp(13, scale),
            fontWeight = FontWeight.Medium
        )
    }
}

private data class MeshGraphUiState(
    val nodes: List<MeshNode>,
    val edges: List<MeshEdge>
)

private fun buildMeshGraphState(
    snapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
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
