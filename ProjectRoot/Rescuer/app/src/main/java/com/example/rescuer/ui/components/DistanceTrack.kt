package com.example.rescuer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.rescuer.ui.theme.AppColors
import com.example.rescuer.ui.theme.LocalAppScale
import com.example.rescuer.ui.theme.scaledDp
import com.example.rescuer.ui.theme.scaledSp
import java.util.Locale

enum class DistanceTrend { Approaching, Receding, Unknown }

@Composable
fun DistanceTrack(
    distanceMeters: Float?,                 // null 이면 탐색 모드
    modifier: Modifier = Modifier,          // ✅ modifier를 첫 optional로
    trend: DistanceTrend = DistanceTrend.Unknown,
    maxMeters: Float = 30f,
) {
    val scale = LocalAppScale.current
    val density = LocalDensity.current

    val isSearching = distanceMeters == null || distanceMeters > maxMeters
    val clamped = remember(distanceMeters, maxMeters) { distanceMeters?.coerceIn(0f, maxMeters) }
    val progress = if (!isSearching && clamped != null) 1f - (clamped / maxMeters) else 0f
    val fillFraction = progress.coerceIn(0f, 1f)

    // ✅ 바 실제 폭 측정(px)
    var barWidthPx by remember { mutableStateOf(0) }
    val barWidthDp = with(density) { barWidthPx.toDp() }

    // ✅ 탐색 하이라이트 애니메이션(0~1)
    val inf = rememberInfiniteTransition(label = "scan")
    val scanT by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanT"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = scaledDp(24, scale))
            .padding(top = scaledDp(10, scale)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(scaledDp(10, scale)))

        Column(modifier = Modifier.widthIn(max = scaledDp(330, scale))) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("30m", color = AppColors.White.copy(alpha = 0.7f), fontSize = scaledSp(12, scale))
                Spacer(modifier = Modifier.weight(1f))
                Text("0m", color = AppColors.White.copy(alpha = 0.7f), fontSize = scaledSp(12, scale))
            }

            Spacer(modifier = Modifier.height(scaledDp(6, scale)))

            val trackShape = RoundedCornerShape(999.dp)
            val trackH = scaledDp(20, scale)
            val knobSize = scaledDp(12, scale)
            val highlightW = scaledDp(64, scale)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .clip(trackShape)
                    .background(AppColors.Red.copy(alpha = 0.10f))
                    .onSizeChanged { barWidthPx = it.width } // ✅ 여기서 폭 측정
            ) {
                if (isSearching) {
                    // 하이라이트 x (dp)
                    val scanX = ((barWidthDp + highlightW * 2) * scanT) - highlightW

                    Box(
                        modifier = Modifier
                            .width(highlightW)
                            .fillMaxHeight()
                            .offset(x = scanX)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        AppColors.Red.copy(alpha = 0.60f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Text(
                        text = "탐색 중",
                        color = AppColors.White.copy(alpha = 0.6f),
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    // 채움(그라데이션)
                    val fillDp = barWidthDp * fillFraction
                    Box(
                        modifier = Modifier
                            .height(trackH)
                            .width(fillDp)
                            .clip(trackShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppColors.Red.copy(alpha = 0.30f), AppColors.Red)
                                )
                            )
                    )

                    // 노브 (끝점)
                    val knobX = ((barWidthDp - knobSize).coerceAtLeast(0.dp)) * fillFraction
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = knobX)
                            .size(knobSize)
                            .clip(CircleShape)
                            .background(AppColors.Red)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(scaledDp(24, scale)))

        if (isSearching) {
            Text(
                text = "30m 이내로 접근하면 거리 표시",
                color = AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Medium
            )
        } else {
            val d = clamped ?: 0f
            val big = String.format(Locale.getDefault(), "%.1f", d) // ✅ Locale 경고 제거

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = AppColors.White,
                            fontSize = scaledSp(52, scale),
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) { append(big) }
                    withStyle(
                        SpanStyle(
                            color = AppColors.White,
                            fontSize = scaledSp(28, scale),
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) { append("m") }
                }
            )

            val sub = when (trend) {
                DistanceTrend.Approaching -> "가까워지는 중"
                DistanceTrend.Receding -> "멀어지는 중"
                DistanceTrend.Unknown -> "거리 측정 중"
            }
            Text(
                text = sub,
                color = AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
