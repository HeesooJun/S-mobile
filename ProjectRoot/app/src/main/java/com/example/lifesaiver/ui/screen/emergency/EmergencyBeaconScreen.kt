package com.example.lifesaiver.ui.screen.emergency

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.R
import com.example.lifesaiver.presentation.screen.EmergencyBeaconUiState
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
fun EmergencyBeaconScreen(
    batteryLevel: Int,
    uiState: EmergencyBeaconUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val scale = LocalAppScale.current
    ScreenScaffold(
        gradient = listOf(AppColors.Black, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledDp(56, scale))
                .padding(horizontal = scaledDp(32, scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.headerLabel,
                color = AppColors.Red,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Medium
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = scaledDp(32, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            val pulseTransition = rememberInfiniteTransition(label = "sosPulse")
            val pulseScale = pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                ),
                label = "pulseScale"
            ).value
            val pulseAlpha = pulseTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
                ),
                label = "pulseAlpha"
            ).value

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(scaledDp(96, scale))
                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale, alpha = pulseAlpha)
                        .border(
                            width = scaledDp(2, scale),
                            color = AppColors.Red.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_sound),
                    contentDescription = "구조 신호",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(scaledDp(56, scale))
                )
            }
            Spacer(modifier = Modifier.height(scaledDp(24, scale)))
            Text(
                text = uiState.titleLabel,
                color = AppColors.White,
                fontSize = scaledSp(28, scale),
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            Text(
                text = uiState.subtitlePrimary,
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale)
            )
            Text(
                text = uiState.subtitleSecondary,
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale)
            )

            Spacer(modifier = Modifier.height(scaledDp(36, scale)))
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = scaledDp(24, scale)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(label = uiState.prevLabel, variant = SecondaryButtonVariant.Gray, onClick = onPrev)
                SecondaryButton(label = uiState.nextLabel, variant = SecondaryButtonVariant.Red, onClick = onNext)
            }
        }
    }
}
