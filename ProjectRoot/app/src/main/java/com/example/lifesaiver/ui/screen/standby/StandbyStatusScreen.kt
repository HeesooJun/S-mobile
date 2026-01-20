package com.example.lifesaiver.ui.screen.standby

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.example.lifesaiver.ui.components.BatteryIndicator
import com.example.lifesaiver.ui.components.Chip
import com.example.lifesaiver.ui.components.ChipVariant
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun rememberCurrentTimeText(
    pattern: String = "HH:mm",
    updateIntervalMillis: Long = 60_000L // 1분마다 갱신
): State<String> {
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    val timeState = remember { mutableStateOf(LocalTime.now().format(formatter)) }

    LaunchedEffect(pattern, updateIntervalMillis) {
        while (true) {
            timeState.value = LocalTime.now().format(formatter)
            delay(updateIntervalMillis)
        }
    }
    return timeState
}
@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onSos: () -> Unit
) {
    val scale = LocalAppScale.current

    val currentTime by rememberCurrentTimeText(pattern = "HH:mm", updateIntervalMillis = 60_000L)


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
            Text(text = "Offline", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
            Text(text = currentTime, color = AppColors.Gray500, fontSize = scaledSp(12, scale)) // ✅ 여기
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
            Spacer(modifier = Modifier.height(scaledDp(24, scale)))
            Text(
                text = "$batteryLevel%",
                color = AppColors.White,
                fontSize = scaledSp(60, scale),
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "약 48시간 대기 가능",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale)
            )
            Spacer(modifier = Modifier.height(scaledDp(48, scale)))

            Image(
                painter = painterResource(id = R.drawable.ic_siren),
                contentDescription = "SOS",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(scaledDp(64, scale))
                    .clickable { onSos() }
            )
            Spacer(modifier = Modifier.height(scaledDp(10, scale)))
            Text(
                text = "SOS",
                color = AppColors.Red,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = scaledDp(24, scale)),
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
