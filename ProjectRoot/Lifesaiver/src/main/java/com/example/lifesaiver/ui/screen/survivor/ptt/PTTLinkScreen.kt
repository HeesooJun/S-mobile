package com.example.lifesaiver.ui.screen.survivor.ptt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.ui.components.PowerSavingLayer
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.ptt.PttActionType
import com.example.lifesaiver.ui.components.ptt.PttActionsBlock
import com.example.lifesaiver.ui.components.ptt.PttBottomBar
import com.example.lifesaiver.ui.components.ptt.PttHeroSection
import com.example.lifesaiver.ui.components.ptt.PttMeshOverlay
import com.example.lifesaiver.ui.components.ptt.PttTopBar
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

@Suppress("UNUSED_PARAMETER")
@Composable
fun PTTLinkScreen(
    batteryLevel: Int,
    connectedCount: Int,
    meshPeerCount: Int,
    directPeerIds: List<String>,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    bleDebugStats: BleDebugStats,
    isConnected: Boolean,
    isMicOn: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    onProfile: () -> Unit,
    onPanicClear: () -> Unit,
    onSettings: () -> Unit
) {
    val scale = LocalAppScale.current
    val (isPowerSaving, setPowerSaving) = remember { mutableStateOf(false) }
    var expandedAction by remember { mutableStateOf<PttActionType?>(null) }
    var showDoubleTapHint by remember { mutableStateOf(false) }
    var showMeshMap by remember { mutableStateOf(false) }

    val meshDisplayCount = meshPeerCount.coerceAtLeast(0)
    val displayConnectedCount = meshDisplayCount
    val hasMeshPeers = meshDisplayCount > 0
    val isLinkActive = isConnected || hasMeshPeers
    val linkStatusLabel = when {
        hasMeshPeers -> "메쉬 연결됨"
        isConnected -> "구조자 연결됨"
        else -> "구조자 연결 대기 중"
    }

    val meshGraphState = remember(meshGraphSnapshot, myPeerId, myNickname, peerNicknames) {
        buildPttMeshGraphState(
            snapshot = meshGraphSnapshot,
            myPeerId = myPeerId,
            myNickname = myNickname,
            peerNicknames = peerNicknames
        )
    }

    LaunchedEffect(expandedAction) {
        if (expandedAction == PttActionType.Disconnect) {
            showDoubleTapHint = true
            delay(3500)
            showDoubleTapHint = false
        } else {
            showDoubleTapHint = false
        }
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PowerSavingLayer(
                isPowerSaving = isPowerSaving,
                isForceExit = !isPowerSaving,
                onRequestExitPowerSaving = { setPowerSaving(false) }
            )

            PttTopBar(
                onPanicClear = onPanicClear,
                onSettings = onSettings
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = scaledDp(32, scale))
                    .offset(y = scaledDp(16, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PttHeroSection(
                    isMicOn = isMicOn,
                    isLinkActive = isLinkActive,
                    statusLabel = linkStatusLabel,
                    onMicPress = onMicPress,
                    onMicRelease = onMicRelease
                )

                Spacer(modifier = Modifier.height(scaledDp(64, scale)))

                PttActionsBlock(
                    expandedAction = expandedAction,
                    displayConnectedCount = displayConnectedCount,
                    showDoubleTapHint = showDoubleTapHint,
                    onPowerClick = {
                        setPowerSaving(!isPowerSaving)
                        expandedAction = PttActionType.Power
                    },
                    onDisconnectClick = {
                        if (expandedAction == PttActionType.Disconnect) {
                            expandedAction = null
                            onDisconnect()
                        } else {
                            expandedAction = PttActionType.Disconnect
                        }
                    },
                    onChatClick = {
                        expandedAction = null
                        onChat()
                    },
                    onUsersClick = {
                        expandedAction = null
                        showMeshMap = true
                    },
                    modifier = Modifier.offset(y = scaledDp(24, scale))
                )
            }

            PttBottomBar(
                isConnected = isConnected,
                onProfile = onProfile,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = scaledDp(32, scale),
                        end = scaledDp(32, scale),
                        bottom = scaledDp(20, scale)
                    )
            )

            if (showMeshMap) {
                PttMeshOverlay(
                    nodes = meshGraphState.nodes,
                    edges = meshGraphState.edges,
                    visualEvents = meshVisualEvents,
                    connectedCount = connectedCount,
                    meshDisplayCount = meshDisplayCount,
                    bleDebugStats = bleDebugStats,
                    onClose = { showMeshMap = false }
                )
            }
        }
    }
}
