package com.example.lifesaivior.ui.screen.rescuer.ptt

import android.media.MediaPlayer
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lifesaivior.R
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.presentation.MeshVisualEvent
import com.example.lifesaivior.core.location.DistanceMeasurementSource
import com.example.lifesaivior.core.location.DistanceTrend
import com.example.lifesaivior.ui.components.MeshEdge
import com.example.lifesaivior.ui.components.MeshNode
import com.example.lifesaivior.ui.components.PowerSavingLayer
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.components.chat.AutoScrollChatList
import com.example.lifesaivior.ui.components.quintupleClickable
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import com.example.lifesaivior.ui.theme.scaledSp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

enum class RssiFeedbackMode(val label: String) {
    OFF("알림 끔"),
    VIBRATION("진동"),
    SOUND("소리"),
    BOTH("진동+소리")
}

enum class RssiFeedbackLevel(val label: String) {
    LOW("약"),
    MEDIUM("중"),
    HIGH("강")
}

@Composable
fun RescuerPTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    isInCall: Boolean,
    isCalling: Boolean = false,
    isMicOn: Boolean = false,
    isConnected: Boolean,
    isSpeakerphoneOn: Boolean = true,
    distanceMeters: Float?,
    distanceTrend: DistanceTrend,
    distanceSource: DistanceMeasurementSource,
    targetDisplayName: String? = null,
    chatRoomTitle: String = "전체 채팅",
    chatMessages: List<ChatMessage> = emptyList(),
    onRequestCall: () -> Unit,
    onEndCall: () -> Unit,
    onBack: () -> Unit,
    onPanicClear: () -> Unit,
    onSendChat: (String) -> Unit = {},
    onMicPress: () -> Unit = {},
    onMicRelease: () -> Unit = {},
    onToggleSpeakerphone: () -> Unit = {},
    rssiFeedbackMode: RssiFeedbackMode = RssiFeedbackMode.BOTH,
    rssiFeedbackLevel: RssiFeedbackLevel = RssiFeedbackLevel.MEDIUM,
    onCycleRssiFeedbackMode: () -> Unit = {},
    onCycleRssiFeedbackLevel: () -> Unit = {},
    remoteControlEnabled: Boolean = false,
    remotePowerSavingState: Boolean? = null,
    onSendRemoteWake: () -> Unit = {},
    onSendRemoteBeep: () -> Unit = {},
    onSendRemoteVibrate: () -> Unit = {},
    onSendRemoteHighTone: () -> Unit = {},
    onSetRemotePowerSaving: (Boolean) -> Unit = {},
    onSendRemoteStop: () -> Unit = {},
    remoteRepeatIntervalMs: Long = 2_500L
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    var showControlModal by remember { mutableStateOf(false) }
    var stickyBeep by remember { mutableStateOf(false) }
    var stickyVibrate by remember { mutableStateOf(false) }
    var stickyHighTone by remember { mutableStateOf(false) }
    var isBeepRepeating by remember { mutableStateOf(false) }
    var isVibrateRepeating by remember { mutableStateOf(false) }
    var isHighToneRepeating by remember { mutableStateOf(false) }
    val meshDisplayCount = (meshPeerCount - 1).coerceAtLeast(0)
    val hasMeshPeers = meshDisplayCount > 0
    val isLinkActive = isConnected || hasMeshPeers
    val targetStatusText = when {
        isInCall && targetDisplayName.isNullOrBlank() -> "통화 연결됨"
        targetDisplayName.isNullOrBlank() -> "대상 미선택"
        isInCall -> "${targetDisplayName}님과 통화 중입니다."
        isCalling -> "${targetDisplayName}님과 연결 시도 중입니다."
        else -> "${targetDisplayName}님이 선택되었습니다."
    }
    val hasActiveRepeating = isBeepRepeating || isVibrateRepeating || isHighToneRepeating

    LaunchedEffect(remoteControlEnabled) {
        if (!remoteControlEnabled) {
            stickyBeep = false
            stickyVibrate = false
            stickyHighTone = false
            isBeepRepeating = false
            isVibrateRepeating = false
            isHighToneRepeating = false
        }
    }
    LaunchedEffect(stickyBeep, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyBeep) return@LaunchedEffect
        while (remoteControlEnabled && stickyBeep) {
            onSendRemoteBeep()
            delay(remoteRepeatIntervalMs)
        }
    }
    LaunchedEffect(stickyVibrate, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyVibrate) return@LaunchedEffect
        while (remoteControlEnabled && stickyVibrate) {
            onSendRemoteVibrate()
            delay(remoteRepeatIntervalMs)
        }
    }
    LaunchedEffect(stickyHighTone, remoteControlEnabled) {
        if (!remoteControlEnabled || !stickyHighTone) return@LaunchedEffect
        while (remoteControlEnabled && stickyHighTone) {
            onSendRemoteHighTone()
            delay(remoteRepeatIntervalMs)
        }
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Shadow.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                // 절전 해제 컨텐츠가 없으면 원래처럼 단순 처리라도 유지
                isForceExit = !isPowerSaving,
                onRequestExitPowerSaving = { setPowerSaving(false) }
            )
            TopIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "이전",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(12, scale), top = scaledDp(12, scale))
            )
            Text(
                text = "LIFESAIVIOR",
                color = AppColors.Gray500,
                fontSize = scaledSp(12, scale),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(60, scale), top = scaledDp(18, scale))
                    .quintupleClickable(onQuintupleClick = onPanicClear)
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = scaledDp(32, scale))
            ) {
                val chatPanelHeight = maxHeight * 0.44f
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Spacer(modifier = Modifier.height(scaledDp(52, scale)))
                Text(
                    text = when {
                        hasMeshPeers -> "메쉬 연결됨"
                        isConnected -> "직접 연결됨"
                        else -> "생존자 연결 대기 중"
                    },
                    color = if (isLinkActive) AppColors.Green else AppColors.Gray500,
                    fontSize = scaledSp(14, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(scaledDp(4, scale)))
                Text(
                    text = targetStatusText,
                    color = if (targetDisplayName.isNullOrBlank()) AppColors.Gray500 else AppColors.White,
                    fontSize = scaledSp(12, scale),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                DistanceStatusCard(
                    distanceMeters = distanceMeters,
                    distanceSource = distanceSource,
                    distanceTrend = distanceTrend,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(scaledDp(26, scale)))
                Box(contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = {
                            if (isInCall) onEndCall() else onRequestCall()
                        },
                        enabled = isInCall || !isCalling,
                        modifier = Modifier.size(scaledDp(86, scale)),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isInCall) AppColors.Red else AppColors.Green,
                            disabledContainerColor = AppColors.Green.copy(alpha = 0.4f),
                            contentColor = AppColors.PureWhite,
                            disabledContentColor = AppColors.PureWhite.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = if (isInCall) "통화 종료" else "통화 요청",
                            modifier = Modifier.size(scaledDp(40, scale))
                        )
                    }
                    if (isCalling && !isInCall) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(scaledDp(104, scale)),
                            color = AppColors.Green,
                            strokeWidth = scaledDp(2, scale)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                Text(
                    text = when {
                        isCalling && !isInCall -> "통화 연결 대기 중... (최대 15초)"
                        isInCall -> "통화 중 · 버튼을 눌러 종료"
                        else -> "통화 요청"
                    },
                    color = when {
                        isCalling && !isInCall -> AppColors.Green
                        isInCall -> AppColors.Red
                        else -> AppColors.Green
                    },
                    fontSize = scaledSp(13, scale),
                    fontWeight = FontWeight.Bold
                )
                if (isInCall) {
                    Spacer(modifier = Modifier.height(scaledDp(10, scale)))
                    OutlinedButton(onClick = onToggleSpeakerphone) {
                        Text(
                            text = if (isSpeakerphoneOn) "스피커 끄기" else "스피커 켜기",
                            fontSize = scaledSp(12, scale),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                PttChatPanel(
                    roomTitle = chatRoomTitle,
                    messages = chatMessages,
                    onSend = onSendChat,
                    isMicOn = isMicOn,
                    onMicPress = onMicPress,
                    onMicRelease = onMicRelease,
                    showControlBadge = hasActiveRepeating,
                    onOpenControls = { showControlModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chatPanelHeight)
                )
                Spacer(modifier = Modifier.height(scaledDp(8, scale)))
            }
            }
            if (showControlModal) {
                Dialog(onDismissRequest = { showControlModal = false }) {
                    val modalScale = scale / 1.08f
                    Surface(
                        color = AppColors.Gray900,
                        shape = RoundedCornerShape(scaledDp(18, scale)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = scaledDp(6, scale))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(scaledDp(18, scale))
                        ) {
                            Text(
                                text = "원격 제어",
                                color = AppColors.White,
                                fontSize = scaledSp(16, modalScale),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Text(
                                text = "RSSI 탐지음 ${rssiFeedbackMode.label} · 강도 ${rssiFeedbackLevel.label}",
                                color = AppColors.Gray400,
                                fontSize = scaledSp(11, modalScale),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
                            ) {
                                Button(
                                    onClick = onCycleRssiFeedbackMode,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Gray800.copy(alpha = 0.7f),
                                        contentColor = AppColors.White,
                                        disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
                                        disabledContentColor = AppColors.Gray500
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "알림 모드 변경",
                                        fontSize = scaledSp(11, modalScale)
                                    )
                                }
                                Button(
                                    onClick = onCycleRssiFeedbackLevel,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Gray800.copy(alpha = 0.7f),
                                        contentColor = AppColors.White,
                                        disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
                                        disabledContentColor = AppColors.Gray500
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "강도 변경",
                                        fontSize = scaledSp(11, modalScale)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Text(
                                text = "대상 절전 상태: " + when (remotePowerSavingState) {
                                    true -> "켜짐"
                                    false -> "꺼짐"
                                    null -> "알 수 없음"
                                },
                                color = when (remotePowerSavingState) {
                                    true -> AppColors.Yellow
                                    false -> AppColors.Green
                                    null -> AppColors.Gray500
                                },
                                fontSize = scaledSp(11, modalScale),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                            ) {
                                Button(
                                    enabled = remoteControlEnabled,
                                    onClick = { onSetRemotePowerSaving(true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Gray800.copy(alpha = 0.7f),
                                        contentColor = AppColors.White,
                                        disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
                                        disabledContentColor = AppColors.Gray500
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(52, scale))
                                ) {
                                    Text("절전 켜기", fontSize = scaledSp(11, modalScale))
                                }
                                Button(
                                    enabled = remoteControlEnabled,
                                    onClick = { onSetRemotePowerSaving(false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Gray800.copy(alpha = 0.7f),
                                        contentColor = AppColors.White,
                                        disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
                                        disabledContentColor = AppColors.Gray500
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(52, scale))
                                ) {
                                    Text("절전 해제", fontSize = scaledSp(11, modalScale))
                                }
                                RepeatActionButton(
                                    label = "비프음",
                                    enabled = remoteControlEnabled,
                                    onTap = onSendRemoteBeep,
                                    isStickyActive = stickyBeep,
                                    onStickyStateChanged = { stickyBeep = it },
                                    isRepeating = isBeepRepeating,
                                    onActiveStateChanged = { isBeepRepeating = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(44, scale))
                                )
                            }
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(scaledDp(6, scale))
                            ) {
                                TextButton(
                                    enabled = remoteControlEnabled,
                                    onClick = {
                                        stickyBeep = false
                                        stickyVibrate = false
                                        stickyHighTone = false
                                        onSendRemoteStop()
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = AppColors.Gray800.copy(alpha = 0.7f),
                                        contentColor = AppColors.White,
                                        disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
                                        disabledContentColor = AppColors.Gray500
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(44, scale))
                                ) {
                                    Text("송출 중지", fontSize = scaledSp(11, modalScale))
                                }
                                RepeatActionButton(
                                    label = "저주파",
                                    enabled = remoteControlEnabled,
                                    onTap = onSendRemoteVibrate,
                                    isStickyActive = stickyVibrate,
                                    onStickyStateChanged = { stickyVibrate = it },
                                    isRepeating = isVibrateRepeating,
                                    onActiveStateChanged = { isVibrateRepeating = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(44, scale))
                                )
                                RepeatActionButton(
                                    label = "고주파",
                                    enabled = remoteControlEnabled,
                                    onTap = onSendRemoteHighTone,
                                    isStickyActive = stickyHighTone,
                                    onStickyStateChanged = { stickyHighTone = it },
                                    isRepeating = isHighToneRepeating,
                                    onActiveStateChanged = { isHighToneRepeating = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(scaledDp(44, scale))
                                )
                            }
                            Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                            val repeatingItems = buildList {
                                if (isBeepRepeating) add("비프음")
                                if (isVibrateRepeating) add("저주파")
                                if (isHighToneRepeating) add("고주파")
                            }
                            Text(
                                text = if (repeatingItems.isNotEmpty()) {
                                    "반복 송출 중: ${repeatingItems.joinToString(", ")} · [송출 중지]로 해제"
                                } else {
                                    "안내: 비프음/저주파/고주파를 3초 길게 누르면 반복 고정"
                                },
                                color = if (repeatingItems.isNotEmpty()) AppColors.Green else AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale),
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(scaledDp(8, scale)))
                            Text(
                                text = "절전 켜기/해제: 대상 기기의 절전 상태를 직접 변경합니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Text(
                                text = "비프음: 대상 기기에서 경고음을 재생합니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Text(
                                text = "저주파: 대상 기기에서 진동을 울립니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Text(
                                text = "고주파: 대상 기기에서 높은 주파수대의 음을 재생합니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Text(
                                text = "강도 변경: 알림 강도를 약/중/강으로 순환합니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Text(
                                text = "송출 중지: 현재 실행 중인 반복 송출을 즉시 멈춥니다.",
                                color = AppColors.Gray500,
                                fontSize = scaledSp(10, modalScale)
                            )
                            Spacer(modifier = Modifier.height(scaledDp(14, scale)))
                            Text(
                                text = "닫기",
                                color = AppColors.Green,
                                fontSize = scaledSp(12, modalScale),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable { showControlModal = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceStatusCard(
    distanceMeters: Float?,
    distanceSource: DistanceMeasurementSource,
    distanceTrend: DistanceTrend,
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    val hasDistance =
        distanceMeters != null && distanceMeters.isFinite() && distanceMeters > 0f
    val distanceText = if (hasDistance) {
        String.format(Locale.getDefault(), "%.1f m", distanceMeters)
    } else {
        "탐색 중"
    }
    val sourceText = when (distanceSource) {
        DistanceMeasurementSource.UWB -> "UWB"
        DistanceMeasurementSource.RTT -> "RTT"
        DistanceMeasurementSource.RSSI -> "RSSI"
        DistanceMeasurementSource.NONE -> "신호 대기"
    }
    val trendText = when {
        !hasDistance -> "거리 측정 준비 중"
        distanceTrend == DistanceTrend.Approaching -> "가까워지는 중"
        distanceTrend == DistanceTrend.Receding -> "멀어지는 중"
        else -> "거리 유지"
    }

    Surface(
        color = AppColors.Gray800.copy(alpha = 0.58f),
        shape = RoundedCornerShape(scaledDp(14, scale)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = scaledDp(14, scale),
                    vertical = scaledDp(10, scale)
                ),
            verticalArrangement = Arrangement.spacedBy(scaledDp(4, scale))
        ) {
            Text(
                text = "현재 거리",
                color = AppColors.Gray500,
                fontSize = scaledSp(11, scale),
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = distanceText,
                    color = AppColors.White,
                    fontSize = scaledSp(22, scale),
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = sourceText,
                    color = AppColors.Gray400,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = trendText,
                color = AppColors.Gray400,
                fontSize = scaledSp(11, scale),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PttChatPanel(
    roomTitle: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    showControlBadge: Boolean = false,
    onOpenControls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scale = LocalAppScale.current
    var inputValue by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var currentRecordingJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = modifier
            .background(
                color = AppColors.Gray900.copy(alpha = 0.82f),
                shape = RoundedCornerShape(scaledDp(14, scale))
            )
            .border(
                width = scaledDp(1, scale),
                color = AppColors.Gray500.copy(alpha = 0.35f),
                shape = RoundedCornerShape(scaledDp(14, scale))
            )
            .padding(scaledDp(12, scale))
    ) {
        Text(
            text = roomTitle,
            color = AppColors.White,
            fontSize = scaledSp(12, scale),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        AutoScrollChatList(
            messages = messages,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            listModifier = Modifier.fillMaxWidth(),
            verticalSpacing = scaledDp(6, scale)
        ) { message ->
            PttChatMessageBubble(message = message)
        }
        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(scaledDp(44, scale))
                    .background(AppColors.Gray800, RoundedCornerShape(scaledDp(22, scale)))
                    .padding(horizontal = scaledDp(14, scale)),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputValue.isEmpty()) {
                    Text(
                        text = "메시지 입력...",
                        color = AppColors.Gray500,
                        fontSize = scaledSp(11, scale)
                    )
                }
                BasicTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = AppColors.White,
                        fontSize = scaledSp(11, scale)
                    ),
                    cursorBrush = SolidColor(AppColors.Green)
                )
            }
            Box(
                modifier = Modifier
                    .size(scaledDp(40, scale))
                    .background(
                        if (inputValue.isNotBlank() || isMicOn) AppColors.Green else AppColors.Gray800,
                        CircleShape
                    )
                    .clickable {
                        if (inputValue.isNotBlank()) {
                            val payload = inputValue.trim()
                            if (payload.isNotBlank()) {
                                onSend(payload)
                                inputValue = ""
                            }
                            return@clickable
                        }
                        if (isMicOn) {
                            currentRecordingJob?.cancel()
                            onMicRelease()
                            currentRecordingJob = null
                        } else {
                            currentRecordingJob = coroutineScope.launch {
                                onMicPress()
                                delay(9_000L)
                                onMicRelease()
                                currentRecordingJob = null
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (inputValue.isNotBlank()) {
                    Text(
                        text = "전송",
                        color = AppColors.PureWhite,
                        fontSize = scaledSp(10, scale),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Image(
                        painter = painterResource(id = if (isMicOn) R.drawable.ic_mic_red else R.drawable.ic_mic),
                        contentDescription = if (isMicOn) "MIC 녹음 중" else "MIC 대기",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(scaledDp(18, scale))
                    )
                }
            }
            TextButton(
                onClick = {
                    onOpenControls()
                }
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "원격 제어 열기",
                        tint = AppColors.White
                    )
                    if (showControlBadge) {
                        Box(
                            modifier = Modifier
                                .size(scaledDp(8, scale))
                                .background(AppColors.Red, shape = CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PttChatMessageBubble(message: ChatMessage) {
    val scale = LocalAppScale.current
    val background = if (message.isMine) Color(0xFF2B2F33) else Color(0xFFF27B7B)
    val textColor = AppColors.PureWhite
    val voicePath = message.text.takeIf { it.startsWith(VOICE_PREFIX) }
        ?.removePrefix(VOICE_PREFIX)
        ?.trim()
    val labelText = if (message.isMine) {
        "나"
    } else {
        val rawSender = message.senderName?.trim().orEmpty()
        if (rawSender.isNotBlank()) {
            rawSender.replace(Regex("\\[[^\\]]{4}\\]$"), "").trim().ifBlank { rawSender }
        } else {
            "상대"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            Text(
                text = labelText,
                color = AppColors.Gray500,
                fontSize = scaledSp(9, scale),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(scaledDp(2, scale)))
            if (!voicePath.isNullOrBlank()) {
                PttAudioMessageBubble(
                    path = voicePath,
                    isMine = message.isMine
                )
            } else {
                Box(
                    modifier = Modifier
                        .background(background, shape = RoundedCornerShape(scaledDp(12, scale)))
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
                ) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = scaledSp(11, scale)
                    )
                }
            }
        }
    }
}

@Composable
private fun PttAudioMessageBubble(
    path: String,
    isMine: Boolean
) {
    val scale = LocalAppScale.current
    val background = if (isMine) Color(0xFF2B2F33) else Color(0xFFF27B7B)
    var isReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }

    val mediaPlayer = remember(path) {
        MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                durationMs = duration
                isReady = true
            } catch (_: Exception) {
                isReady = false
            }
        }
    }
    val stopSelf = remember(mediaPlayer) {
        {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            } catch (_: Exception) {
            }
            isPlaying = false
        }
    }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            positionMs = durationMs
            PttVoicePlaybackController.clear(stopSelf)
        }
        onDispose {
            PttVoicePlaybackController.clear(stopSelf)
            try {
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isPlaying, isReady) {
        if (!isPlaying || !isReady) return@LaunchedEffect
        while (isPlaying && mediaPlayer.isPlaying) {
            positionMs = mediaPlayer.currentPosition
            delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.74f)
            .background(background, shape = RoundedCornerShape(scaledDp(12, scale)))
            .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(8, scale))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
        ) {
            TextButton(
                enabled = isReady,
                onClick = {
                    if (isPlaying) {
                        stopSelf()
                    } else {
                        PttVoicePlaybackController.requestPlay(stopSelf)
                        mediaPlayer.start()
                        isPlaying = true
                    }
                }
            ) {
                Text(
                    text = if (isPlaying) "Pause" else "Play",
                    fontSize = scaledSp(10, scale),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PureWhite
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaledDp(3, scale))
                        .background(AppColors.PureWhite.copy(alpha = 0.35f), shape = RoundedCornerShape(999.dp))
                ) {
                    val progress = if (durationMs > 0) {
                        positionMs.toFloat() / durationMs.toFloat()
                    } else {
                        0f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(scaledDp(3, scale))
                            .background(AppColors.PureWhite, shape = RoundedCornerShape(999.dp))
                    )
                }
                Spacer(modifier = Modifier.height(scaledDp(3, scale)))
                Text(
                    text = "${formatAudioTime(positionMs)}/${formatAudioTime(durationMs)}",
                    color = AppColors.PureWhite.copy(alpha = 0.85f),
                    fontSize = scaledSp(9, scale)
                )
            }
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private const val VOICE_PREFIX = "[voice] "

private object PttVoicePlaybackController {
    private var stopCurrent: (() -> Unit)? = null

    fun requestPlay(onStop: () -> Unit) {
        if (stopCurrent !== onStop) {
            stopCurrent?.invoke()
            stopCurrent = onStop
        }
    }

    fun clear(onStop: () -> Unit) {
        if (stopCurrent === onStop) {
            stopCurrent = null
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
private fun RepeatActionButton(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    isStickyActive: Boolean = false,
    onStickyStateChanged: (Boolean) -> Unit = {},
    isRepeating: Boolean,
    onActiveStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentTap by rememberUpdatedState(onTap)
    val currentStickyStateChanged by rememberUpdatedState(onStickyStateChanged)
    val currentActiveStateChanged by rememberUpdatedState(onActiveStateChanged)
    var suppressTap by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed, enabled, isStickyActive) {
        if (!enabled) {
            if (isStickyActive) {
                currentStickyStateChanged(false)
            }
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        if (isStickyActive) {
            currentActiveStateChanged(true)
            return@LaunchedEffect
        }
        if (!isPressed) {
            currentActiveStateChanged(false)
            return@LaunchedEffect
        }
        delay(3_000L)
        if (!isPressed || isStickyActive) return@LaunchedEffect
        // Consume release click once so sticky mode does not immediately toggle off.
        suppressTap = true
        currentStickyStateChanged(true)
        currentActiveStateChanged(true)
    }
    TextButton(
        enabled = enabled,
        onClick = {
            if (suppressTap) {
                suppressTap = false
                return@TextButton
            }
            if (isStickyActive) {
                currentStickyStateChanged(false)
                currentActiveStateChanged(false)
                return@TextButton
            }
            currentTap()
        },
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isRepeating) {
                AppColors.Gray700.copy(alpha = 0.9f)
            } else {
                AppColors.Gray800.copy(alpha = 0.7f)
            },
            contentColor = AppColors.White,
            disabledContainerColor = AppColors.Gray800.copy(alpha = 0.35f),
            disabledContentColor = AppColors.Gray500
        ),
        modifier = modifier
    ) {
        Text(
            text = if (isRepeating) "$label · 반복중" else label,
            fontSize = scaledSp(11, LocalAppScale.current),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class MeshGraphUiState(
    val nodes: List<MeshNode>,
    val edges: List<MeshEdge>
)

private fun buildMeshGraphState(
    snapshot: com.example.lifesaivior.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>
): MeshGraphUiState {
    if (snapshot.nodes.isEmpty()) {
        val selfId = myPeerId.trim().ifBlank { "self" }
        val selfLabel = myNickname.trim().ifBlank { selfId }
        return MeshGraphUiState(
            nodes = listOf(
                MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel)
            ),
            edges = emptyList()
        )
    }

    val selfId = myPeerId
    val adjacency = mutableMapOf<String, MutableSet<String>>()
    snapshot.edges.forEach { edge ->
        adjacency.getOrPut(edge.a) { mutableSetOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableSetOf() }.add(edge.a)
    }

    val hops = mutableMapOf<String, Int>()
    val queue = ArrayDeque<String>()
    hops[selfId] = 0
    queue.add(selfId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val nextHop = (hops[current] ?: 0) + 1
        adjacency[current].orEmpty().forEach { neighbor ->
            if (hops.containsKey(neighbor)) return@forEach
            hops[neighbor] = nextHop
            queue.add(neighbor)
        }
    }

    val nodes = mutableListOf<MeshNode>()
    val selfLabel = myNickname.trim().ifBlank { myPeerId.trim().ifBlank { "self" } }
    nodes.add(MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel))

    snapshot.nodes.forEach { node ->
        if (node.peerId == selfId) return@forEach
        val hop = (hops[node.peerId] ?: 2).coerceAtLeast(1)
        val signal = when (hop) {
            1 -> 0.8f
            2 -> 0.55f
            else -> 0.4f
        }
        val label = node.nickname?.takeIf { it.isNotBlank() }
            ?: peerNicknames[node.peerId]
            ?: node.peerId
        nodes.add(
            MeshNode(
                id = node.peerId,
                hop = hop,
                signal = signal,
                label = label
            )
        )
    }

    val edges = snapshot.edges.map { edge ->
        MeshEdge(
            a = edge.a,
            b = edge.b,
            isConfirmed = edge.isConfirmed,
            confirmedBy = edge.confirmedBy
        )
    }

    return MeshGraphUiState(nodes = nodes, edges = edges)
}

