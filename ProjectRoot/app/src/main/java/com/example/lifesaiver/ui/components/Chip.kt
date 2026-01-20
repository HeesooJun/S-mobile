package com.example.lifesaiver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

enum class ChipVariant {
    Green,
    Gray
}

@Composable
fun Chip(
    label: String,
    variant: ChipVariant,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val background = when (variant) {
        ChipVariant.Green -> AppColors.GreenSoft
        ChipVariant.Gray -> AppColors.Gray700
    }
    val border = when (variant) {
        ChipVariant.Green -> AppColors.Green
        ChipVariant.Gray -> AppColors.Gray500
    }
    val textColor = when (variant) {
        ChipVariant.Green -> AppColors.Green
        ChipVariant.Gray -> AppColors.Gray400
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = scaledSp(11, scale),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = scaledDp(10, scale),
                vertical = scaledDp(6, scale)
            )
        )
    }
}
