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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.ui.components.PowerSavingLayer
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.components.ptt.PttActionType
import com.example.lifesaiver.ui.components.ptt.PttActionsBlock
import com.example.lifesaiver.ui.components.ptt.PttMeshOverlay
import com.example.lifesaiver.ui.components.ptt.PttTopBar
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
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
                    .offset(y = scaledDp(-18, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "생존자 네트워크 형성 완료",
                    color = AppColors.Green,
                    fontSize = scaledSp(16, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(scaledDp(6, scale)))
                Text(
                    text = "구조자 연결 시 자동으로 알림 후 연결화면으로 전환됩니다.",
                    color = AppColors.Gray500,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(scaledDp(24, scale)))

                PttActionsBlock(
                    expandedAction = expandedAction,
                    batteryLevel = batteryLevel,
                    survivorCount = displayConnectedCount,
                    isPowerSaving = isPowerSaving,
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
                    onUsersClick = {
                        expandedAction = null
                        showMeshMap = true
                    },
                    modifier = Modifier.offset(y = scaledDp(12, scale))
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = scaledDp(86, scale)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(scaledDp(28, scale))
                        .height(scaledDp(28, scale)),
                    color = AppColors.Green,
                    strokeWidth = scaledDp(4, scale)
                )
                Spacer(modifier = Modifier.width(scaledDp(10, scale)))
                Text(
                    text = "구조자 연결 요청 대기중",
                    color = AppColors.Gray500,
                    fontSize = scaledSp(11, scale),
                    fontWeight = FontWeight.Medium
                )
            }

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
