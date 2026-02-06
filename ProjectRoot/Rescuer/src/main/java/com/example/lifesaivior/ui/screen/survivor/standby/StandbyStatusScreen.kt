package com.example.lifesaivior.ui.screen.survivor.standby

import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import com.example.lifesaivior.R
import com.example.lifesaivior.ai.stt.EmergencyIntentClassifierKorean
import com.example.lifesaivior.ai.stt.VoiceTriggerDetector
import com.example.lifesaivior.core.log.ConnectionLog
import com.example.lifesaivior.presentation.BleDebugStats
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.components.SecondaryButton
import com.example.lifesaivior.ui.components.SecondaryButtonVariant
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    sttResetToken: Long,
    sttEnabled: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    bleDebugStats: BleDebugStats,
    onPrev: () -> Unit,
    onProfile: () -> Unit,
    onSos: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val classifier = remember(context) { EmergencyIntentClassifierKorean(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var hasTriggered by remember { mutableStateOf(false) }
    var sttStatus by remember { mutableStateOf("🎙️ 대기 중") }
    var lastHeardText by remember { mutableStateOf("") }
    var showDebugModal by remember { mutableStateOf(false) }
    val connectionLogs by ConnectionLog.logs.collectAsState()

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        LaunchedEffect(sttResetToken, sttEnabled) {
            hasTriggered = false
            sttStatus = "🎙️ 대기 중"
            lastHeardText = ""
            if (sttEnabled) {
                delay(30_000)
                if (!hasTriggered) {
                    hasTriggered = true
                    sttStatus = "⏱️ 시간 초과로 자동 송출"
                    onSos()
                }
            }
        }
        DisposableEffect(context, sttResetToken, sttEnabled) {
            if (!sttEnabled) {
                onDispose {}
                return@DisposableEffect onDispose {}
            }
            var detector: VoiceTriggerDetector? = null
            detector = VoiceTriggerDetector(
                context = context,
                onStateChange = { state ->
                    sttStatus = state
                },
                onDetected = { text ->
                    if (hasTriggered) return@VoiceTriggerDetector
                    lastHeardText = text
                    classifier.checkIntent(text) { isEmergency, _, _ ->
                        if (!isEmergency) {
                            detector?.startListening()
                            return@checkIntent
                        }
                        if (!hasTriggered) {
                            mainHandler.post {
                                if (!hasTriggered) {
                                    hasTriggered = true
                                    detector?.stopListening()
                                    onSos()
                                }
                            }
                        }
                    }
                },
                onErrorOccurred = {
                    if (!hasTriggered) {
                        detector?.startListening()
                    }
                }
            )
            detector.startListening()
            onDispose {
                detector.stopListening()
            }
        }
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
            Column(horizontalAlignment = Alignment.End) {
                if (sttEnabled) {
                    Text(
                        text = sttStatus,
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "디버그",
                    color = AppColors.Gray400,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showDebugModal = true }
                )
            }
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
            if (sttEnabled) {
                Spacer(modifier = Modifier.height(scaledDp(16, scale)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = AppColors.White, shape = RoundedCornerShape(scaledDp(10, scale)))
                        .padding(
                            horizontal = scaledDp(14, scale),
                            vertical = scaledDp(10, scale)
                        )
                ) {
                    Text(
                        text = if (lastHeardText.isBlank()) "최근 인식된 문장이 여기 표시됩니다." else lastHeardText,
                        color = AppColors.Gray900,
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
        if (showDebugModal) {
            Dialog(onDismissRequest = { showDebugModal = false }) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Surface(
                        color = AppColors.Gray900,
                        shape = RoundedCornerShape(scaledDp(18, scale)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(24, scale))
                    ) {
                        Column(
                            modifier = Modifier.padding(scaledDp(18, scale)),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "디버그",
                                color = AppColors.White,
                                fontSize = scaledSp(16, scale),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(10, scale)))
                            Text(
                                text = "직접 ${connectedCount}명 · 메쉬 ${meshPeerCount}명",
                                color = AppColors.Gray400,
                                fontSize = scaledSp(12, scale)
                            )
                            val scanAvg = bleDebugStats.scanRssiAvg?.let { "$it dBm" } ?: "-"
                            val connAvg = bleDebugStats.connectionRssiAvg?.let { "$it dBm" } ?: "-"
                            Text(
                                text = "RSSI scan $scanAvg (${bleDebugStats.scanRssiCount}) · conn $connAvg (${bleDebugStats.connectionRssiCount})",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(11, scale),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "pending ${bleDebugStats.pendingCount} · attempts ${bleDebugStats.attemptTracked}/${bleDebugStats.maxAttempts}",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, scale)
                            )
                            Spacer(modifier = Modifier.height(scaledDp(12, scale)))
                            Text(
                                text = "통신 로그",
                                color = AppColors.White,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Bold
                            )
                            val displayedLogs = connectionLogs.takeLast(12)
                            if (displayedLogs.isEmpty()) {
                                Text(
                                    text = "로그 없음",
                                    color = AppColors.Gray500,
                                    fontSize = scaledSp(10, scale)
                                )
                            } else {
                                displayedLogs.forEach { line ->
                                    Text(
                                        text = line,
                                        color = AppColors.Gray400,
                                        fontSize = scaledSp(10, scale)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(scaledDp(16, scale)))
                            Text(
                                text = "닫기",
                                color = AppColors.Green,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { showDebugModal = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
