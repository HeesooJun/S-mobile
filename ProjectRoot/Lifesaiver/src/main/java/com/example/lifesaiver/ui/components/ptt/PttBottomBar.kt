package com.example.lifesaiver.ui.components.ptt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
internal fun PttBottomBar(
    isConnected: Boolean,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(scaledDp(12, scale))
                .padding(start = scaledDp(14, scale)),
            contentAlignment = Alignment.Center
        ) {
            SignalBars(
                strength = if (isConnected) 4 else 1,
                variant = SignalVariant.Green,
                modifier = Modifier.graphicsLayer(rotationX = 180f)
            )
        }
        PttProfileActionButton(label = "내정보", onClick = onProfile)
    }
}

@Composable
private fun PttProfileActionButton(
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
                top = scaledDp(4, scale),
                bottom = scaledDp(4, scale)
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
