package com.example.lifesaivior.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.ui.theme.AppColors
import com.example.lifesaivior.ui.theme.LocalAppScale
import com.example.lifesaivior.ui.theme.scaledDp
import kotlinx.coroutines.launch

@Composable
fun AutoScrollChatList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    verticalSpacing: Dp,
    contentPadding: PaddingValues = PaddingValues(),
    initialScrollToBottom: Boolean = false,
    nearBottomThreshold: Int = 2,
    itemKey: ((ChatMessage) -> Any)? = null,
    itemContent: @Composable (ChatMessage) -> Unit
) {
    val scale = LocalAppScale.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var didInitialScroll by remember { mutableStateOf(false) }

    val isNearBottom by remember {
        derivedStateOf {
            if (messages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val lastIndex = messages.lastIndex
                lastVisibleIndex >= lastIndex - nearBottomThreshold
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

    LaunchedEffect(messages.size, initialScrollToBottom) {
        if (!initialScrollToBottom || didInitialScroll) return@LaunchedEffect
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
        }
    }

    LaunchedEffect(messages.size, isNearBottom) {
        if (messages.isNotEmpty() && (isNearBottom || !listState.canScrollForward)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = listModifier,
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            if (itemKey != null) {
                items(messages, key = itemKey) { message ->
                    itemContent(message)
                }
            } else {
                items(messages) { message ->
                    itemContent(message)
                }
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
}
