package com.example.lifesaiver.protocol.storeforward

import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.util.toHexString

class StoreForwardManager(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cacheTimeoutMs: Long = ProtocolConstants.StoreForward.MESSAGE_CACHE_TIMEOUT_MS,
    private val maxCachedMessages: Int = ProtocolConstants.StoreForward.MAX_CACHED_MESSAGES,
    private val cleanupIntervalMs: Long = ProtocolConstants.StoreForward.CLEANUP_INTERVAL_MS
) {
    private data class CachedPacket(
        val packet: Packet,
        val recipientId: String,
        val messageId: String,
        val timestamp: Long
    )

    private val lock = Any()
    private val cache = mutableListOf<CachedPacket>()
    private val cachedIds = mutableSetOf<String>()
    private var lastCleanupAt: Long = 0

    fun cache(packet: Packet) {
        val recipientId = packet.header.recipientId ?: return
        if (shouldSkip(packet.header.type)) return
        val messageId = buildMessageId(packet)
        val now = clock()
        synchronized(lock) {
            maybeCleanup(now)
            if (cachedIds.contains(messageId)) return
            cache.add(CachedPacket(packet, recipientId.toHexString(), messageId, now))
            cachedIds.add(messageId)
            trimIfNeeded()
        }
    }

    suspend fun drainForPeer(
        peerId: String,
        sender: suspend (Packet) -> Boolean
    ) {
        val pending = synchronized(lock) {
            maybeCleanup(clock())
            cache.filter { it.recipientId == peerId }
                .sortedBy { it.timestamp }
        }
        for (entry in pending) {
            val delivered = sender(entry.packet)
            if (delivered) {
                synchronized(lock) {
                    cache.remove(entry)
                    cachedIds.remove(entry.messageId)
                }
            }
        }
    }

    private fun shouldSkip(type: PacketType): Boolean {
        return when (type) {
            PacketType.ANNOUNCE,
            PacketType.LEAVE,
            PacketType.FILE_ACK,
            PacketType.FRAGMENT -> true
            else -> false
        }
    }

    private fun maybeCleanup(now: Long) {
        if (now - lastCleanupAt < cleanupIntervalMs) return
        lastCleanupAt = now
        val cutoff = now - cacheTimeoutMs
        val expired = cache.filter { it.timestamp < cutoff }
        if (expired.isEmpty()) return
        cache.removeAll(expired.toSet())
        expired.forEach { cachedIds.remove(it.messageId) }
    }

    private fun trimIfNeeded() {
        if (cache.size <= maxCachedMessages) return
        val excess = cache.size - maxCachedMessages
        val removed = cache.sortedBy { it.timestamp }.take(excess)
        cache.removeAll(removed.toSet())
        removed.forEach { cachedIds.remove(it.messageId) }
    }

    private fun buildMessageId(packet: Packet): String {
        val senderHex = packet.header.senderId.toHexString()
        val payloadHash = if (packet.header.type == PacketType.FRAGMENT) {
            packet.payload.contentHashCode()
        } else {
            val limit = minOf(64, packet.payload.size)
            packet.payload.copyOfRange(0, limit).contentHashCode()
        }
        return "${packet.header.timestamp}-$senderHex-${packet.header.type.code}-$payloadHash"
    }
}
