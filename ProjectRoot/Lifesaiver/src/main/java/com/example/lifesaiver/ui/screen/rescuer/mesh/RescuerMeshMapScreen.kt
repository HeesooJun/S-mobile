package com.example.lifesaiver.ui.screen.rescuer.mesh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.presentation.MeshVisualEvent
import com.example.lifesaiver.ui.components.MeshEdge
import com.example.lifesaiver.ui.components.MeshMap
import com.example.lifesaiver.ui.components.MeshNode
import com.example.lifesaiver.ui.components.ScreenScaffold
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun RescuerMeshMapScreen(
    connectedCount: Int,
    meshPeerCount: Int,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>,
    meshGraphSnapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    meshVisualEvents: SharedFlow<MeshVisualEvent>,
    onBack: () -> Unit
) {
    val scale = LocalAppScale.current
    val meshDisplayCount = meshPeerCount.coerceAtLeast(0)
    val meshGraphState = remember(meshGraphSnapshot, myPeerId, myNickname, peerNicknames) {
        buildMeshGraphState(
            snapshot = meshGraphSnapshot,
            myPeerId = myPeerId,
            myNickname = myNickname,
            peerNicknames = peerNicknames
        )
    }

    ScreenScaffold(
        gradient = listOf(AppColors.Gray900, AppColors.Black),
        vignetteColor = AppColors.Black.copy(alpha = 0.7f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MeshMap(
                nodes = meshGraphState.nodes,
                edges = meshGraphState.edges,
                visualEvents = meshVisualEvents,
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = scaledDp(12, scale), top = scaledDp(12, scale))
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = AppColors.White
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = scaledDp(18, scale)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "메쉬 현황",
                    color = AppColors.White,
                    fontSize = scaledSp(16, scale),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "직접 ${connectedCount}명 · 메쉬 ${meshDisplayCount}명",
                    color = AppColors.Gray400,
                    fontSize = scaledSp(12, scale)
                )
            }
        }
    }
}

private data class MeshGraphUiState(
    val nodes: List<MeshNode>,
    val edges: List<MeshEdge>
)

private fun buildMeshGraphState(
    snapshot: com.example.lifesaiver.protocol.mesh.MeshGraphRegistry.GraphSnapshot,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>
): MeshGraphUiState {
    if (snapshot.nodes.isEmpty()) {
        val selfId = myPeerId.trim().ifBlank { "self" }
        val selfLabel = myNickname.trim().ifBlank { selfId }
        return MeshGraphUiState(
            nodes = listOf(
                MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel)
            ),
            edges = emptyList()
        )
    }

    val selfId = myPeerId
    val adjacency = mutableMapOf<String, MutableSet<String>>()
    snapshot.edges.forEach { edge ->
        adjacency.getOrPut(edge.a) { mutableSetOf() }.add(edge.b)
        adjacency.getOrPut(edge.b) { mutableSetOf() }.add(edge.a)
    }

    val hops = mutableMapOf<String, Int>()
    val queue = ArrayDeque<String>()
    hops[selfId] = 0
    queue.add(selfId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val nextHop = (hops[current] ?: 0) + 1
        adjacency[current].orEmpty().forEach { neighbor ->
            if (hops.containsKey(neighbor)) return@forEach
            hops[neighbor] = nextHop
            queue.add(neighbor)
        }
    }

    val nodes = mutableListOf<MeshNode>()
    val selfLabel = myNickname.trim().ifBlank { myPeerId.trim().ifBlank { "self" } }
    nodes.add(MeshNode(id = selfId, hop = 0, signal = 1f, isSelf = true, label = selfLabel))

    snapshot.nodes.forEach { node ->
        if (node.peerId == selfId) return@forEach
        val hop = (hops[node.peerId] ?: 2).coerceAtLeast(1)
        val signal = when (hop) {
            1 -> 0.8f
            2 -> 0.55f
            else -> 0.4f
        }
        val label = node.nickname?.takeIf { it.isNotBlank() }
            ?: peerNicknames[node.peerId]
            ?: node.peerId
        nodes.add(
            MeshNode(
                id = node.peerId,
                hop = hop,
                signal = signal,
                label = label
            )
        )
    }

    val edges = snapshot.edges.map { edge ->
        MeshEdge(
            a = edge.a,
            b = edge.b,
            isConfirmed = edge.isConfirmed,
            confirmedBy = edge.confirmedBy
        )
    }

    return MeshGraphUiState(nodes = nodes, edges = edges)
}
