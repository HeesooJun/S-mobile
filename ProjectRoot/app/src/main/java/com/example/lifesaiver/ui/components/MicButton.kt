package com.example.lifesaiver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun MicButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val borderColor = if (isActive) AppColors.Red else AppColors.Green
    val contentColor = if (isActive) AppColors.Red else AppColors.Green
    val background = if (isActive) AppColors.RedSoft else AppColors.GreenSoft

    Surface(
        shape = CircleShape,
        color = background,
        border = BorderStroke(3.dp, borderColor),
        modifier = modifier
            .size(110.dp)
            .clickable { onToggle() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isActive) "MIC ON" else "MIC OFF",
                color = contentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
