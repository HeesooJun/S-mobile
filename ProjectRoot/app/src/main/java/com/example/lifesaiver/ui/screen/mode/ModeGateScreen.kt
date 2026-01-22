package com.example.lifesaiver.ui.screen.mode

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.presentation.screen.ModeGateUiState
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
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
    onRescuerMode: () -> Unit
) {
    val scale = LocalAppScale.current
    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
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
                text = uiState.appName,
                color = AppColors.White,
                fontSize = scaledSp(18, scale),
                fontWeight = FontWeight.Bold
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
            Surface(
                shape = CircleShape,
                color = AppColors.White,
                modifier = Modifier.size(scaledDp(130, scale))
            ) {}
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
