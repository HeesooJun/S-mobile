package com.example.lifesaiver.ui.screen.survivor.ptt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.R
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.ui.components.MeshNode
import com.example.lifesaiver.ui.components.MeshEdge
import com.example.lifesaiver.ui.components.MeshMap
import com.example.lifesaiver.ui.components.MicButton
import com.example.lifesaiver.ui.components.tripleClickable
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.components.PowerSavingLayer
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.material3.Icon

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    isConnected: Boolean,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    onProfile: () -> Unit,
    onPanicClear: () -> Unit,
    onSettings: () -> Unit
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    val (expandedAction, setExpandedAction) = remember { mutableStateOf<ActionType?>(null) }
    val (showDoubleTapHint, setShowDoubleTapHint) = remember { mutableStateOf(false) }
    var showMeshMap by remember { mutableStateOf(false) }
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

    LaunchedEffect(expandedAction) {
        if (expandedAction == ActionType.Disconnect) {
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
                text = "LIFESAIVER",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(20, scale), top = scaledDp(18, scale))
                    .tripleClickable(onTripleClick = onPanicClear)
            )
            Icon(
                // 프로젝트에 ic_settings 아이콘이 없다면 추가하거나, R.drawable.ic_gear 등으로 변경하세요.
                painter = painterResource(id = R.drawable.ic_ptt_settings),
                contentDescription = "설정",
                tint = AppColors.Gray500, // 로고 텍스트와 톤을 맞춰 자연스럽게 배치
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = scaledDp(20, scale), top = scaledDp(18, scale))
                    .size(scaledDp(24, scale)) // 터치 영역 확보를 위해 padding을 줄이고 size를 키워도 됨
                    .clickable { onSettings() }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = scaledDp(32, scale))
                    .offset(y = scaledDp(16, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RipplePulse(
                    isActive = isMicOn,
                    isConnected = isLinkActive,
                    modifier = Modifier
                        .size(scaledDp(72, scale))
                        .offset(y = scaledDp(-40, scale))
                )
                Spacer(modifier = Modifier.height(scaledDp(36, scale)))
                MicButton(
                    isActive = isMicOn,
                    modifier = Modifier.offset(y = scaledDp(-10, scale)),
                    size = scaledDp(92, scale),
                    onPress = onMicPress,
                    onRelease = onMicRelease
                )
                Spacer(modifier = Modifier.height(scaledDp(20, scale)))
                Text(
                    text = when {
                        hasMeshPeers -> "메쉬 연결됨"
                        isConnected -> "구조자 연결됨"
                        else -> "구조자 연결 대기 중"
                    },
                    color = if (isLinkActive) AppColors.Green else AppColors.Gray500,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(scaledDp(44, scale)))
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
                            iconRes = R.drawable.ic_ptt_power_off,
                            label = "절전 모드",
                            isExpanded = expandedAction == ActionType.Power,
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setPowerSaving(!isPowerSaving)
                                setExpandedAction(ActionType.Power)
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.ic_ptt_connection_lost,
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
                            iconRes = R.drawable.ic_ptt_chat,
                            label = "채팅",
                            isExpanded = expandedAction == ActionType.Chat,
                            iconSizeOverride = scaledDp(38, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setExpandedAction(null)
                                onChat()
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.ic_ptt_connection_filled,
                            label = "사용자 $displayConnectedCount",
                            isExpanded = expandedAction == ActionType.Count,
                            iconSizeOverride = scaledDp(32, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setExpandedAction(null)
                                showMeshMap = true
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
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = scaledDp(32, scale),
                        end = scaledDp(32, scale),
                        bottom = scaledDp(24, scale)
                    ),
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

            if (showMeshMap) {
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
                        iconRes = R.drawable.ic_common_back,
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
                    }
                }
            }
        }
    }

}

@Composable
private fun RipplePulse(
    isActive: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pttRipple")
    val waveA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveA"
    )
    val waveB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(420)
        ),
        label = "waveB"
    )
    val waveC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(840)
        ),
        label = "waveC"
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val waveColor = when {
        isActive -> Color(0xFF66F2B2)
        isConnected -> Color(0xFF57D89E)
        else -> Color(0xFF4C8A72)
    }
    val coreAlpha = if (isActive) 0.34f else 0.20f
    val strokeBaseAlpha = if (isActive) 0.62f else 0.34f

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = if (isActive) pulseScale else 1f
            scaleY = if (isActive) pulseScale else 1f
        }
    ) {
        val diameter = size.minDimension
        val coreRadius = diameter * 0.18f
        val maxWaveSpread = diameter * 0.48f
        val strokeWidth = diameter * 0.07f

        fun drawWave(progress: Float) {
            val radius = coreRadius + maxWaveSpread * progress
            val alpha = (1f - progress) * strokeBaseAlpha
            drawCircle(
                color = waveColor.copy(alpha = alpha),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )
        }

        drawCircle(
            color = waveColor.copy(alpha = coreAlpha),
            radius = coreRadius
        )
        drawWave(waveA)
        drawWave(waveB)
        drawWave(waveC)
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
    Disconnect,
    Chat,
    Count
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
