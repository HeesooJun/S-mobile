package com.example.lifesaivior.ui.components.ptt

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaivior.R
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp

@Composable
internal fun PttActionsBlock(
    expandedAction: PttActionType?,
    batteryLevel: Int,
    survivorCount: Int,
    isPowerSaving: Boolean,
    showDoubleTapHint: Boolean,
    onPowerClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onUsersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val cardShape = RoundedCornerShape(scaledDp(16, scale))
    val batteryLabel = when (batteryLevel.coerceIn(0, 100)) {
        in 0..24 -> "충전이 필요해요"
        in 25..49 -> "배터리가 낮아요"
        in 50..74 -> "배터리 보통"
        else -> "배터리 안정"
    }
    val isDisconnectArmed = expandedAction == PttActionType.Disconnect

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(6, scale)),
            horizontalArrangement = Arrangement.spacedBy(scaledDp(12, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttStatusCard(
                modifier = Modifier.weight(1f),
                shape = cardShape,
                iconRes = batteryIconRes(batteryLevel),
                iconSize = scaledDp(40, scale),
                title = "${batteryLevel.coerceIn(0, 100)}%",
                subtitle = batteryLabel
            )

            PttStatusCard(
                modifier = Modifier.weight(1f),
                shape = cardShape,
                iconRes = R.drawable.ic_ptt_connection_filled,
                iconSize = scaledDp(28, scale),
                title = "생존자 네트워크",
                subtitle = "${survivorCount.coerceAtLeast(0)}명 연결됨",
                trailing = "연결 지도 보기",
                onClick = onUsersClick
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(6, scale), vertical = scaledDp(12, scale)),
            horizontalArrangement = Arrangement.spacedBy(scaledDp(12, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttStatusCard(
                modifier = Modifier.weight(1f),
                shape = cardShape,
                iconRes = R.drawable.ic_ptt_connection_lost,
                iconSize = scaledDp(30, scale),
                title = "연결 해제",
                subtitle = if (isDisconnectArmed) "다시 누르면 종료" else "연결을 종료합니다",
                trailing = "안전 종료",
                accent = if (isDisconnectArmed) AppColors.Red else AppColors.White,
                onClick = onDisconnectClick
            )

            PttStatusCard(
                modifier = Modifier.weight(1f),
                shape = cardShape,
                iconRes = R.drawable.ic_ptt_power_off,
                iconSize = scaledDp(30, scale),
                title = "절전 모드",
                subtitle = if (isPowerSaving) "현재 켜짐" else "현재 꺼짐",
                trailing = "터치하여 전환",
                accent = if (isPowerSaving) AppColors.Green else AppColors.White,
                onClick = onPowerClick
            )
        }

        AnimatedVisibility(
            visible = showDoubleTapHint,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            Text(
                text = "연결 해제는 한번 더 눌러주세요",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(y = scaledDp(4, scale))
            )
        }
    }
}

@Composable
private fun PttStatusCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    title: String,
    subtitle: String,
    trailing: String? = null,
    accent: androidx.compose.ui.graphics.Color = AppColors.White,
    onClick: (() -> Unit)? = null
) {
    val scale = LocalAppScale.current

    Column(
        modifier = modifier
            .height(scaledDp(112, scale))
            .background(color = AppColors.Gray800, shape = shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = scaledDp(14, scale), vertical = scaledDp(12, scale)),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit
            )
            trailing?.let {
                Text(
                    text = it,
                    color = AppColors.Gray400,
                    fontSize = scaledSp(9, scale),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(max = scaledDp(70, scale))
                )
            }
        }

        Text(
            text = title,
            color = accent,
            fontSize = scaledSp(14, scale),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = AppColors.Gray400,
            fontSize = scaledSp(11, scale),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun batteryIconRes(level: Int): Int {
    val clamped = level.coerceIn(0, 100)
    return when {
        clamped >= 100 -> R.drawable.ic_common_battery_segment_100
        clamped >= 75 -> R.drawable.ic_common_battery_segment_75
        clamped >= 50 -> R.drawable.ic_common_battery_segment_50
        clamped >= 25 -> R.drawable.ic_common_battery_segment_25
        else -> R.drawable.ic_common_battery_segment_0
    }
}
