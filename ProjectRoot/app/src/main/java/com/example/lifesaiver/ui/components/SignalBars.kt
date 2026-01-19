package com.example.lifesaiver.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.ui.theme.AppColors

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
    val color = when (variant) {
        SignalVariant.Green -> AppColors.Green
        SignalVariant.Gray -> AppColors.Gray500
    }
    val clamped = strength.coerceIn(0, 4)
    val heights = listOf(6.dp, 10.dp, 14.dp, 18.dp)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heights.forEachIndexed { index, height ->
            val active = index < clamped
            Surface(
                color = if (active) color else AppColors.Gray700,
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
            ) {}
        }
    }
}
