package com.example.lifesaiver.protocol.codec

import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryPacketCodecTest {

    @Test
    fun encodeDecode_v1_basic() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = false)
        val payload = "hello".toByteArray()
        val senderId = bytesOf(1, 2, 3, 4, 5, 6, 7, 8)

        val header = PacketHeader(
            version = 1,
            type = PacketType.MESSAGE,
            ttl = 7,
            flags = 0,
            length = payload.size,
            timestamp = 123456789L,
            senderId = senderId
        )
        val packet = Packet(header = header, payload = payload)

        val encoded = codec.encode(packet)
        val decoded = codec.decode(encoded)

        assertNotNull(decoded)
        decoded!!

        assertEquals(1, decoded.header.version)
        assertEquals(PacketType.MESSAGE, decoded.header.type)
        assertEquals(7, decoded.header.ttl)
        assertEquals(123456789L, decoded.header.timestamp)
        assertArrayEquals(senderId, decoded.header.senderId)
        assertNull(decoded.header.recipientId)
        assertNull(decoded.header.signature)
        assertNull(decoded.header.route)
        assertEquals(payload.size, decoded.header.length)
        assertEquals(0, decoded.header.flags)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun encodeDecode_v2_withRecipientRouteSignature() {
        val codec = BinaryPacketCodec(enableCompression = false, enablePadding = false)
        val payload = "routing-test".toByteArray()
        val senderId = bytesOf(10, 11, 12, 13, 14, 15, 16, 17)
        val recipientId = bytesOf(21, 22, 23, 24, 25, 26, 27, 28)
        val signature = ByteArray(64) { 0x7A.toByte() }
        val route = listOf(
            bytesOf(31, 32, 33, 34, 35, 36, 37, 38),
            bytesOf(41, 42, 43, 44, 45, 46, 47, 48)
        )

        val header = PacketHeader(
            version = 2,
            type = PacketType.REQUEST_SYNC,
            ttl = 1,
            flags = 0,
            length = payload.size,
            timestamp = 987654321L,
            senderId = senderId,
            recipientId = recipientId,
            signature = signature,
            route = route
        )
        val packet = Packet(header = header, payload = payload)

        val encoded = codec.encode(packet)
        val decoded = codec.decode(encoded)

        assertNotNull(decoded)
        decoded!!

        assertEquals(2, decoded.header.version)
        assertEquals(PacketType.REQUEST_SYNC, decoded.header.type)
        assertEquals(1, decoded.header.ttl)
        assertEquals(987654321L, decoded.header.timestamp)
        assertArrayEquals(senderId, decoded.header.senderId)
        assertArrayEquals(recipientId, decoded.header.recipientId)
        assertArrayEquals(signature, decoded.header.signature)
        assertEquals(2, decoded.header.route?.size)
        assertArrayEquals(route[0], decoded.header.route?.get(0))
        assertArrayEquals(route[1], decoded.header.route?.get(1))
        assertTrue(decoded.header.flags and PacketFlags.HAS_RECIPIENT != 0)
        assertTrue(decoded.header.flags and PacketFlags.HAS_SIGNATURE != 0)
        assertTrue(decoded.header.flags and PacketFlags.HAS_ROUTE != 0)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun encodeDecode_compressedPayload_roundTrip() {
        val payload = ByteArray(400) { 'A'.code.toByte() }
        val compressed = CompressionUtil.compress(payload)
        assertNotNull(compressed)

        val codec = BinaryPacketCodec(enableCompression = true, enablePadding = false)
        val header = PacketHeader(
            version = 2,
            type = PacketType.MESSAGE,
            ttl = 7,
            flags = 0,
            length = payload.size,
            timestamp = 42L,
            senderId = bytesOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
        val packet = Packet(header = header, payload = payload)

        val encoded = codec.encode(packet)
        val decoded = codec.decode(encoded)

        assertNotNull(decoded)
        decoded!!

        assertTrue(decoded.header.flags and PacketFlags.IS_COMPRESSED != 0)
        assertArrayEquals(payload, decoded.payload)
    }

    private fun bytesOf(vararg values: Int): ByteArray {
        return ByteArray(values.size) { index -> values[index].toByte() }
    }
}
