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
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.ui.theme.AppColors

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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
