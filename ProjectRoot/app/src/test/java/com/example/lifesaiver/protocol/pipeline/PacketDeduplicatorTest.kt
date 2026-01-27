package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketDeduplicatorTest {

    @Test
    fun duplicatePacketsAreRejected() {
        val deduplicator = PacketDeduplicator(clock = { 1_000L })
        val packet = createPacket(PacketType.MESSAGE, ttl = 3)

        assertTrue(deduplicator.shouldProcess(packet))
        assertFalse(deduplicator.shouldProcess(packet))
    }

    @Test
    fun announceDuplicatesWithMaxTtlAreAllowed() {
        val deduplicator = PacketDeduplicator(clock = { 2_000L })
        val announce = createPacket(PacketType.ANNOUNCE, ttl = ProtocolConstants.MESSAGE_TTL_HOPS)

        assertTrue(deduplicator.shouldProcess(announce))
        assertTrue(deduplicator.shouldProcess(announce))
    }

    private fun createPacket(type: PacketType, ttl: Int): Packet {
        val payload = byteArrayOf(1, 2, 3)
        val header = PacketHeader(
            version = 2,
            type = type,
            ttl = ttl,
            flags = 0,
            length = payload.size,
            timestamp = 10L,
            senderId = byteArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
        return Packet(header = header, payload = payload)
    }
}
