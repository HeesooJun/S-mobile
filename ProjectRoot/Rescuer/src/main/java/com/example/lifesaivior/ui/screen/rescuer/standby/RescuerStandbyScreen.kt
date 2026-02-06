package com.example.lifesaivior.ui.screen.rescuer.standby

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.components.SecondaryButton
import com.example.lifesaivior.ui.components.SecondaryButtonVariant
import com.example.lifesaivior.R
import com.example.lifesaivior.core.log.ConnectionLog
import com.example.lifesaivior.presentation.BleDebugStats
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun RescuerStandbyScreen(
    batteryLevel: Int,
    isConnected: Boolean,
    connectedCount: Int,
    meshPeerCount: Int,
    bleDebugStats: BleDebugStats,
    onPrev: () -> Unit,
    onGoPTT: () -> Unit,
    onSos: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    var showDebugModal by remember { mutableStateOf(false) }
    val connectionLogs by ConnectionLog.logs.collectAsState()

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
                text = "탐색 안내",
                color = AppColors.Gray500,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "디버그",
                color = AppColors.Gray400,
                fontSize = scaledSp(11, scale),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showDebugModal = true }
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
                        .widthIn(max = scaledDp(260, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "구조 신호를 찾을까요?",
                        color = AppColors.White,
                        fontSize = scaledSp(20, scale),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(scaledDp(20, scale)))
                    Text(
                        text = "주변 기기의 SOS 신호를 탐색합니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "신호를 포착하면 거리 정보가 표시됩니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "연결되면 음성·채팅으로 즉시 대응할 수 있습니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "누군가 신호를 받으면 음성·채팅으로 바로 연결됩니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        fontWeight = FontWeight.SemiBold,
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
                SecondaryButton(label = "이전", variant = SecondaryButtonVariant.Gray, onClick = onPrev)
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
                        fontWeight = FontWeight.SemiBold
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
