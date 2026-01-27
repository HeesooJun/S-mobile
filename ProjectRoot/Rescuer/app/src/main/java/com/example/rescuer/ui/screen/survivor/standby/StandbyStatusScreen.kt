package com.example.rescuer.ui.screen.survivor.standby

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
import com.example.rescuer.ui.components.ScreenScaffold
import com.example.rescuer.ui.components.SecondaryButton
import com.example.rescuer.ui.components.SecondaryButtonVariant
import com.example.rescuer.R
import com.example.rescuer.ui.theme.AppColors
import com.example.rescuer.ui.theme.LocalAppScale
import com.example.rescuer.ui.theme.scaledDp
import com.example.rescuer.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun StandbyStatusScreen(
    batteryLevel: Int,
    onPrev: () -> Unit,
    onSos: () -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensorItems = remember {
        listOf(
            SensorProbe("가속도", Sensor.TYPE_ACCELEROMETER),
            SensorProbe("자이로", Sensor.TYPE_GYROSCOPE),
            SensorProbe("지자기", Sensor.TYPE_MAGNETIC_FIELD),
            SensorProbe("조도", Sensor.TYPE_LIGHT),
            SensorProbe("근접", Sensor.TYPE_PROXIMITY)
        )
    }
    val sensorStatus = remember { mutableStateMapOf<Int, SensorStatus>() }
    var isSensorExpanded by remember { mutableStateOf(false) }
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val type = event?.sensor?.type ?: return
                if (sensorStatus[type] != SensorStatus.Active) {
                    sensorStatus[type] = SensorStatus.Active
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }
    }

    DisposableEffect(isSensorExpanded) {
        if (!isSensorExpanded) return@DisposableEffect onDispose { }
        val activeSensors = sensorItems.mapNotNull { item ->
            sensorManager.getDefaultSensor(item.type)
        }
        activeSensors.forEach { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    LaunchedEffect(isSensorExpanded) {
        if (!isSensorExpanded) return@LaunchedEffect
        sensorItems.forEach { item ->
            val sensor = sensorManager.getDefaultSensor(item.type)
            sensorStatus[item.type] = if (sensor == null) {
                SensorStatus.Unsupported
            } else {
                SensorStatus.Checking
            }
        }
        delay(3000)
        if (isSensorExpanded) {
            sensorItems.forEach { item ->
                if (sensorStatus[item.type] == SensorStatus.Checking) {
                    sensorStatus[item.type] = SensorStatus.NoData
                }
            }
        }
    }
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
                text = "작동 안내",
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
                        .widthIn(max = scaledDp(260, scale)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "구조 신호를 보내실 건가요?",
                        color = AppColors.White,
                        fontSize = scaledSp(20, scale),
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(scaledDp(20, scale)))
                    Text(
                        text = "SOS 버튼을 누르면",
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
                        text = "주변 기기끼리 신호를 이어 도움을 기다립니다.",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "누군가 신호를 받으면 음성·채팅으로 바로 연결됩니다.",
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
                SecondaryButton(label = "이전", variant = SecondaryButtonVariant.Gray, onClick = onPrev)

                Box {
                    SensorStatusToggle(
                        label = "센서 상태",
                        isExpanded = isSensorExpanded,
                        onToggle = { isSensorExpanded = !isSensorExpanded }
                    )
                    DropdownMenu(
                        expanded = isSensorExpanded,
                        onDismissRequest = { isSensorExpanded = false },
                        offset = DpOffset(-scaledDp(4, scale), scaledDp(12, scale)),
                        modifier = Modifier
                            .widthIn(min = scaledDp(150, scale), max = scaledDp(190, scale))
                            .background(
                                color = AppColors.Gray800,
                                shape = RoundedCornerShape(scaledDp(14, scale))
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = scaledDp(16, scale),
                                vertical = scaledDp(12, scale)
                            )
                        ) {
                            sensorItems.forEach { item ->
                                val status = sensorStatus[item.type] ?: SensorStatus.Checking
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = scaledDp(4, scale)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.label,
                                        color = AppColors.White,
                                        fontSize = scaledSp(12, scale)
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = sensorStatusLabel(status),
                                        color = sensorStatusColor(status),
                                        fontSize = scaledSp(11, scale),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorStatusToggle(
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val scale = LocalAppScale.current
    val arrowRotation = if (isExpanded) 180f else 0f
    Row(
        modifier = Modifier
            .background(
                color = AppColors.Gray800,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(
                start = scaledDp(12, scale),
                end = scaledDp(10, scale),
                top = scaledDp(8, scale),
                bottom = scaledDp(8, scale)
            )
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
    ) {
        Text(
            text = label,
            color = AppColors.Gray400,
            fontSize = scaledSp(13, scale),
            fontWeight = FontWeight.Medium
        )
        Image(
            painter = painterResource(id = R.drawable.ic_arrow_up),
            contentDescription = "센서 상태 펼침",
            modifier = Modifier
                .size(scaledDp(12, scale))
                .rotate(arrowRotation),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(AppColors.Gray400),
            contentScale = ContentScale.Fit
        )
    }
}

private data class SensorProbe(
    val label: String,
    val type: Int
)

private enum class SensorStatus {
    Unsupported,
    Checking,
    Active,
    NoData
}

private fun sensorStatusLabel(status: SensorStatus): String {
    return when (status) {
        SensorStatus.Unsupported -> "없음"
        SensorStatus.Checking -> "확인 중"
        SensorStatus.Active -> "정상"
        SensorStatus.NoData -> "미탐지"
    }
}

private fun sensorStatusColor(status: SensorStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        SensorStatus.Unsupported -> AppColors.Gray500
        SensorStatus.Checking -> AppColors.Yellow
        SensorStatus.Active -> AppColors.Green
        SensorStatus.NoData -> AppColors.Red
    }
}
