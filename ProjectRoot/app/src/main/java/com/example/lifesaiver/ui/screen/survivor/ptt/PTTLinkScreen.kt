package com.example.lifesaiver.ui.screen.survivor.ptt

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.components.BatteryIndicator
import com.example.lifesaiver.ui.components.MeshMap
import com.example.lifesaiver.ui.components.MeshNode
import com.example.lifesaiver.ui.components.MicButton
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.components.PowerSavingLayer
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit
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
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    val (expandedAction, setExpandedAction) = remember { mutableStateOf<ActionType?>(null) }
    val (showDoubleTapHint, setShowDoubleTapHint) = remember { mutableStateOf(false) }
    var showMeshMap by remember { mutableStateOf(false) }
    val showActionLabelsAlways = true
    val displayConnectedCount = (connectedCount - 1).coerceAtLeast(0)
    val meshNodes = remember(connectedCount) { buildMeshNodes(connectedCount) }

    LaunchedEffect(expandedAction) {
        if (
            expandedAction == ActionType.Chat ||
            expandedAction == ActionType.Disconnect ||
            expandedAction == ActionType.Power ||
            expandedAction == ActionType.Count
        ) {
            setShowDoubleTapHint(true)
            delay(3500)
            setShowDoubleTapHint(false)
        } else {
            setShowDoubleTapHint(false)
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
        Box(modifier = Modifier.fillMaxSize()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                // 완전 해제 개념이 없으면 아래처럼 단순 처리해도 됨
                isForceExit = !isPowerSaving,
                onRequestExitPowerSaving = { setPowerSaving(false) }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(32, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                BatteryIndicator(level = batteryLevel)
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                Text(
                    text = "$batteryLevel%",
                    color = AppColors.White,
                    fontSize = scaledSp(54, scale),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "약 36시간 대기 가능",
                    color = AppColors.Gray500,
                    fontSize = scaledSp(12, scale)
                )
                Spacer(modifier = Modifier.height(scaledDp(80, scale)))
                MicButton(
                    isActive = isMicOn,
                    size = scaledDp(80, scale),
                    onPress = onMicPress,
                    onRelease = onMicRelease
                )
                Spacer(modifier = Modifier.height(scaledDp(20, scale)))
                Text(
                    text = if (isConnected) "구조자 연결됨" else "구조자 연결 대기 중",
                    color = if (isConnected) AppColors.Green else AppColors.Gray500,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(scaledDp(18, scale)))
                Spacer(modifier = Modifier.weight(0.6f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(8, scale)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpandableAction(
                            iconRes = R.drawable.ic_power_off,
                            label = "절전 모드",
                            isExpanded = expandedAction == ActionType.Power,
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                setPowerSaving(!isPowerSaving)
                                setExpandedAction(ActionType.Power)
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.connection_lost,
                            label = "연결 끊기",
                            isExpanded = expandedAction == ActionType.Disconnect,
                            iconSizeOverride = scaledDp(44, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (expandedAction == ActionType.Disconnect) {
                                    setExpandedAction(null)
                                    onDisconnect()
                                } else {
                                    setExpandedAction(ActionType.Disconnect)
                                }
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.ic_chat,
                            label = "채팅",
                            isExpanded = expandedAction == ActionType.Chat,
                            iconSizeOverride = scaledDp(38, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (expandedAction == ActionType.Chat) {
                                    setExpandedAction(null)
                                    onChat()
                                } else {
                                    setExpandedAction(ActionType.Chat)
                                }
                            }
                        )
                        ExpandableAction(
                            iconRes = R.drawable.connection_filled,
                            label = "사용자 $displayConnectedCount",
                            isExpanded = expandedAction == ActionType.Count,
                            iconSizeOverride = scaledDp(32, scale),
                            showLabelAlways = showActionLabelsAlways,
                            onClick = {
                                if (expandedAction == ActionType.Count) {
                                    setExpandedAction(null)
                                    showMeshMap = true
                                } else {
                                    setExpandedAction(ActionType.Count)
                                }
                            }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .height(scaledDp(32, scale))
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        AnimatedVisibility(
                            visible = showDoubleTapHint,
                            enter = fadeIn() + slideInVertically { it / 3 },
                            exit = fadeOut() + slideOutVertically { it / 3 }
                        ) {
                            Text(
                                text = "한번 더 눌러주세요",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(12, scale),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.offset(y = scaledDp(6, scale))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = scaledDp(24, scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(scaledDp(36, scale))
                            .padding(start = scaledDp(14, scale)),
                        contentAlignment = Alignment.Center
                    ) {
                        SignalBars(
                            strength = if (isConnected) 4 else 1,
                            variant = SignalVariant.Green,
                            modifier = Modifier.graphicsLayer(rotationX = 180f)
                        )
                    }
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

            if (showMeshMap) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black.copy(alpha = 0.9f))
                ) {
                    MeshMap(nodes = meshNodes, modifier = Modifier.fillMaxSize())
                    TopIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = "닫기",
                        onClick = { showMeshMap = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = scaledDp(16, scale), top = scaledDp(16, scale))
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = scaledDp(18, scale)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "메쉬 현황",
                            color = AppColors.White,
                            fontSize = scaledSp(16, scale),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "직접 연결 ${displayConnectedCount}명",
                            color = AppColors.Gray400,
                            fontSize = scaledSp(12, scale)
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun TopIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .size(scaledDp(36, scale))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(scaledDp(18, scale)),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AppColors.White)
            )
        }
    }
}

@Composable
private fun ExpandableAction(
    iconRes: Int,
    label: String,
    isExpanded: Boolean,
    iconSizeOverride: Dp? = null,
    showLabelAlways: Boolean = false,
    onClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val baseSize = scaledDp(48, scale)
    val labelHeight = scaledDp(16, scale)
    val totalHeight = baseSize + labelHeight
    val iconSize = iconSizeOverride ?: scaledDp(36, scale)
    Column(
        modifier = Modifier
            .width(baseSize)
            .height(totalHeight)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(baseSize)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AppColors.White)
            )
        }
        Box(
            modifier = Modifier
                .height(labelHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (showLabelAlways || isExpanded) {
                Text(
                    text = label,
                    color = AppColors.White,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class ActionType {
    Power,
    Disconnect,
    Chat,
    Count
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
            colorFilter = ColorFilter.tint(AppColors.Gray400),
            contentScale = ContentScale.Fit
        )
    }
}

private fun sensorStatusLabel(status: SensorStatus): String {
    return when (status) {
        SensorStatus.Unsupported -> "없음"
        SensorStatus.Checking -> "확인 중"
        SensorStatus.Active -> "정상"
        SensorStatus.NoData -> "미탐지"
    }
}

private fun sensorStatusColor(status: SensorStatus): Color {
    return when (status) {
        SensorStatus.Unsupported -> AppColors.Gray500
        SensorStatus.Checking -> AppColors.Yellow
        SensorStatus.Active -> AppColors.Green
        SensorStatus.NoData -> AppColors.Red
    }
}

private fun buildMeshNodes(connectedCount: Int): List<MeshNode> {
    val directCount = (connectedCount - 1).coerceAtLeast(0)
    val nodes = mutableListOf<MeshNode>()
    nodes.add(MeshNode(id = "self", hop = 0, signal = 1f, isSelf = true))
    repeat(directCount) { index ->
        val signal = 0.55f + (index % 5) * 0.1f
        nodes.add(
            MeshNode(
                id = "peer-$index",
                hop = 1,
                signal = signal
            )
        )
    }
    return nodes
}
