package com.example.lifesaivior.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lifesaivior.R
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlin.math.roundToInt

private val ColorBackground = Color(0xFF0D0F11)
private val ColorCard = Color(0xFF1D2124)
private val ColorTextMain = Color(0xFFFFFFFF)
private val ColorTextSub = Color(0xFF8E8E93)
private val ColorDivider = Color(0x80FFFFFF)
private val ColorAccent = Color(0xFFFF4D4D)

@Composable
fun DemoSettingsScreen(
    isDemoOn: Boolean,
    beepLevel: Int,
    highToneLevel: Int,
    vibrateLevel: Int,
    onBeepLevelChange: (Int) -> Unit,
    onHighToneLevelChange: (Int) -> Unit,
    onVibrateLevelChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scale = LocalAppScale.current
    val enabled = isDemoOn
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scaledDp(16, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection(scale = scale, onBack = onBack, title = "시연 모드 상세")
            if (!isDemoOn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorCard, RoundedCornerShape(scaledDp(12, scale)))
                        .padding(scaledDp(12, scale))
                ) {
                    Text(
                        text = "시연 모드를 켜면 강도 조절이 가능합니다.\n시연 모드가 꺼져 있으면 최대 강도로 송출됩니다.",
                        color = ColorTextSub,
                        fontSize = scaledSp(12, scale)
                    )
                }
                Spacer(modifier = Modifier.height(scaledDp(16, scale)))
            }
            DemoLevelCard(
                scale = scale,
                label = "비프음",
                value = beepLevel,
                onValueChange = onBeepLevelChange,
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            DemoLevelCard(
                scale = scale,
                label = "고주파",
                value = highToneLevel,
                onValueChange = onHighToneLevelChange,
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
            DemoLevelCard(
                scale = scale,
                label = "진동",
                value = vibrateLevel,
                onValueChange = onVibrateLevelChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun HeaderSection(scale: Float, onBack: () -> Unit, title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = scaledDp(40, scale), bottom = scaledDp(20, scale))
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_common_back),
            contentDescription = "뒤로가기",
            tint = ColorTextMain,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(scaledDp(24, scale))
                .clickable { onBack() }
        )
        Text(
            text = title,
            color = ColorTextMain,
            fontSize = scaledSp(20, scale),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun DemoLevelCard(
    scale: Float,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean
) {
    val clampedValue = value.coerceIn(0, 100)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorCard, RoundedCornerShape(scaledDp(13, scale)))
            .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(14, scale))
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ColorTextMain,
                fontSize = scaledSp(16, scale),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = clampedValue.toString(),
                color = ColorAccent,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(scaledDp(10, scale)))
        Slider(
            value = clampedValue.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 99,
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(scaledDp(6, scale)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange((clampedValue - 10).coerceIn(0, 100)) },
                enabled = enabled
            ) {
                Text(
                    text = "-10",
                    color = ColorTextMain,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .weight(1f)
                    .background(ColorDivider)
            )
            IconButton(
                onClick = { onValueChange((clampedValue + 10).coerceIn(0, 100)) },
                enabled = enabled
            ) {
                Text(
                    text = "+10",
                    color = ColorTextMain,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
