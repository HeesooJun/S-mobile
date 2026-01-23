package com.example.lifesaiver.ui.screen.ptt

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.lifesaiver.ui.components.MicButton
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SignalBars
import com.example.lifesaiver.ui.components.SignalVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import com.example.lifesaiver.presentation.screen.ActionType
import com.example.lifesaiver.presentation.screen.PTTLinkUiState
import com.example.lifesaiver.presentation.sensor.SensorProbe
import com.example.lifesaiver.presentation.sensor.SensorStatus
import kotlinx.coroutines.delay

@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    uiState: PTTLinkUiState,
    sensorItems: List<SensorProbe>,
    onSensorExpandedChange: (Boolean) -> Unit,
    onSensorStatusChange: (Int, SensorStatus) -> Unit,
    onPowerToggle: () -> Unit,
    onActionSelected: (ActionType?) -> Unit
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensorStatus = uiState.sensorStatus
    val isSensorExpanded = uiState.isSensorExpanded
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val type = event?.sensor?.type ?: return
                if (sensorStatus[type] != SensorStatus.Active) {
                    onSensorStatusChange(type, SensorStatus.Active)
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
    val isPowerSaving = uiState.isPowerSaving
    val expandedAction = uiState.expandedAction
    val showDoubleTapHint = uiState.showDoubleTapHint
    val showActionLabelsAlways = true
    val displayConnectedCount = (connectedCount - 1).coerceAtLeast(0)

    LaunchedEffect(isSensorExpanded) {
        if (!isSensorExpanded) return@LaunchedEffect
        sensorItems.forEach { item ->
            val sensor = sensorManager.getDefaultSensor(item.type)
            val status = if (sensor == null) SensorStatus.Unsupported else SensorStatus.Checking
            onSensorStatusChange(item.type, status)
        }
        delay(3000)
        if (isSensorExpanded) {
            sensorItems.forEach { item ->
                if (sensorStatus[item.type] == SensorStatus.Checking) {
                    onSensorStatusChange(item.type, SensorStatus.NoData)
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
            TopIconButton(
                iconRes = R.drawable.arrow,
                contentDescription = "뒤로",
                onClick = onBack
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
            Spacer(modifier = Modifier.height(scaledDp(28, scale)))
            MicButton(
                isActive = isMicOn,
                size = scaledDp(80, scale),
                onPress = onMicPress,
                onRelease = onMicRelease
            )
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
            ) {
                if (isDisconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(scaledDp(14, scale)),
                        color = AppColors.Red,
                        strokeWidth = scaledDp(2, scale)
                    )
                }
                Text(
                    text = when {
                        isDisconnecting -> "연결 종료 중"
                        isConnected -> "구조자 연결됨"
                        else -> "구조자 연결 대기 중"
                    },
                    color = when {
                        isDisconnecting -> AppColors.Red
                        isConnected -> AppColors.Green
                        else -> AppColors.Gray500
                    },
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
            }
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
                            onPowerToggle()
                        }
                    )
                    ExpandableAction(
                        iconRes = R.drawable.connection_lost,
                        label = "연결 끊기",
                        isExpanded = expandedAction == ActionType.Disconnect,
                        iconSizeOverride = scaledDp(44, scale),
                        showLabelAlways = showActionLabelsAlways,
                        onClick = {
                            if (isDisconnecting) return@ExpandableAction
                            if (expandedAction == ActionType.Disconnect) {
                                onActionSelected(null)
                                onDisconnect()
                            } else {
                                onActionSelected(ActionType.Disconnect)
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
                                onActionSelected(null)
                                onChat()
                            } else {
                                onActionSelected(ActionType.Chat)
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
                            onActionSelected(ActionType.Count)
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
                        onToggle = { onSensorExpandedChange(!isSensorExpanded) }
                    )
                    DropdownMenu(
                        expanded = isSensorExpanded,
                        onDismissRequest = { onSensorExpandedChange(false) },
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
