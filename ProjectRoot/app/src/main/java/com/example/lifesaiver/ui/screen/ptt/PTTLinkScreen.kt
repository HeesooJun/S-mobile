package com.example.lifesaiver.ui.screen.ptt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = if (isConnected) "Connected" else "Offline", color = AppColors.Gray500, fontSize = 12.sp)
            Text(text = "12:00", color = AppColors.Gray500, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(label = "절전 모드", variant = ChipVariant.Green)
            Chip(label = "연결된 조난자 수: $connectedCount", variant = ChipVariant.Green)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            BatteryIndicator(level = batteryLevel)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "$batteryLevel%",
                color = AppColors.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "약 36시간 대기 가능",
                color = AppColors.Gray500,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            MicButton(isActive = isMicOn, onToggle = onToggleMic)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isConnected) "구조자와 연결 확인" else "연결 대기 중",
                color = if (isConnected) AppColors.Green else AppColors.Gray500,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalBars(strength = if (isConnected) 4 else 1, variant = SignalVariant.Green)
                Chip(label = "센서 상태 정상 작동", variant = ChipVariant.Green)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
