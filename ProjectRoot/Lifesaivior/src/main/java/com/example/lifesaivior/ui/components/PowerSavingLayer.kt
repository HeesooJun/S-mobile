package com.example.lifesaivior.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp

@Composable
fun PowerSavingLayer(
    isPowerSaving: Boolean,
    isForceExit: Boolean = false,
    resetToken: Long = 0L,
    onRequestExitPowerSaving: () -> Unit
) {
    if (!isPowerSaving) return

    val scale = LocalAppScale.current
    var revealed by remember { mutableStateOf(false) }

    // ✅ 이게 없어서 에러났던 부분
    val interactionSource = remember { MutableInteractionSource() }

    // 절전모드 들어가면 무조건 블랙부터 시작
    LaunchedEffect(isPowerSaving) { revealed = false }

    // 외부에서 절전 모드 재요청 시(예: 원격 패킷), 즉시 블랙으로 복귀
    LaunchedEffect(resetToken) {
        if (resetToken > 0L) {
            revealed = false
        }
    }

    // (옵션) 강제 트리거가 있으면 화면 잠깐 보여주기
    LaunchedEffect(isForceExit) {
        if (isForceExit) revealed = true
    }

    // 잠깐 보여주는 상태면 10초 후 다시 블랙
    LaunchedEffect(revealed, isPowerSaving) {
        if (isPowerSaving && revealed) {
            delay(10_000)
            revealed = false
        }
    }

    val wakeModifier = if (!revealed) {
        Modifier.clickable(
            indication = null,
            interactionSource = interactionSource
        ) { revealed = true }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .background(
                if (!revealed) AppColors.Black.copy(alpha = 0.96f)
                else AppColors.Black.copy(alpha = 0.0f) // 잠깐 보여줄 때는 투명
            )
            // revealed 상태에서는 포인터를 가로채지 않아 하단 버튼(절전 모드 토글)이 동작하도록 함.
            .then(wakeModifier)
    ) {
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = scaledDp(28, scale))
                    .background(
                        color = AppColors.Gray800.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(scaledDp(16, scale))
                    )
                    .padding(
                        horizontal = scaledDp(16, scale),
                        vertical = scaledDp(12, scale)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "절전 모드",
                    color = AppColors.White,
                    fontSize = scaledSp(13, scale),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                Text(
                    text = "10초 후 다시 꺼집니다.\n유지하려면 절전모드를 해제하세요.",
                    color = AppColors.Gray400,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
