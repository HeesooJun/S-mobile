package com.example.lifesaivior.ui.components.chat

import android.media.MediaPlayer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaivior.core.model.ChatMessage
import kotlinx.coroutines.delay

private const val VOICE_PREFIX = "[voice] "

@Composable
fun ChatMessageBubble(message: ChatMessage, senderName: String? = null) {
    val isMine = message.isMine

    // 피그마 디자인 색상 반영
    val bubbleColor = if (isMine) Color(0xFF2B2F33) else Color(0xFFF27B7B)
    val resolvedSenderName = senderName
        ?: message.senderName?.takeIf { it.isNotBlank() }
        ?: message.senderPeerId?.let { "익명 (${it.take(4)})" }
    val labelText = if (isMine) "나" else (resolvedSenderName ?: "조난자")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        // 이름 라벨
        Text(
            text = labelText,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        val voicePath = message.text.takeIf { it.startsWith(VOICE_PREFIX) }
            ?.removePrefix(VOICE_PREFIX)
            ?.trim()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            if (voicePath != null && voicePath.isNotBlank()) {
                AudioMessageBubble(path = voicePath, isMine = isMine, backgroundColor = bubbleColor)
            } else {
                Box(
                    modifier = Modifier
                        .background(
                            color = bubbleColor,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 0.dp,
                                bottomEnd = if (isMine) 0.dp else 16.dp
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMessageBubble(path: String, isMine: Boolean, backgroundColor: Color) {
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
            .background(
                backgroundColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 0.dp,
                    bottomEnd = if (isMine) 0.dp else 16.dp
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .width(200.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                    .clickable(enabled = isReady) {
                        if (isPlaying) stopSelf()
                        else {
                            VoicePlaybackController.requestPlay(stopSelf)
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "||" else "▶",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

private object VoicePlaybackController {
    private var stopCurrent: (() -> Unit)? = null
    fun requestPlay(onStop: () -> Unit) {
        if (stopCurrent !== onStop) {
            stopCurrent?.invoke()
            stopCurrent = onStop
        }
    }
    fun clear(onStop: () -> Unit) {
        if (stopCurrent === onStop) stopCurrent = null
    }
}
