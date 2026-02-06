package com.example.lifesaivior.protocol.core

import com.example.lifesaivior.protocol.codec.BinaryPacketCodec
import com.example.lifesaivior.protocol.model.Packet
import com.example.lifesaivior.protocol.model.PacketHeader
import com.example.lifesaivior.protocol.model.PacketType
import com.example.lifesaivior.protocol.transport.RecordingTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProtocolCoreTransportTest {

    @Test
    fun sendAndBroadcastUseTransport() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = false)
        val core = ProtocolCore(codec, codec, myPeerId = TEST_PEER_ID)
        val transport = RecordingTransport()
        core.attachTransport(transport)

        val packet = createPacket()
        core.send(packet)
        core.broadcast(packet)

        assertEquals(1, transport.sent.size)
        assertEquals(1, transport.broadcasts.size)
    }

    @Test
    fun inboundBytesTriggerPacketHandler() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = false)
        val core = ProtocolCore(codec, codec, myPeerId = TEST_PEER_ID)
        val transport = RecordingTransport()
        core.attachTransport(transport)

        var received: Packet? = null
        val latch = CountDownLatch(1)
        core.setOnPacketReceived { packet, _ ->
            received = packet
            latch.countDown()
        }

        val packet = createPacket()
        val encoded = codec.encode(packet)
        transport.emit(encoded)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertNotNull(received)
    }

    private fun createPacket(): Packet {
        val payload = "ping".toByteArray()
        val header = PacketHeader(
            version = 2,
            type = PacketType.MESSAGE,
            ttl = ProtocolConstants.MESSAGE_TTL_HOPS,
            flags = 0,
            length = payload.size,
            timestamp = 123L,
            senderId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )
        return Packet(header = header, payload = payload)
    }

    companion object {
        private val TEST_PEER_ID = byteArrayOf(9, 9, 9, 9, 9, 9, 9, 9)
    }
}
