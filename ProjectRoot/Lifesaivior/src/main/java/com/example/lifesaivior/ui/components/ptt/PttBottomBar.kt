package com.example.lifesaivior.ui.components.ptt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.lifesaivior.R
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp

internal enum class PttBottomTab {
    Home,
    Chat,
    Settings
}

@Composable
internal fun PttBottomBar(
    selectedTab: PttBottomTab,
    onHome: () -> Unit,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = AppColors.Gray900.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(
                    horizontal = scaledDp(20, scale),
                    vertical = scaledDp(2, scale)
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttNavItem(
                iconRes = R.drawable.ic_nav_home,
                isSelected = selectedTab == PttBottomTab.Home,
                onClick = onHome
            )
            PttNavItem(
                iconRes = R.drawable.ic_nav_chat,
                isSelected = selectedTab == PttBottomTab.Chat,
                onClick = onChat
            )
            PttNavItem(
                iconRes = R.drawable.ic_nav_settings,
                isSelected = selectedTab == PttBottomTab.Settings,
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun PttNavItem(
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    Box(
        modifier = Modifier
            .size(width = scaledDp(74, scale), height = scaledDp(44, scale))
            .background(
                color = if (isSelected) AppColors.Gray800 else AppColors.Gray900.copy(alpha = 0.05f),
                shape = RoundedCornerShape(scaledDp(14, scale))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(scaledDp(30, scale))
                .alpha(if (isSelected) 1f else 0.82f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(scaledDp(30, scale)),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(if (isSelected) AppColors.White else AppColors.Gray500)
            )
        }
    }
}
