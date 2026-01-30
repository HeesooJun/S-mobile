package com.example.lifesaiver.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

enum class PrimaryButtonVariant {
    Gray,
    Red,
    Green
}

@Composable
fun PrimaryButton(
    label: String,
    variant: PrimaryButtonVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val colors = when (variant) {
        PrimaryButtonVariant.Gray -> ButtonDefaults.buttonColors(
            containerColor = AppColors.Gray800,
            contentColor = AppColors.White
        )
        PrimaryButtonVariant.Red -> ButtonDefaults.buttonColors(
            containerColor = AppColors.Red,
            contentColor = AppColors.White
        )
        PrimaryButtonVariant.Green -> ButtonDefaults.buttonColors(
            containerColor = AppColors.Green,
            contentColor = AppColors.Black
        )
    }

    Button(
        onClick = onClick,
        colors = colors,
        contentPadding = PaddingValues(
            horizontal = scaledDp(20, scale),
            vertical = scaledDp(14, scale)
        ),
        shape = RoundedCornerShape(scaledDp(18, scale)),
        modifier = modifier
    ) {
        Text(text = label, fontSize = scaledSp(16, scale), fontWeight = FontWeight.SemiBold)
    }
}
