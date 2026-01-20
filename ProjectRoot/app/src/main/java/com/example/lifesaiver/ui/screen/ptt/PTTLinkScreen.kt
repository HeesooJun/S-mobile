package com.example.lifesaiver.ui.screen.ptt

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.lifesaiver.ui.components.BatteryIndicator
import com.example.lifesaiver.ui.components.Chip
import com.example.lifesaiver.ui.components.ChipVariant
import com.example.lifesaiver.ui.components.MicButton
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import com.example.lifesaiver.R
import kotlinx.coroutines.delay

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    onToggleMic: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    val (expandedAction, setExpandedAction) = remember { mutableStateOf<ActionType?>(null) }
    val (showDoubleTapHint, setShowDoubleTapHint) = remember { mutableStateOf(false) }
    val showActionLabelsAlways = true
    val displayConnectedCount = (connectedCount - 1).coerceAtLeast(0)

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale), vertical = scaledDp(24, scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIconButton(
                iconRes = R.drawable.arrow,
                contentDescription = "뒤로",
                onClick = onBack
            )
            Text(
                text = "$batteryLevel%",
                color = AppColors.Gray400,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
            Spacer(modifier = Modifier.height(scaledDp(28, scale)))
            MicButton(
                isActive = isMicOn,
                size = scaledDp(80, scale),
                onToggle = onToggleMic
            )
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))
            Text(
                text = if (isConnected) "구조자와 연결 확인" else "연결 대기 중",
                color = if (isConnected) AppColors.Green else AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.SemiBold
            )
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
                        label = "사용자 $displayConnectedCount",
                        isExpanded = expandedAction == ActionType.Count,
                        iconSizeOverride = scaledDp(32, scale),
                        showLabelAlways = showActionLabelsAlways,
                        onClick = {
                            setExpandedAction(ActionType.Count)
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
                    .padding(bottom = scaledDp(18, scale)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalBars(strength = if (isConnected) 4 else 1, variant = SignalVariant.Green)
                Chip(label = "센서 상태 정상 작동", variant = ChipVariant.Green)
            }
        }
    }

}

@Composable
private fun TopIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
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
