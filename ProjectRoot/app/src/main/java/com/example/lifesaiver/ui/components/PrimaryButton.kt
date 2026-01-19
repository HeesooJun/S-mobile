package com.example.lifesaiver.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.ui.theme.AppColors

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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
