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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.SecondaryButton
import com.example.lifesaiver.ui.components.SecondaryButtonVariant
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.launch

@Composable
fun RescuerChatScreen(
    roomTitle: String,
    meshPeerCount: Int,
    messages: List<ChatMessage>,
    onPrev: () -> Unit,
    onSend: (String) -> Unit
) {
    val (inputValue, setInputValue) = remember { mutableStateOf("") }
    val scale = LocalAppScale.current
    val participantCount = meshPeerCount.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isNearBottom by remember {
        derivedStateOf {
            if (messages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val lastIndex = messages.lastIndex
                lastVisibleIndex >= lastIndex - 2
            }
        }
    }
    val hasUserScrolledUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showScrollToBottom by remember {
        derivedStateOf {
            messages.isNotEmpty() && hasUserScrolledUp && !isNearBottom
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && (isNearBottom || !listState.canScrollForward)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
            // ?쇱そ: ?댁쟾 踰꾪듉
            SecondaryButton(
                label = "이전",
                variant = SecondaryButtonVariant.Gray,
                onClick = onPrev,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            // 以묒븰: 梨꾪똿諛??쒕ぉ
            Text(
                text = roomTitle,
                color = AppColors.White,
                fontSize = scaledSp(14, scale),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )

            Text(
                text = "인원 ${participantCount}명",
                color = AppColors.Green,
                fontSize = scaledSp(12, scale),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(scaledDp(8, scale)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = scaledDp(24, scale)),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(scaledDp(10, scale))
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }
            if (showScrollToBottom) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = scaledDp(24, scale),
                            bottom = scaledDp(12, scale)
                        )
                        .background(
                            color = AppColors.Gray800,
                            shape = RoundedCornerShape(scaledDp(20, scale))
                        )
                        .clickable {
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.lastIndex)
                                }
                            }
                        }
                        .padding(
                            horizontal = scaledDp(14, scale),
                            vertical = scaledDp(8, scale)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        tint = AppColors.White
                    )
                }
            }
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
