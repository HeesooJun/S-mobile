package com.example.lifesaiver.ui.screen.mode

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
import com.example.lifesaiver.ui.components.PrimaryButton
import com.example.lifesaiver.ui.components.PrimaryButtonVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun ModeGateScreen(
    batteryLevel: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onRescuerMode: () -> Unit
) {
    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saivior",
                color = AppColors.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$batteryLevel%",
                color = AppColors.Gray400,
                fontSize = 12.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(70.dp))
            Surface(
                shape = CircleShape,
                color = AppColors.White,
                modifier = Modifier.size(130.dp)
            ) {}
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Saivior",
                color = AppColors.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "오프라인 구조 시스템",
                color = AppColors.Gray500,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                label = "구조자 모드",
                variant = PrimaryButtonVariant.Gray,
                modifier = Modifier.fillMaxWidth(0.7f),
                onClick = onRescuerMode
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "위급상황이신가요?",
                color = AppColors.Red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PrimaryButton(
                    label = "YES",
                    variant = PrimaryButtonVariant.Red,
                    modifier = Modifier.weight(1f),
                    onClick = onYes
                )
                PrimaryButton(
                    label = "NO",
                    variant = PrimaryButtonVariant.Gray,
                    modifier = Modifier.weight(1f),
                    onClick = onNo
                )
            }
        }
    }
}
