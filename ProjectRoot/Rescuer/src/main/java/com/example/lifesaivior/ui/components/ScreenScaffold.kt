package com.example.lifesaivior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScreenScaffold(
    gradient: List<Color>,
    vignetteColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseColor = gradient.lastOrNull() ?: Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        Column(modifier = Modifier.fillMaxSize(), content = content)
    }
}
