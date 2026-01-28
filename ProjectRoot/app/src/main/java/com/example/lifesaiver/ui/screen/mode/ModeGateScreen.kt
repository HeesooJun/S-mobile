package com.example.lifesaiver.ui.screen.mode


import androidx.compose.foundation.Image
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.presentation.screen.ModeGateUiState
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
fun ModeGateScreen(
    batteryLevel: Int,
    uiState: ModeGateUiState,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onRescuerMode: () -> Unit,
    onStartSensorMonitor: () -> Unit
) {
    val scale = LocalAppScale.current
    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = scaledDp(32, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = scaledDp(16, scale)),
                horizontalArrangement = Arrangement.End
            ) {
                SecondaryButton(
                    label = "센서 작동 시작",
                    variant = SecondaryButtonVariant.Gray,
                    onClick = onStartSensorMonitor
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            val circleSize = scaledDp(130, scale)

            Surface(
                shape = CircleShape,
                color = AppColors.White,
                modifier = Modifier.size(circleSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.lifesaivior_logo),
                        contentDescription = "LifesAIvior Logo",
                        modifier = Modifier.size(circleSize * 0.62f),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))
            Text(
                text = uiState.appName,
                color = AppColors.White,
                fontSize = scaledSp(30, scale),
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = uiState.tagline,
                color = AppColors.Gray500,
                fontSize = scaledSp(13, scale)
            )
            Spacer(modifier = Modifier.height(scaledDp(44, scale)))

            PrimaryButton(
                label = uiState.rescuerLabel,
                variant = PrimaryButtonVariant.Gray,
                modifier = Modifier.fillMaxWidth(0.7f),
                onClick = onRescuerMode
            )

            Spacer(modifier = Modifier.height(scaledDp(36, scale)))
            Text(
                text = uiState.questionLabel,
                color = AppColors.Red,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(scaledDp(16, scale))
            ) {
                PrimaryButton(
                    label = uiState.yesLabel,
                    variant = PrimaryButtonVariant.Red,
                    modifier = Modifier.weight(1f),
                    onClick = onYes
                )
                PrimaryButton(
                    label = uiState.noLabel,
                    variant = PrimaryButtonVariant.Gray,
                    modifier = Modifier.weight(1f),
                    onClick = onNo
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
