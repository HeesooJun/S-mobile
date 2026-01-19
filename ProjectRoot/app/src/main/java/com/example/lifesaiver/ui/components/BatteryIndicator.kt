package com.example.lifesaiver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun BatteryIndicator(
    level: Int,
    modifier: Modifier = Modifier
) {
    val clamped = level.coerceIn(0, 100)
    val bodyWidth = 130.dp
    val bodyHeight = 24.dp
    val capWidth = 6.dp
    val capHeight = 14.dp
    val borderWidth = 2.dp
    val innerPadding = 4.dp
    val fillWidth = (bodyWidth - innerPadding * 2) * (clamped / 100f)
    val bodyShape = RoundedCornerShape(8.dp)
    val fillShape = RoundedCornerShape(6.dp)
    val capShape = RoundedCornerShape(3.dp)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(bodyWidth)
                .height(bodyHeight)
                .border(borderWidth, AppColors.White, bodyShape)
                .background(AppColors.Gray900, bodyShape)
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .background(AppColors.White, fillShape)
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(width = capWidth, height = capHeight)
                .border(borderWidth, AppColors.White, capShape)
                .background(AppColors.Gray900, capShape)
        )
    }
}
