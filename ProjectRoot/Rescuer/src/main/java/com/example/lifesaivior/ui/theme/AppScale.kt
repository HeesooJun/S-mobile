package com.example.lifesaivior.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalAppScale = staticCompositionLocalOf { 1f }

private const val TEXT_SCALE_BOOST = 1.08f

@Composable
@ReadOnlyComposable
fun rememberAppScale(
    baseWidth: Float = 360f,
    maxScale: Float = 1.2f
): Float {
    val width = LocalConfiguration.current.screenWidthDp.toFloat().coerceAtLeast(baseWidth)
    val rawScale = width / baseWidth
    return rawScale.coerceIn(1f, maxScale)
}

fun scaledDp(value: Int, scale: Float): Dp = (value * scale).dp

fun scaledDp(value: Float, scale: Float): Dp = (value * scale).dp

fun scaledSp(value: Int, scale: Float): TextUnit = (value * scale * TEXT_SCALE_BOOST).sp

fun scaledSp(value: Float, scale: Float): TextUnit = (value * scale * TEXT_SCALE_BOOST).sp
