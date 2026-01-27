package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.util.toHexString

class PacketDeduplicator(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val messageTimeoutMs: Long = ProtocolConstants.Dedup.MESSAGE_TIMEOUT_MS,
    private val maxProcessedMessages: Int = ProtocolConstants.Dedup.MAX_PROCESSED_MESSAGES
) {
    private val processedMessages = LinkedHashSet<String>()
    private val messageTimestamps = mutableMapOf<String, Long>()

    fun shouldProcess(packet: Packet): Boolean {
        val messageId = generateMessageId(packet)
        val now = clock()

        if (processedMessages.contains(messageId)) {
            val isFreshAnnounce = packet.header.type == PacketType.ANNOUNCE &&
                packet.header.ttl >= ProtocolConstants.MESSAGE_TTL_HOPS
            if (!isFreshAnnounce) {
                return false
            }
        }

        processedMessages.add(messageId)
        messageTimestamps[messageId] = now

        cleanup(now)
        return true
    }

    fun clear() {
        processedMessages.clear()
        messageTimestamps.clear()
    }

    private fun cleanup(now: Long) {
        val cutoff = now - messageTimeoutMs
        val expired = messageTimestamps.filter { it.value < cutoff }.keys
        for (messageId in expired) {
            messageTimestamps.remove(messageId)
            processedMessages.remove(messageId)
        }

        if (processedMessages.size <= maxProcessedMessages) return

        val excess = processedMessages.size - maxProcessedMessages
        val iterator = processedMessages.iterator()
        repeat(excess) {
            if (!iterator.hasNext()) return
            val messageId = iterator.next()
            iterator.remove()
            messageTimestamps.remove(messageId)
        }
    }

    private fun generateMessageId(packet: Packet): String {
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
