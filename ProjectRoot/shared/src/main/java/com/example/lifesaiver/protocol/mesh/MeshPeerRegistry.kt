package com.example.lifesaiver.protocol.mesh

import java.util.concurrent.ConcurrentHashMap

data class MeshPeer(
    val id: String,
    val isWifiAware: Boolean,
    val isWifiDirect: Boolean,
    val isUwb: Boolean,
    val lastSeen: Long
)

class MeshPeerRegistry(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val peers = ConcurrentHashMap<String, MeshPeer>()

    fun getPeer(id: String): MeshPeer? = peers[id]

    fun updatePeer(peerIdHex: String, isWifiAware: Boolean, isWifiDirect: Boolean, isUwb: Boolean) {
        peers[peerIdHex] = MeshPeer(
            id = peerIdHex,
            isWifiAware = isWifiAware,
            isWifiDirect = isWifiDirect,
            isUwb = isUwb,
            lastSeen = clock()
        )
    }

    fun markSeenOnly(peerIdHex: String) {
        peers.computeIfPresent(peerIdHex) { _, currentPeer ->
            currentPeer.copy(lastSeen = clock())
        }
    }

    fun prune(timeoutMs: Long) {
        val cutoff = clock() - timeoutMs
        peers.entries.removeIf { it.value.lastSeen < cutoff }
    }

    fun remove(peerIdHex: String) {
        peers.remove(peerIdHex)
    }

    fun count(): Int = peers.size
}
