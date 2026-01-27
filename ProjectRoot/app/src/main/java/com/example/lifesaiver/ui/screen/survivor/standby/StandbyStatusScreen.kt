package com.example.lifesaiver.ui.screen.survivor.standby

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onProfile: () -> Unit,
    onSos: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current

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
                text = "연동 안내",
                color = AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Medium
            )
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
                        .widthIn(max = scaledDp(270, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "구조 신호를 보내는 건가요?",
                        color = AppColors.White,
                        fontSize = scaledSp(20, scale),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(scaledDp(30, scale)))
                    Text(
                        text = "SOS 버튼을 누르면,",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "주변 사용자에게 구조 신호를 보냅니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "주변 기기들이 신호를 이어 받아 구조를 돕습니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "구조자가 신호를 받으면 음성/채팅으로 바로 연결됩니다.",
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
                    .padding(
                        start = scaledDp(32, scale),
                        end = scaledDp(32, scale),
                        bottom = scaledDp(24, scale)
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    label = "이전",
                    variant = SecondaryButtonVariant.Gray,
                    onClick = onPrev
                )
                SecondaryButton(
                    label = "내 정보",
                    variant = SecondaryButtonVariant.Gray,
                    onClick = onProfile
                )
            }
        }
    }
}
