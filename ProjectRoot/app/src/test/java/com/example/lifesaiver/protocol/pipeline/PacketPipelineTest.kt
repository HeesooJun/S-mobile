package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketPipelineTest {

    @Test
    fun reassembleFragments_returnsOriginalPacketWithSuppressedTtl() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = true)
        val pipeline = PacketPipeline(codec, codec)

        val payload = ByteArray(2000) { index -> (index % 256).toByte() }
        val original = createPacket(PacketType.MESSAGE, payload)

        val fragments = pipeline.prepareOutbound(original)
        assertTrue(fragments.size > 1)

        var delivered: Packet? = null
        fragments.forEach { fragment ->
            val encoded = codec.encode(fragment)
            val decoded = codec.decode(encoded)
            val result = decoded?.let { pipeline.handleInbound(it) }
            if (result != null) {
                delivered = result
            }
        }

        assertNotNull(delivered)
        delivered!!
        assertArrayEquals(payload, delivered.payload)
        assertTrue(delivered.header.ttl == ProtocolConstants.SYNC_TTL_HOPS)
    }

    private fun createPacket(type: PacketType, payload: ByteArray): Packet {
        val header = PacketHeader(
            version = 2,
            type = type,
            ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
            flags = 0,
            length = payload.size,
            timestamp = 1L,
            senderId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )
        return Packet(header = header, payload = payload)
    }
}
