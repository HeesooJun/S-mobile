package com.example.lifesaivior.protocol.mesh

class DirectPeerAnnounceTracker(
    private val connectionAnnounceCooldownMs: Long = 3_000L
) {
    data class Update(
        val shouldAnnounce: Boolean,
        val newPeers: List<String>
    )

    private var lastConnectionAnnounceMs: Long = 0L
    private val announcedToPeers = LinkedHashSet<String>()

    @Synchronized
    fun onConnectionUpdate(
        connected: Boolean,
        directPeerIds: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): Update {
        val newPeers = directPeerIds.filterNot { announcedToPeers.contains(it) }
        if (newPeers.isNotEmpty()) {
            announcedToPeers.addAll(newPeers)
        }
        announcedToPeers.retainAll(directPeerIds.toSet())

        val shouldAnnounce =
            connected && (nowMs - lastConnectionAnnounceMs >= connectionAnnounceCooldownMs)
        if (shouldAnnounce) {
            lastConnectionAnnounceMs = nowMs
        }

        return Update(shouldAnnounce = shouldAnnounce, newPeers = newPeers)
    }

    @Synchronized
    fun removePeer(peerId: String) {
        announcedToPeers.remove(peerId)
    }

    @Synchronized
    fun clear() {
        announcedToPeers.clear()
        lastConnectionAnnounceMs = 0L
    }
}
