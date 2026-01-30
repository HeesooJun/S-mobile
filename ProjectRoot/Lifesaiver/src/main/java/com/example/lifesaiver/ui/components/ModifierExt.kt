package com.example.lifesaiver.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.tripleClickable(
    thresholdMs: Long = 500L,
    onTripleClick: () -> Unit
): Modifier = composed {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf(0L) }
    pointerInput(onTripleClick) {
        detectTapGestures(onTap = {
            val now = System.currentTimeMillis()
            tapCount = if (now - lastTapMs <= thresholdMs) tapCount + 1 else 1
            lastTapMs = now
            if (tapCount >= 3) {
                tapCount = 0
                lastTapMs = 0L
                onTripleClick()
            }
        })
    }
}
