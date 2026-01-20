package com.example.lifesaiver.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

enum class RecordState {
    Idle,
    Recording
}

@Composable
fun RecordButton(
    state: RecordState,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val borderColor = if (state == RecordState.Recording) AppColors.Red else AppColors.Green
    val label = if (state == RecordState.Recording) "REC" else "MIC"

    Surface(
        shape = CircleShape,
        color = AppColors.Gray800,
        border = BorderStroke(scaledDp(2, scale), borderColor),
        modifier = modifier.size(scaledDp(40, scale))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = borderColor,
                fontSize = scaledSp(10, scale),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
