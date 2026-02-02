package com.example.lifesaiver.ui.screen.survivor.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.components.ScreenScaffold
 import com.example.lifesaiver.ui.components.DbLogDialog
import com.example.lifesaiver.ui.components.SignatureLogDialog
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.security.SignatureLogEntry
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import kotlinx.coroutines.delay

@Composable
fun RescueChatScreen(
    roomTitle: String,
    meshPeerCount: Int,
    messages: List<ChatMessage>,
    signatureLogs: List<SignatureLogEntry>,
    profileLogs: List<ProfileSyncLogEntry>,
    peerNodes: List<MeshGraphRegistry.GraphNode>,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onClearSignatureLogs: () -> Unit,
    onClearProfileLogs: () -> Unit,
    onSendProfileTest: () -> Unit,
    onPrev: () -> Unit,
    onSettings: () -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var showSignatureLog by remember { mutableStateOf(false) }
    var showDbLog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            delay(300)
            onPrev()
        }
    }

    ScreenScaffold(
        gradient = listOf(Color.Transparent, Color.Transparent),
        vignetteColor = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(enabled = false) { }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 400)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RescueChatContent(
                        roomTitle = roomTitle,
                        participants = peerNodes.map { it.peerId to (it.nickname ?: "") },
                        messages = messages,
                        onPrev = { isVisible = false },
                        onSettings = onSettings,
                        onShowSignatureLog = { showSignatureLog = true },
                        onShowDbLog = { showDbLog = true },
                        onSendProfileTest = onSendProfileTest,
                        inputValue = inputValue,
                        onInputChange = onInputChange,
                        onSendClick = onSendClick,
                        onMicPress = onMicPress,
                        onMicRelease = onMicRelease,
                        isMicOn = isMicOn
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color.Gray.copy(alpha = 0.3f), CircleShape)
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
}
