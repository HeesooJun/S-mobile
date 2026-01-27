package com.example.rescuer.protocol.mesh

import java.util.concurrent.ConcurrentHashMap

class MeshPeerRegistry(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val peers = ConcurrentHashMap<String, Long>()

    fun markSeen(peerIdHex: String) {
        peers[peerIdHex] = clock()
    }

    fun prune(timeoutMs: Long) {
        val cutoff = clock() - timeoutMs
        peers.entries.removeIf { it.value < cutoff }
    }

    fun remove(peerIdHex: String) {
        peers.remove(peerIdHex)
    }

    fun count(): Int = peers.size
}
