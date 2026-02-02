package com.example.lifesaiver.ui.screen.survivor.ptt

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.presentation.BleDebugStats
import com.example.lifesaiver.ui.theme.LifesaiverTheme
import kotlinx.coroutines.flow.MutableSharedFlow

@Preview(name = "PTT - Connected", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PTTLinkScreenPreviewConnected() {
    LifesaiverTheme(darkTheme = true, dynamicColor = false) {
        PTTLinkScreen(
            batteryLevel = 87,
            connectedCount = 2,
            meshPeerCount = 4,
            directPeerIds = listOf("A1", "B2"),
            myPeerId = "ME",
            myNickname = "구조자1",
            peerNicknames = mapOf("A1" to "팀원A", "B2" to "팀원B"),
            meshGraphSnapshot = MeshGraphRegistry.GraphSnapshot(
                nodes = listOf(
                    MeshGraphRegistry.GraphNode("A1", "팀원A"),
                    MeshGraphRegistry.GraphNode("B2", "팀원B")
                ),
                edges = listOf(
                    MeshGraphRegistry.GraphEdge("ME", "A1", isConfirmed = true),
                    MeshGraphRegistry.GraphEdge("A1", "B2", isConfirmed = true)
                )
            ),
            meshVisualEvents = MutableSharedFlow(),
            bleDebugStats = BleDebugStats(
                scanRssiAvg = -63,
                scanRssiCount = 8,
                connectionRssiAvg = -58,
                connectionRssiCount = 5,
                pendingCount = 1,
                attemptTracked = 3,
                maxAttempts = 6
            ),
            isConnected = true,
            onBack = {},
            onDisconnect = {},
            onProfile = {},
            onPanicClear = {},
            onSettings = {}
        )
    }
}

@Preview(name = "PTT - Waiting", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PTTLinkScreenPreviewWaiting() {
    LifesaiverTheme(darkTheme = true, dynamicColor = false) {
        PTTLinkScreen(
            batteryLevel = 41,
            connectedCount = 0,
            meshPeerCount = 0,
            directPeerIds = emptyList(),
            myPeerId = "ME",
            myNickname = "구조자1",
            peerNicknames = emptyMap(),
            meshGraphSnapshot = MeshGraphRegistry.GraphSnapshot(
                nodes = emptyList(),
                edges = emptyList()
            ),
            meshVisualEvents = MutableSharedFlow(),
            bleDebugStats = BleDebugStats(),
            isConnected = false,
            onBack = {},
            onDisconnect = {},
            onProfile = {},
            onPanicClear = {},
            onSettings = {}
        )
    }
}
