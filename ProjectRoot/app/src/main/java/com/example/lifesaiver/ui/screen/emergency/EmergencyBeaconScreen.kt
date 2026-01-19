package com.example.lifesaiver.ui.screen.emergency

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
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun EmergencyBeaconScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    ScreenScaffold(
        gradient = listOf(AppColors.RedSoft, AppColors.Black),
        vignetteColor = AppColors.Red.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "긴급 상황", color = AppColors.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = "$batteryLevel%", color = AppColors.Red.copy(alpha = 0.8f), fontSize = 12.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            Surface(
                shape = CircleShape,
                color = AppColors.RedSoft,
                border = androidx.compose.foundation.BorderStroke(3.dp, AppColors.Red),
                modifier = Modifier.size(120.dp)
            ) {}
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "구조 신호 송출",
                color = AppColors.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "초절전 모드로 신호 전송 중",
                color = AppColors.Gray500,
                fontSize = 12.sp
            )
            Text(
                text = "화면 밝기가 최소화됩니다",
                color = AppColors.Gray500,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(label = "이전", variant = SecondaryButtonVariant.Gray, onClick = onPrev)
                SecondaryButton(label = "다음", variant = SecondaryButtonVariant.Red, onClick = onNext)
            }
        }
    }
}
