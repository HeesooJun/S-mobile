package com.example.lifesaivior.protocol.mesh

import com.example.lifesaivior.protocol.core.ProtocolConstants
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MeshGraphRegistry {
    data class GraphNode(val peerId: String, val nickname: String?)
    data class GraphEdge(val a: String, val b: String, val isConfirmed: Boolean, val confirmedBy: String? = null)
    data class GraphSnapshot(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

    private val nicknames = ConcurrentHashMap<String, String?>()
    private val announcements = ConcurrentHashMap<String, Set<String>>()
    private val lastUpdate = ConcurrentHashMap<String, Long>()
    private val suppressedPeers = ConcurrentHashMap<String, Long>()
    private val suppressionTtlMs: Long = ProtocolConstants.Mesh.PEER_TIMEOUT_MS
    private val _graphState = MutableStateFlow(GraphSnapshot(emptyList(), emptyList()))
    val graphState: StateFlow<GraphSnapshot> = _graphState.asStateFlow()

    @Synchronized
    fun updateFromAnnouncement(
        originPeerId: String,
        originNickname: String?,
        neighborsOrNull: List<String>?,
        timestamp: Long
    ) {
        val previous = lastUpdate[originPeerId]
        if (previous != null && previous >= timestamp) return
        lastUpdate[originPeerId] = timestamp
        if (originNickname != null) {
            nicknames[originPeerId] = originNickname
        }

        val now = System.currentTimeMillis()
        if (isSuppressed(originPeerId, now)) {
            suppressedPeers.remove(originPeerId)
        }
        val neighbors = neighborsOrNull ?: emptyList()
        val newSet = neighbors.distinct()
            .take(10)
            .filter { it != originPeerId && !isSuppressed(it, now) }
            .toSet()
        announcements[originPeerId] = newSet
        publishSnapshot()
    }

    @Synchronized
    fun touchPeer(peerId: String, nickname: String?, timestamp: Long) {
        val previous = lastUpdate[peerId]
        if (previous != null && previous >= timestamp) return
        lastUpdate[peerId] = timestamp
        if (!nickname.isNullOrBlank()) {
            nicknames[peerId] = nickname
        }
        if (isSuppressed(peerId, System.currentTimeMillis())) {
            suppressedPeers.remove(peerId)
        }
        announcements.putIfAbsent(peerId, emptySet())
        publishSnapshot()
    }

    @Synchronized
    fun removePeer(peerId: String) {
        suppressPeer(peerId, System.currentTimeMillis())
        nicknames.remove(peerId)
        announcements.remove(peerId)
        lastUpdate.remove(peerId)
        removePeerFromNeighbors(peerId)
        publishSnapshot()
    }

    @Synchronized
    fun prune(timeoutMs: Long, now: Long = System.currentTimeMillis()) {
        val cutoff = now - timeoutMs
        val stale = lastUpdate.filterValues { it < cutoff }.keys
        if (stale.isEmpty()) return
        for (peerId in stale) {
            removePeer(peerId)
        }
        cleanupSuppressed(now)
    }

    @Synchronized
    fun countNodes(): Int {
        return buildSnapshot().nodes.size
    }

    @Synchronized
    fun snapshot(): GraphSnapshot {
        return buildSnapshot()
    }

    @Synchronized
    fun clear() {
        nicknames.clear()
        announcements.clear()
        lastUpdate.clear()
        suppressedPeers.clear()
        publishSnapshot()
    }

    private fun buildSnapshot(): GraphSnapshot {
        val allNodes = mutableSetOf<String>()
        allNodes.addAll(nicknames.keys)
        announcements.forEach { (origin, neighbors) ->
            allNodes.add(origin)
            allNodes.addAll(neighbors)
        }

        val nodes = allNodes.map { GraphNode(it, nicknames[it]) }.sortedBy { it.peerId }
        val edges = mutableListOf<GraphEdge>()
        val processedPairs = mutableSetOf<Pair<String, String>>()

        announcements.forEach { (source, targets) ->
            targets.forEach { target ->
                val pair = if (source <= target) source to target else target to source
                if (processedPairs.add(pair)) {
                    val (a, b) = pair
                    val aAnnouncesB = announcements[a]?.contains(b) == true
                    val bAnnouncesA = announcements[b]?.contains(a) == true
                    when {
                        aAnnouncesB && bAnnouncesA -> edges.add(GraphEdge(a, b, isConfirmed = true))
                        aAnnouncesB -> edges.add(GraphEdge(a, b, isConfirmed = false, confirmedBy = a))
                        bAnnouncesA -> edges.add(GraphEdge(a, b, isConfirmed = false, confirmedBy = b))
                    }
                }
            }
        }

        val sortedEdges = edges.sortedWith(compareBy({ it.a }, { it.b }))
        return GraphSnapshot(nodes, sortedEdges)
    }

    private fun removePeerFromNeighbors(peerId: String) {
        announcements.forEach { (origin, neighbors) ->
            if (!neighbors.contains(peerId)) return@forEach
            val updated = neighbors - peerId
            if (updated.isEmpty()) {
                announcements.remove(origin)
            } else {
                announcements[origin] = updated
            }
        }
    }

    @Synchronized
    private fun publishSnapshot() {
        _graphState.value = buildSnapshot()
    }

    private fun suppressPeer(peerId: String, now: Long) {
        suppressedPeers[peerId] = now + suppressionTtlMs
    }

    private fun isSuppressed(peerId: String, now: Long): Boolean {
        val expiresAt = suppressedPeers[peerId] ?: return false
        if (expiresAt <= now) {
            suppressedPeers.remove(peerId)
            return false
        }
        return true
    }

    private fun cleanupSuppressed(now: Long) {
        val expired = suppressedPeers.filterValues { it <= now }.keys
        if (expired.isEmpty()) return
        expired.forEach { suppressedPeers.remove(it) }
    }
}
