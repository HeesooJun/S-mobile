package com.example.lifesaiver.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp

@Composable
fun BatteryIndicator(
    level: Int,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val clamped = level.coerceIn(0, 100)
    val iconRes = when {
        clamped >= 100 -> R.drawable.battery_segment_100
        clamped >= 75 -> R.drawable.battery_segment_75
        clamped >= 50 -> R.drawable.battery_segment_50
        clamped >= 25 -> R.drawable.battery_segment_25
        else -> R.drawable.battery_segment_0
    }
    val size = scaledDp(110, scale)

    Image(
        painter = painterResource(id = iconRes),
        contentDescription = "Battery",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .offset(x = scaledDp(-6, scale))
            .size(size)
    )
}
