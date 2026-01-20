package com.example.lifesaiver.ui.screen.standby

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
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

@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onSos: () -> Unit
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
            Text(text = "Offline", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
            Text(text = "12:00", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .widthIn(max = scaledDp(260, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "구조 신호를 보내실 건가요?",
                        color = AppColors.White,
                        fontSize = scaledSp(20, scale),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(scaledDp(20, scale)))
            Text(
                text = "SOS 버튼을 누르면 주변 사용자에게 구조 신호를 보냅니다.",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                textAlign = TextAlign.Center
            )
            Text(
                text = "이 신호가 이어져 구조자가 더 빨리 찾을 수 있어요.",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                textAlign = TextAlign.Center
            )
            Text(
                text = "주변 기기끼리 신호를 이어 도움을 기다립니다.",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                textAlign = TextAlign.Center
            )
            Text(
                text = "누군가 신호를 받으면 음성·채팅으로 바로 연결됩니다.",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                textAlign = TextAlign.Center
            )
                }
            }
            Spacer(modifier = Modifier.height(scaledDp(48, scale)))
            Spacer(modifier = Modifier.height(scaledDp(24, scale)))

            Image(
                painter = painterResource(id = R.drawable.ic_siren),
                contentDescription = "SOS",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(scaledDp(52, scale))
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
