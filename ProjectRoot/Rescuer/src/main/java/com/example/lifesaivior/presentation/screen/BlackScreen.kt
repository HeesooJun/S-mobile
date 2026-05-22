package com.example.lifesaivior.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun BlackSaverScreen(
    onUnlock: () -> Unit // 잠금 해제 시 실행할 함수
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // 완전 검은색 (AMOLED 절전)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onUnlock() } // 더블 탭 하면 원래 화면으로 복귀
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🚨 SOS 신호 송출 중\n\n(화면을 두 번 두드리면 켜집니다)",
            color = Color.DarkGray, // 눈부심 방지용 어두운 글씨
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
