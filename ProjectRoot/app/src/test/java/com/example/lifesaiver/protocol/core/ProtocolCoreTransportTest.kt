package com.example.lifesaiver.protocol.core

import com.example.lifesaiver.protocol.codec.BinaryPacketCodec
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.transport.RecordingTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolCoreTransportTest {

    @Test
    fun sendAndBroadcastUseTransport() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = false)
        val core = ProtocolCore(codec, codec)
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
        val core = ProtocolCore(codec, codec)
        val transport = RecordingTransport()
        core.attachTransport(transport)

        var received: Packet? = null
        core.setOnPacketReceived { packet -> received = packet }

        val packet = createPacket()
        val encoded = codec.encode(packet)
        transport.emit(encoded)

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
}
