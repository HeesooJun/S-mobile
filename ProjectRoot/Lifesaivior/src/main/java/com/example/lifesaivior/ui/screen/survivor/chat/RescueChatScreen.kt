package com.example.lifesaivior.ui.screen.survivor.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.lifesaivior.core.model.ChatMessage
import com.example.lifesaivior.ui.components.ScreenScaffold
import com.example.lifesaivior.ui.components.DbLogDialog
import com.example.lifesaivior.ui.components.SignatureLogDialog
import com.example.lifesaivior.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaivior.protocol.security.SignatureLogEntry
import com.example.lifesaivior.protocol.mesh.MeshGraphRegistry

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
    onSendClick: () -> Unit,
    autoPlayMessageKey: String? = null,
    onAutoPlayMessageConsumed: (String) -> Unit = {}
) {
    var showSignatureLog by remember { mutableStateOf(false) }
    var showDbLog by remember { mutableStateOf(false) }

    ScreenScaffold(
        gradient = listOf(Color.Transparent, Color.Transparent),
        vignetteColor = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            RescueChatContent(
                roomTitle = roomTitle,
                participants = peerNodes.map { it.peerId to (it.nickname ?: "") },
                messages = messages,
                onPrev = onPrev,
                onSettings = onSettings,
                onShowSignatureLog = { showSignatureLog = true },
                onShowDbLog = { showDbLog = true },
                onSendProfileTest = onSendProfileTest,
                autoPlayMessageKey = autoPlayMessageKey,
                onAutoPlayMessageConsumed = onAutoPlayMessageConsumed,
                inputValue = inputValue,
                onInputChange = onInputChange,
                onSendClick = onSendClick,
                onMicPress = onMicPress,
                onMicRelease = onMicRelease,
                isMicOn = isMicOn
            )

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
