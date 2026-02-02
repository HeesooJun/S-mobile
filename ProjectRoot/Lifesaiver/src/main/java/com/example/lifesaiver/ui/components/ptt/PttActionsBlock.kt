package com.example.lifesaiver.ui.components.ptt

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
internal fun PttActionsBlock(
    expandedAction: PttActionType?,
    displayConnectedCount: Int,
    showDoubleTapHint: Boolean,
    onPowerClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onUsersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(8, scale)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttExpandableAction(
                iconRes = R.drawable.ic_ptt_power_off,
                label = "절전 모드",
                isExpanded = expandedAction == PttActionType.Power,
                onClick = onPowerClick
            )
            PttExpandableAction(
                iconRes = R.drawable.ic_ptt_connection_lost,
                label = "연결 끊기",
                isExpanded = expandedAction == PttActionType.Disconnect,
                iconSizeOverride = scaledDp(44, scale),
                onClick = onDisconnectClick
            )
            PttExpandableAction(
                iconRes = R.drawable.ic_ptt_connection_filled,
                label = "사용자 $displayConnectedCount",
                isExpanded = expandedAction == PttActionType.Count,
                iconSizeOverride = scaledDp(32, scale),
                onClick = onUsersClick
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

@Composable
private fun PttExpandableAction(
    iconRes: Int,
    label: String,
    isExpanded: Boolean,
    iconSizeOverride: androidx.compose.ui.unit.Dp? = null,
    showLabelAlways: Boolean = true,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val baseSize = scaledDp(48, scale)
    val labelHeight = scaledDp(20, scale)
    val iconSize = iconSizeOverride ?: scaledDp(36, scale)

    Column(
        modifier = Modifier
            .width(baseSize)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
                .heightIn(min = labelHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
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
