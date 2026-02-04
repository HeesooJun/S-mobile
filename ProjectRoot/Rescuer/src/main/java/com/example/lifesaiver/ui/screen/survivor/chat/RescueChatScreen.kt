package com.example.lifesaiver.ui.screen.survivor.chat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.media.MediaPlayer
import androidx.compose.runtime.LaunchedEffect
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.components.chat.AutoScrollChatList
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.components.DbLogDialog
import com.example.lifesaiver.ui.components.SignatureLogDialog
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import kotlinx.coroutines.delay

@Composable
fun RescueChatScreen(
    roomTitle: String,
    meshPeerCount: Int,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onSendProfileTest: () -> Unit,
    onPrev: () -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    val scale = LocalAppScale.current
    val participantCount = meshPeerCount.coerceAtLeast(0)
    var showSignatureLog by remember { mutableStateOf(false) }
    var showDbLog by remember { mutableStateOf(false) }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale), vertical = scaledDp(8, scale))
        ) {
            // 좌측: 이전 버튼
            SecondaryButton(
                label = "이전",
                variant = SecondaryButtonVariant.Gray,
                onClick = onPrev,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            // 중앙: 채팅방 제목
            Text(
                text = roomTitle,
                color = AppColors.White,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaledDp(8, scale))
            ) {
                Text(
                    text = "인원 ${participantCount}명",
                    color = AppColors.Green,
                    fontSize = scaledSp(12, scale)
                )
                Box(
                    modifier = Modifier
                        .background(AppColors.Gray700, shape = RoundedCornerShape(scaledDp(12, scale)))
                        .clickable { showSignatureLog = true }
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
                ) {
                    Text(
                        text = "로그",
                        color = AppColors.White,
                        fontSize = scaledSp(10, scale),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(AppColors.Gray700, shape = RoundedCornerShape(scaledDp(12, scale)))
                        .clickable { showDbLog = true }
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
                ) {
                    Text(
                        text = "DB 로그",
                        color = AppColors.White,
                        fontSize = scaledSp(10, scale),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(AppColors.Gray700, shape = RoundedCornerShape(scaledDp(12, scale)))
                        .clickable { onSendProfileTest() }
                        .padding(horizontal = scaledDp(10, scale), vertical = scaledDp(6, scale))
                ) {
                    Text(
                        text = "TLV 전송",
                        color = AppColors.White,
                        fontSize = scaledSp(10, scale),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        AutoScrollChatList(
            messages = messages,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            listModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale)),
            verticalSpacing = scaledDp(10, scale)
        ) { message ->
            MessageBubble(message = message)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(20, scale), vertical = scaledDp(16, scale))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = AppColors.Gray800,
                        shape = RoundedCornerShape(scaledDp(28, scale))
                    )
                    .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(10, scale)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaledDp(10, scale))
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text("메세지 입력...", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AppColors.White,
                        fontSize = scaledSp(12, scale)
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.Gray800,
                        unfocusedContainerColor = AppColors.Gray800,
                        disabledContainerColor = AppColors.Gray800,
                        focusedIndicatorColor = AppColors.Gray800,
                        unfocusedIndicatorColor = AppColors.Gray800,
                        cursorColor = AppColors.Green,
                        focusedTextColor = AppColors.White,
                        unfocusedTextColor = AppColors.White
                    )
                )

                Box(
                    modifier = Modifier
                        .background(AppColors.GreenSoft, shape = RoundedCornerShape(scaledDp(20, scale)))
                        .padding(horizontal = scaledDp(16, scale), vertical = scaledDp(10, scale))
                        .then(
                            if (inputValue.isNotBlank()) {
                                Modifier
                                    .clickable {
                                        onSendClick()
                                    }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = "전송",
                        color = AppColors.Green,
                        fontSize = scaledSp(12, scale),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        if (showSignatureLog) {
            SignatureLogDialog(
                entries = signatureLogs,
                onDismiss = { showSignatureLog = false },
                onClear = onClearSignatureLogs
            )
        }
        if (showDbLog) {
            DbLogDialog(
                entries = profileLogs,
                onDismiss = { showDbLog = false },
                onClear = onClearProfileLogs
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val scale = LocalAppScale.current
    val background = if (message.isMine) AppColors.GreenSoft else AppColors.Gray800
    val textColor = if (message.isMine) AppColors.Green else AppColors.White
    val labelText = message.senderName?.takeIf { it.isNotBlank() }
        ?: message.senderPeerId?.let { "구조자[${it.take(4)}]" }
        ?: if (message.isMine) "구조자[----]" else "상대방"
    val voicePath = message.text.takeIf { it.startsWith(VOICE_PREFIX) }
        ?.removePrefix(VOICE_PREFIX)
        ?.trim()
    val pathLabel = message.path?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
        ) {
            Text(
                text = labelText,
                color = AppColors.Gray500,
                fontSize = scaledSp(10, scale),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(scaledDp(2, scale)))
            if (voicePath != null && voicePath.isNotBlank()) {
                AudioMessageBubble(path = voicePath, isMine = message.isMine)
            } else {
                Box(
                    modifier = Modifier
                        .background(background, shape = RoundedCornerShape(scaledDp(16, scale)))
                        .padding(horizontal = scaledDp(14, scale), vertical = scaledDp(8, scale))
                ) {
                    Text(text = message.text, color = textColor, fontSize = scaledSp(12, scale))
                }
            }
            if (pathLabel != null) {
                Spacer(modifier = Modifier.height(scaledDp(4, scale)))
                Text(
                    text = "path=$pathLabel",
                    color = AppColors.Gray500,
                    fontSize = scaledSp(10, scale)
                )
            }
        }
    }
}

@Composable
private fun AudioMessageBubble(path: String, isMine: Boolean) {
    val scale = LocalAppScale.current
    val background = if (isMine) AppColors.GreenSoft else AppColors.Gray800
    val textColor = if (isMine) AppColors.Green else AppColors.White
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
            VoicePlaybackController.clear(stopSelf)
        }
        onDispose {
            VoicePlaybackController.clear(stopSelf)
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
            .background(background, shape = RoundedCornerShape(scaledDp(16, scale)))
            .padding(horizontal = scaledDp(14, scale), vertical = scaledDp(10, scale))
            .width(scaledDp(220, scale))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(scaledDp(8, scale))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaledDp(10, scale))
            ) {
                Box(
                    modifier = Modifier
                        .size(scaledDp(32, scale))
                        .background(AppColors.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(999.dp))
                        .clickable(enabled = isReady) {
                            if (isPlaying) {
                                stopSelf()
                            } else {
                                VoicePlaybackController.requestPlay(stopSelf)
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        color = textColor,
                        fontSize = scaledSp(14, scale),
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaledDp(4, scale))
                            .background(AppColors.Gray700, shape = RoundedCornerShape(999.dp))
                    ) {
                        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(scaledDp(4, scale))
                                .background(AppColors.Green, shape = RoundedCornerShape(999.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(scaledDp(4, scale)))
                    Text(
                        text = "${formatTime(positionMs)}/${formatTime(durationMs)}",
                        color = textColor,
                        fontSize = scaledSp(10, scale)
                    )
                }
            }
            if (!isReady) {
                Text(
                    text = "음성 파일을 불러오지 못했습니다.",
                    color = AppColors.Red,
                    fontSize = scaledSp(10, scale)
                )
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private const val VOICE_PREFIX = "[voice] "

private object VoicePlaybackController {
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
