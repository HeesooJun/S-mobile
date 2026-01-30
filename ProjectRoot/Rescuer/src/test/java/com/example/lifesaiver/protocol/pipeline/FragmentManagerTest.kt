package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentManagerTest {

    @Test
    fun fragmentsFitWithinThreshold() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = true)
        val fragmentManager = FragmentManager(codec)

        val payload = ByteArray(2000) { 0x5A.toByte() }
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.MESSAGE,
                ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = 5L,
                senderId = byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9)
            ),
            payload = payload
        )

        val fragments = fragmentManager.createFragments(packet)
        assertTrue(fragments.size > 1)

        fragments.forEach { fragment ->
            val encoded = codec.encode(fragment)
            assertTrue(encoded.size <= ProtocolConstants.Fragmentation.FRAGMENT_SIZE_THRESHOLD)
        }
    }
}
