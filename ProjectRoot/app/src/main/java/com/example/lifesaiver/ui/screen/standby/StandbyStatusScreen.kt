package com.example.lifesaiver.ui.screen.standby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.ui.components.BatteryIndicator
import com.example.lifesaiver.ui.components.Chip
import com.example.lifesaiver.ui.components.ChipVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onSos: () -> Unit
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
            Text(text = "Offline", color = AppColors.Gray500, fontSize = 12.sp)
            Text(text = "12:00", color = AppColors.Gray500, fontSize = 12.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(50.dp))
            BatteryIndicator(level = batteryLevel)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "$batteryLevel%",
                color = AppColors.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "약 48시간 대기 가능",
                color = AppColors.Gray500,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(50.dp))

            Surface(
                shape = CircleShape,
                color = AppColors.RedSoft,
                border = androidx.compose.foundation.BorderStroke(4.dp, AppColors.Red),
                modifier = Modifier
                    .size(160.dp)
                    .clickable { onSos() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "SOS",
                        color = AppColors.Red,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(label = "이전", variant = SecondaryButtonVariant.Gray, onClick = onPrev)
                Chip(label = "센서 상태 정상 작동", variant = ChipVariant.Green)
                SignalBars(strength = 4, variant = SignalVariant.Green)
            }
        }
    }
}
