package com.example.lifesaiver.ui.screen.survivor.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

private const val AUTO_RECORDING_DURATION_MS = 9000L

@Composable
fun RescueChatContent(
    roomTitle: String,
    participants: List<Pair<String, String>>,
    messages: List<ChatMessage>,
    onPrev: () -> Unit,
    onSettings: () -> Unit, // 파라미터 유지 (AppNavHost 호환)
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

    var debugClickCount by remember { mutableStateOf(0) }
    var isDebugMode by remember { mutableStateOf(false) }

    // 메인 컨텐츠 영역
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent) // BottomSheet 컨테이너가 배경을 담당함
    ) {
        // 메인 채팅 영역
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header - 타이틀과 인원 아이콘 정렬, 설정 버튼 제거
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = roomTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                debugClickCount++
                                if (debugClickCount >= 5) isDebugMode = true
                            }
                        )
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

                    // 인원 아이콘 (높이 타이틀에 맞춤)
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "People",
                        tint = if (showInwon) Color(0xFF3BBF8C) else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showInwon = !showInwon }
                    )
                }

                // Chat List - 배경 클릭 시 모달 닫기 기능 추가
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPrev() }
                ) {
                    AutoScrollChatList(
                        messages = messages,
                        modifier = Modifier.fillMaxSize(),
                        listModifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalSpacing = 8.dp
                    ) { message ->
                        // 메시지 버블 클릭 시에는 닫히지 않도록
                        Box(modifier = Modifier.clickable(enabled = false) { }) {
                            ChatMessageBubble(message = message)
                        }
                    }
                }

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
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
                                            delay(AUTO_RECORDING_DURATION_MS)
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

            // 인원 목록 열려 있을 때 오버레이
            if (showInwon) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showInwon = false }
                )
            }
        }

        // 우측 인원 목록 (사이드바)
        if (showInwon) {
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                    .padding(top = 24.dp)
                    .clickable(enabled = false) { },
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
                        val truncatedName = if (displayName.length > 6) "${displayName.take(6)}..." else displayName

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
