package com.example.lifesaiver.ui.screen.survivor.ptt

import com.example.lifesaiver.protocol.mesh.MeshGraphRegistry
import com.example.lifesaiver.ui.components.MeshEdge
import com.example.lifesaiver.ui.components.MeshNode

internal data class PttMeshGraphState(
    val nodes: List<MeshNode>,
    val edges: List<MeshEdge>
)

internal fun buildPttMeshGraphState(
    snapshot: MeshGraphRegistry.GraphSnapshot,
    myPeerId: String,
    myNickname: String,
    peerNicknames: Map<String, String>
): PttMeshGraphState {
    if (snapshot.nodes.isEmpty()) {
        val selfId = myPeerId.trim().ifBlank { "self" }
        val selfLabel = myNickname.trim().ifBlank { selfId }
        return PttMeshGraphState(
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

    return PttMeshGraphState(nodes = nodes, edges = edges)
}
