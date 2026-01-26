package com.example.lifesaiver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp

enum class SignalVariant {
    Green,
    Gray
}

@Composable
fun SignalBars(
    strength: Int,
    variant: SignalVariant,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val color = when (variant) {
        SignalVariant.Green -> AppColors.Green
        SignalVariant.Gray -> AppColors.Gray500
    }
    val clamped = strength.coerceIn(0, 4)
    val heights = listOf(6, 10, 14, 18).map { scaledDp(it, scale) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(scaledDp(3, scale))) {
        heights.forEachIndexed { index, height ->
            val active = index < clamped
            Surface(
                color = if (active) color else AppColors.Gray700,
                modifier = Modifier
                    .width(scaledDp(4, scale))
                    .height(height)
            ) {}
        }
    }
}
