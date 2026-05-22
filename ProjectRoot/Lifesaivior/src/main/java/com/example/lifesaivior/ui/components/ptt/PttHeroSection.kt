package com.example.lifesaivior.ui.components.ptt

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import com.example.lifesaivior.ui.components.MicButton
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp

@Composable
internal fun PttHeroSection(
    isMicOn: Boolean,
    isLinkActive: Boolean,
    statusLabel: String,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PttRipplePulse(
            isActive = isMicOn,
            isConnected = isLinkActive,
            modifier = Modifier
                .size(scaledDp(72, scale))
                .offset(y = scaledDp(-40, scale))
        )
        Spacer(modifier = Modifier.height(scaledDp(36, scale)))
        MicButton(
            isActive = isMicOn,
            modifier = Modifier.offset(y = scaledDp(-4, scale)),
            size = scaledDp(92, scale),
            onPress = onMicPress,
            onRelease = onMicRelease
        )
        Spacer(modifier = Modifier.height(scaledDp(20, scale)))
        Text(
            text = statusLabel,
            color = if (isLinkActive) AppColors.Green else AppColors.Gray500,
            fontSize = scaledSp(14, scale),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PttRipplePulse(
    isActive: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pttRipple")
    val waveA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveA"
    )
    val waveB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(420)
        ),
        label = "waveB"
    )
    val waveC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(840)
        ),
        label = "waveC"
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val waveColor = when {
        isActive -> Color(0xFF66F2B2)
        isConnected -> Color(0xFF57D89E)
        else -> Color(0xFF4C8A72)
    }
    val coreAlpha = if (isActive) 0.34f else 0.20f
    val strokeBaseAlpha = if (isActive) 0.62f else 0.34f

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = if (isActive) pulseScale else 1f
            scaleY = if (isActive) pulseScale else 1f
        }
    ) {
        val diameter = size.minDimension
        val coreRadius = diameter * 0.18f
        val maxWaveSpread = diameter * 0.48f
        val strokeWidth = diameter * 0.07f

        fun drawWave(progress: Float) {
            val radius = coreRadius + maxWaveSpread * progress
            val alpha = (1f - progress) * strokeBaseAlpha
            drawCircle(
                color = waveColor.copy(alpha = alpha),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )
        }

        drawCircle(
            color = waveColor.copy(alpha = coreAlpha),
            radius = coreRadius
        )
        drawWave(waveA)
        drawWave(waveB)
        drawWave(waveC)
    }
}
