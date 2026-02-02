package com.example.lifesaiver.ui.screen.survivor.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.R
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.components.chat.AutoScrollChatList
import com.example.lifesaiver.ui.components.chat.ChatMessageBubble
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RescueChatContent(
    roomTitle: String,
    participants: List<Pair<String, String>>,
    messages: List<ChatMessage>,
    onPrev: () -> Unit,
    onSettings: () -> Unit,
    onShowSignatureLog: () -> Unit,
    onShowDbLog: () -> Unit,
    onSendProfileTest: () -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInwon by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var currentRecordingJob by remember { mutableStateOf<Job?>(null) }

    // 디버깅 모드 진입을 위한 상태
    var debugClickCount by remember { mutableStateOf(0) }
    var isDebugMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onPrev() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = roomTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                debugClickCount++
                                if (debugClickCount >= 5) {
                                    isDebugMode = true
                                }
                            }
                        )

                        // 디버깅 모드일 때만 배지 표시
                        AnimatedVisibility(
                            visible = isDebugMode,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DebugBadge(text = "보안", onClick = onShowSignatureLog)
                                DebugBadge(text = "DB", onClick = onShowDbLog)
                                DebugBadge(text = "TLV", onClick = onSendProfileTest)
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onSettings() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "People",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { showInwon = !showInwon }
                    )
                }

                // Chat List
                AutoScrollChatList(
                    messages = messages,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    listModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalSpacing = 8.dp
                ) { message ->
                    ChatMessageBubble(message = message)
                }

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(Color(0xFF2B2F33), RoundedCornerShape(26.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputValue.isEmpty()) {
                            Text("메세지 입력...", color = Color.Gray, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = inputValue,
                            onValueChange = onInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF3BBF8C)),
                            singleLine = true
                        )
                    }

                    val isTyping = inputValue.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isTyping || isMicOn) Color(0xFF3BBF8C) else Color(0xFF2B2F33),
                                CircleShape
                            )
                            .clickable {
                                if (isTyping) {
                                    onSendClick()
                                } else {
                                    if (isMicOn) {
                                        currentRecordingJob?.cancel()
                                        onMicRelease()
                                        currentRecordingJob = null
                                    } else {
                                        currentRecordingJob = coroutineScope.launch {
                                            onMicPress()
                                            delay(9000)
                                            onMicRelease()
                                            currentRecordingJob = null
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTyping) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = if (isMicOn) R.drawable.ic_common_mic_active else R.drawable.ic_common_mic_inactive),
                                contentDescription = "Mic",
                                tint = if (isMicOn) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 우측 인원 목록
            if (showInwon) {
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("현재 인원", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    participants.forEach { (peerId, nickname) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val displayName = nickname.ifBlank { "익명 (${peerId.take(4)})" }
                            val truncatedName = if (displayName.length > 6) {
                                "${displayName.take(6)}..."
                            } else {
                                displayName
                            }

                            Text(
                                text = truncatedName,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugBadge(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = Color.Gray, fontSize = 9.sp)
    }
}
