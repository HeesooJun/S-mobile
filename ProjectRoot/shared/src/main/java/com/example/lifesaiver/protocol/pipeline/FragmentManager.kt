package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.codec.MessagePadding
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.FragmentPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType

class FragmentManager(
    private val encoder: PacketEncoder,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fragmentSizeThreshold: Int = ProtocolConstants.Fragmentation.FRAGMENT_SIZE_THRESHOLD,
    private val maxFragmentSize: Int = ProtocolConstants.Fragmentation.MAX_FRAGMENT_SIZE,
    private val fragmentTimeoutMs: Long = ProtocolConstants.Fragmentation.FRAGMENT_TIMEOUT_MS,
    private val cleanupIntervalMs: Long = ProtocolConstants.Fragmentation.CLEANUP_INTERVAL_MS
) {
    private data class FragmentMeta(
        val originalType: Int,
        val total: Int,
        val createdAt: Long
    )

    private val incomingFragments = mutableMapOf<String, MutableMap<Int, ByteArray>>()
    private val fragmentMetadata = mutableMapOf<String, FragmentMeta>()
    private var lastCleanupAt: Long = 0

    fun createFragments(packet: Packet): List<Packet> {
        if (packet.header.type == PacketType.FRAGMENT) return listOf(packet)

        val encoded = encoder.encode(packet)
        val fullData = MessagePadding.unpad(encoded)
        if (fullData.size <= fragmentSizeThreshold) {
            return listOf(packet)
        }

        val maxDataSize = calculateMaxDataSize(packet)
        if (maxDataSize <= 0) {
            return emptyList()
        }

        val fragmentId = FragmentPayload.generateFragmentId()
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < fullData.size) {
            val end = minOf(offset + maxDataSize, fullData.size)
            chunks.add(fullData.copyOfRange(offset, end))
            offset = end
        }

        return chunks.mapIndexed { index, data ->
            val payload = FragmentPayload(
                fragmentId = fragmentId,
                index = index,
                total = chunks.size,
                originalType = packet.header.type.code,
                data = data
            ).encode()

            Packet(
                header = packet.header.toFragmentHeader(payload.size),
                payload = payload
            )
        }
    }

    fun handleFragment(packet: Packet): ByteArray? {
        if (packet.payload.size < FragmentPayload.HEADER_SIZE) return null

        val fragmentPayload = FragmentPayload.decode(packet.payload) ?: return null
        if (!fragmentPayload.isValid()) return null

        val now = clock()
        maybeCleanup(now)

        val fragmentId = fragmentPayload.getFragmentIdString()
        val fragments = incomingFragments.getOrPut(fragmentId) { mutableMapOf() }
        fragmentMetadata.putIfAbsent(
            fragmentId,
            FragmentMeta(fragmentPayload.originalType, fragmentPayload.total, now)
        )

        fragments[fragmentPayload.index] = fragmentPayload.data
        if (fragments.size != fragmentPayload.total) return null

        val reassembled = ByteArray(fragments.values.sumOf { it.size })
        var cursor = 0
        for (index in 0 until fragmentPayload.total) {
            val chunk = fragments[index] ?: return null
            System.arraycopy(chunk, 0, reassembled, cursor, chunk.size)
            cursor += chunk.size
        }

        incomingFragments.remove(fragmentId)
        fragmentMetadata.remove(fragmentId)
        return reassembled
    }

    fun clear() {
        incomingFragments.clear()
        fragmentMetadata.clear()
    }

    private fun maybeCleanup(now: Long) {
        if (now - lastCleanupAt < cleanupIntervalMs) return
        lastCleanupAt = now
        val cutoff = now - fragmentTimeoutMs
        val expired = fragmentMetadata.filter { it.value.createdAt < cutoff }.keys
        for (fragmentId in expired) {
            incomingFragments.remove(fragmentId)
            fragmentMetadata.remove(fragmentId)
        }
    }

    private fun calculateMaxDataSize(packet: Packet): Int {
        val hasRoute = !packet.header.route.isNullOrEmpty()
        val version = if (hasRoute && packet.header.version < 2) 2 else packet.header.version
        val headerSize = if (version >= 2) HEADER_SIZE_V2 else HEADER_SIZE_V1
        val senderSize = SENDER_ID_SIZE
        val recipientSize = if (packet.header.recipientId != null) RECIPIENT_ID_SIZE else 0
        val routeSize = if (hasRoute) 1 + (packet.header.route!!.size * SENDER_ID_SIZE) else 0
        val paddingBuffer = 16

        val packetOverhead = headerSize + senderSize + recipientSize + routeSize +
            FragmentPayload.HEADER_SIZE + paddingBuffer

        return (fragmentSizeThreshold - packetOverhead).coerceAtMost(maxFragmentSize)
    }

    private fun PacketHeader.toFragmentHeader(payloadSize: Int): PacketHeader {
        val hasRoute = !route.isNullOrEmpty()
        val version = if (hasRoute && this.version < 2) 2 else this.version
        return copy(
            version = version,
            type = PacketType.FRAGMENT,
            flags = 0,
            length = payloadSize,
            signature = null
        )
    }

    private companion object {
        const val HEADER_SIZE_V1 = 14
        const val HEADER_SIZE_V2 = 16
        const val SENDER_ID_SIZE = 8
        const val RECIPIENT_ID_SIZE = 8
    }
}
