package com.example.lifesaiver.ui.screen.rescuer.ptt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.components.DistanceTrack
import com.example.lifesaiver.ui.components.DistanceTrend
import com.example.lifesaiver.ui.components.MicButton
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

@Composable
fun RescuerPTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    onOpenSurvivorDb: () -> Unit,
    onPanicClear: () -> Unit
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    val (expandedAction, setExpandedAction) = remember { mutableStateOf<ActionType?>(null) }
    val (showDoubleTapHint, setShowDoubleTapHint) = remember { mutableStateOf(false) }
    val showActionLabelsAlways = true
    val meshDisplayCount = meshPeerCount.coerceAtLeast(0)
    val displayConnectedCount = meshDisplayCount
    val hasMeshPeers = meshDisplayCount > 0
    val isLinkActive = isConnected || hasMeshPeers

    LaunchedEffect(expandedAction) {
        if (
            expandedAction == ActionType.Chat ||
            expandedAction == ActionType.Disconnect ||
            expandedAction == ActionType.Power
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
        Box(modifier = Modifier.fillMaxWidth()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                // 절전 해제 컨텐츠가 없으면 원래처럼 단순 처리라도 유지
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(32, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TODO: 거리 데이터 연동 시 실제 값으로 교체
                val distanceMeters: Float? = null
                // val distanceMeters: Float? = 12.4f
                val trend: DistanceTrend = DistanceTrend.Approaching

                Spacer(modifier = Modifier.height(scaledDp(30, scale)))
                DistanceTrack(
                    distanceMeters = distanceMeters,
                    trend = trend,
                    maxMeters = 30f,
                    modifier = Modifier.padding(top = scaledDp(12, scale))
                )

                Spacer(modifier = Modifier.height(scaledDp(140, scale)))
                MicButton(
                    isActive = isMicOn,
                    size = scaledDp(80, scale),
                    onPress = onMicPress,
                    onRelease = onMicRelease
                )
                Spacer(modifier = Modifier.height(scaledDp(20, scale)))
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
                Spacer(modifier = Modifier.height(scaledDp(36, scale)))
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
                            label = "사용자 DB $displayConnectedCount",
                            isExpanded = expandedAction == ActionType.Count,
                            iconSizeOverride = scaledDp(32, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                onOpenSurvivorDb()
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
                    Spacer(modifier = Modifier.size(scaledDp(36, scale)))
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
    Disconnect,
    Chat,
    Count
}
