package com.example.lifesaiver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

enum class SecondaryButtonVariant {
    Gray,
    Red
}

@Composable
fun SecondaryButton(
    label: String,
    variant: SecondaryButtonVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val borderColor = when (variant) {
        SecondaryButtonVariant.Gray -> AppColors.Gray700
        SecondaryButtonVariant.Red -> AppColors.Red
    }
    val contentColor = when (variant) {
        SecondaryButtonVariant.Gray -> AppColors.Gray400
        SecondaryButtonVariant.Red -> AppColors.Red
    }

    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(
            horizontal = scaledDp(16, scale),
            vertical = scaledDp(8, scale)
        ),
        shape = RoundedCornerShape(scaledDp(18, scale)),
        modifier = modifier
    ) {
        Text(text = label, fontSize = scaledSp(13, scale), fontWeight = FontWeight.SemiBold)
    }
}
