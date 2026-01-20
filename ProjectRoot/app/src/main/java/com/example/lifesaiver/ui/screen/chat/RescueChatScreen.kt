package com.example.lifesaiver.ui.screen.chat

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun rememberCurrentTimeText(
    pattern: String = "HH:mm",
    updateIntervalMillis: Long = 60_000L // 1분마다 갱신
): State<String> {
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    val timeState = remember { mutableStateOf(LocalTime.now().format(formatter)) }

    LaunchedEffect(pattern, updateIntervalMillis) {
        while (true) {
            timeState.value = LocalTime.now().format(formatter)
            delay(updateIntervalMillis)
        }
    }
    return timeState
}

@Composable
fun RescueChatScreen(
    roomTitle: String,
    messages: List<ChatMessage>,
    onPrev: () -> Unit,
    onSend: (String) -> Unit
) {
    val (inputValue, setInputValue) = remember { mutableStateOf("") }
    val scale = LocalAppScale.current
    val currentTime by rememberCurrentTimeText(pattern = "HH:mm", updateIntervalMillis = 60_000L)

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale), vertical = scaledDp(24, scale)),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Offline", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
            Text(text = currentTime, color = AppColors.Gray500, fontSize = scaledSp(12, scale)) // ✅ 여기
        }

        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(24, scale), vertical = scaledDp(8, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(label = "이전", variant = SecondaryButtonVariant.Gray, onClick = onPrev)
            Spacer(modifier = Modifier.width(scaledDp(12, scale)))
            Text(
                text = roomTitle,
                color = AppColors.White,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = scaledDp(24, scale)),
            verticalArrangement = Arrangement.spacedBy(scaledDp(10, scale))
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
        }

        Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = scaledDp(20, scale),
                                vertical = scaledDp(16, scale)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = AppColors.Gray800,
                                    shape = RoundedCornerShape(scaledDp(28, scale))
                                )
                                .padding(
                                    horizontal = scaledDp(16, scale),
                                    vertical = scaledDp(10, scale)
                                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaledDp(10, scale))
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = setInputValue,
                    placeholder = {
                        Text("메시지 입력...", color = AppColors.Gray500, fontSize = scaledSp(12, scale))
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
                        .background(
                            AppColors.GreenSoft,
                            shape = RoundedCornerShape(scaledDp(20, scale))
                        )
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
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val scale = LocalAppScale.current
    val background = if (message.isMine) AppColors.GreenSoft else AppColors.Gray800
    val textColor = if (message.isMine) AppColors.Green else AppColors.White
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(background, shape = RoundedCornerShape(scaledDp(16, scale)))
                .padding(horizontal = scaledDp(14, scale), vertical = scaledDp(8, scale))
        ) {
            Text(text = message.text, color = textColor, fontSize = scaledSp(12, scale))
        }
    }
}
