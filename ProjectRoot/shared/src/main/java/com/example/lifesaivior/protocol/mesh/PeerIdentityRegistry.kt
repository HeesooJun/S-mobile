package com.example.lifesaivior.protocol.mesh

import com.example.lifesaivior.protocol.util.toHexString
import java.util.concurrent.ConcurrentHashMap

class PeerIdentityRegistry {
    data class AnnounceDecision(
        val accept: Boolean,
        val removedPeerIds: List<String> = emptyList()
    )

    private val noiseKeyByPeer = ConcurrentHashMap<String, ByteArray>()
    private val peerByNoiseKey = ConcurrentHashMap<String, String>()
    private val nicknameByPeer = ConcurrentHashMap<String, String>()
    private val lastSeenByPeer = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun handleAnnounce(
        peerId: String,
        nickname: String,
        noisePublicKey: ByteArray,
        now: Long,
        duplicateNicknameStaleMs: Long
    ): AnnounceDecision {
        val existingNoiseKey = noiseKeyByPeer[peerId]
        if (existingNoiseKey != null && !existingNoiseKey.contentEquals(noisePublicKey)) {
            return AnnounceDecision(accept = false)
        }

        val toRemove = LinkedHashSet<String>()
        val noiseHex = noisePublicKey.toHexString()
        val previousPeerId = peerByNoiseKey[noiseHex]
        if (previousPeerId != null && previousPeerId != peerId) {
            toRemove.add(previousPeerId)
        }

        if (nickname.isNotBlank()) {
            lastSeenByPeer.forEach { (existingPeerId, lastSeen) ->
                if (existingPeerId == peerId) return@forEach
                val existingNickname = nicknameByPeer[existingPeerId]
                if (existingNickname == nickname && now - lastSeen > duplicateNicknameStaleMs) {
                    toRemove.add(existingPeerId)
                }
            }
        }

        toRemove.forEach { removePeerInternal(it) }

        noiseKeyByPeer[peerId] = noisePublicKey
        peerByNoiseKey[noiseHex] = peerId
        nicknameByPeer[peerId] = nickname
        lastSeenByPeer[peerId] = now

        return AnnounceDecision(accept = true, removedPeerIds = toRemove.toList())
    }

    @Synchronized
    fun removePeer(peerId: String) {
        removePeerInternal(peerId)
    }

    @Synchronized
    fun clear() {
        noiseKeyByPeer.clear()
        peerByNoiseKey.clear()
        nicknameByPeer.clear()
        lastSeenByPeer.clear()
    }

    private fun removePeerInternal(peerId: String) {
        val noiseKey = noiseKeyByPeer.remove(peerId)
        if (noiseKey != null) {
            val noiseHex = noiseKey.toHexString()
            val mappedPeer = peerByNoiseKey[noiseHex]
            if (mappedPeer == peerId) {
                peerByNoiseKey.remove(noiseHex)
            }
        }
        nicknameByPeer.remove(peerId)
        lastSeenByPeer.remove(peerId)
    }
}
