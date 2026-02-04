package com.example.lifesaiver.ui.screen.rescuer.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun RescuerChatScreen(
    roomTitle: String,
    meshPeerCount: Int,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onSendProfileTest: () -> Unit,
    onPrev: () -> Unit,
    onSend: (String) -> Unit
) {
    val (inputValue, setInputValue) = remember { mutableStateOf("") }
    val scale = LocalAppScale.current
    val participantCount = meshPeerCount.coerceAtLeast(0)
    val (showSignatureLog, setShowSignatureLog) = remember { mutableStateOf(false) }
    val (showDbLog, setShowDbLog) = remember { mutableStateOf(false) }

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
                        .clickable { setShowSignatureLog(true) }
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
                        .clickable { setShowDbLog(true) }
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
                    onValueChange = setInputValue,
                    placeholder = {
                        Text("메세지 입력...", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
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
                                        onSend(inputValue.trim())
                                        setInputValue("")
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
                onDismiss = { setShowSignatureLog(false) },
                onClear = onClearSignatureLogs
            )
        }
        if (showDbLog) {
            DbLogDialog(
                entries = profileLogs,
                onDismiss = { setShowDbLog(false) },
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
    val labelText = if (message.isMine) {
        "나"
    } else {
        val rawSender = message.senderName?.trim().orEmpty()
        if (rawSender.isNotBlank()) {
            rawSender.replace(Regex("\\[[^\\]]{4}\\]$"), "").trim().ifBlank { rawSender }
        } else {
            "상대방"
        }
    }
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
            Box(
                modifier = Modifier
                    .background(background, shape = RoundedCornerShape(scaledDp(16, scale)))
                    .padding(horizontal = scaledDp(14, scale), vertical = scaledDp(8, scale))
            ) {
                Text(text = message.text, color = textColor, fontSize = scaledSp(12, scale))
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
