package com.example.lifesaiver.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
fun MicButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val scale = LocalAppScale.current
    val contentColor = if (isActive) AppColors.Green else AppColors.Red
    val micRes = if (isActive) R.drawable.ic_mic else R.drawable.ic_mic_red
    val micSize = size ?: scaledDp(88, scale)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = micRes),
            contentDescription = if (isActive) "MIC ON" else "MIC OFF",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(micSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            try {
                                awaitRelease()
                            } finally {
                                onRelease()
                            }
                        }
                    )
                }
        )
        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        Text(
            text = if (isActive) "ON" else "OFF",
            color = contentColor,
            fontSize = scaledSp(12, scale),
            fontWeight = FontWeight.Bold
        )
    }
}
