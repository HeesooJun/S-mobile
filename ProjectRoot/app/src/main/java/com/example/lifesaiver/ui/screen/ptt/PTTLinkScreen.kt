package com.example.lifesaiver.ui.screen.ptt

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.ui.components.BatteryIndicator
import com.example.lifesaiver.ui.components.Chip
import com.example.lifesaiver.ui.components.ChipVariant
import com.example.lifesaiver.ui.components.MicButton
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import com.example.lifesaiver.R

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    onToggleMic: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
) {
    val scale = LocalAppScale.current
    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale), vertical = scaledDp(24, scale)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isConnected) "Connected" else "Offline",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale)
            )
            Text(text = "12:00", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AppColors.Gray800,
                shape = androidx.compose.foundation.shape.CircleShape,
                border = BorderStroke(scaledDp(1, scale), AppColors.Gray500),
                modifier = Modifier.size(scaledDp(32, scale))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_power_off),
                        contentDescription = "절전 모드",
                        modifier = Modifier.size(scaledDp(16, scale)),
                        colorFilter = ColorFilter.tint(AppColors.White)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Chip(label = "연결된 조난자 수: $connectedCount", variant = ChipVariant.Green)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = scaledDp(32, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            BatteryIndicator(level = batteryLevel)
            Spacer(modifier = Modifier.height(scaledDp(18, scale)))
            Text(
                text = "$batteryLevel%",
                color = AppColors.White,
                fontSize = scaledSp(60, scale),
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "약 36시간 대기 가능",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale)
            )
            Spacer(modifier = Modifier.height(scaledDp(36, scale)))
            MicButton(isActive = isMicOn, onToggle = onToggleMic)
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))
            Text(
                text = if (isConnected) "구조자와 연결 확인" else "연결 대기 중",
                color = if (isConnected) AppColors.Green else AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = scaledDp(18, scale)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalBars(strength = if (isConnected) 4 else 1, variant = SignalVariant.Green)
                Chip(label = "센서 상태 정상 작동", variant = ChipVariant.Green)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = scaledDp(24, scale)),
                horizontalArrangement = Arrangement.spacedBy(scaledDp(12, scale))
            ) {
                PrimaryButton(
                    label = "연결 끊기",
                    variant = PrimaryButtonVariant.Red,
                    modifier = Modifier.weight(1f),
                    onClick = onDisconnect
                )
                PrimaryButton(
                    label = "채팅방",
                    variant = PrimaryButtonVariant.Green,
                    modifier = Modifier.weight(1f),
                    onClick = onChat
                )
            }
        }
    }
}
