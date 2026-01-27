package com.example.lifesaiver.protocol.codec

import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BinaryPacketCodec(
    private val enableCompression: Boolean = true,
    private val enablePadding: Boolean = true
) : PacketEncoder, PacketDecoder {

    override fun encode(packet: Packet): ByteArray {
        val header = packet.header
        val version = header.version
        val typeCode = header.type.code
        val ttl = header.ttl

        var payload = packet.payload
        var isCompressed = false
        var originalPayloadSize: Int? = null
        if (enableCompression && CompressionUtil.shouldCompress(payload)) {
            CompressionUtil.compress(payload)?.let { compressed ->
                originalPayloadSize = payload.size
                payload = compressed
                isCompressed = true
            }
        }

        val hasRecipient = header.recipientId != null
        val hasSignature = header.signature != null
        val hasRoute = !header.route.isNullOrEmpty() && version >= 2

        var flags = 0
        if (hasRecipient) flags = flags or PacketFlags.HAS_RECIPIENT
        if (hasSignature) flags = flags or PacketFlags.HAS_SIGNATURE
        if (isCompressed) flags = flags or PacketFlags.IS_COMPRESSED
        if (hasRoute) flags = flags or PacketFlags.HAS_ROUTE

        val lengthFieldBytes = if (version >= 2) 4 else 2
        val payloadLength = payload.size + if (isCompressed) lengthFieldBytes else 0

        val headerSize = if (version >= 2) HEADER_SIZE_V2 else HEADER_SIZE_V1
        val recipientBytes = if (hasRecipient) RECIPIENT_ID_SIZE else 0
        val signatureBytes = if (hasSignature) SIGNATURE_SIZE else 0
        val routeBytes = if (hasRoute) 1 + (header.route!!.size.coerceAtMost(255) * SENDER_ID_SIZE) else 0
        val capacity = headerSize + SENDER_ID_SIZE + recipientBytes + routeBytes + payloadLength + signatureBytes

        val buffer = ByteBuffer.allocate(capacity).apply { order(ByteOrder.BIG_ENDIAN) }

        buffer.put(version.toByte())
        buffer.put(typeCode.toByte())
        buffer.put(ttl.toByte())
        buffer.putLong(header.timestamp)
        buffer.put(flags.toByte())
        if (version >= 2) {
            buffer.putInt(payloadLength)
        } else {
            buffer.putShort(payloadLength.toShort())
        }

        buffer.put(fixedSize(header.senderId, SENDER_ID_SIZE))

        if (hasRecipient) {
            buffer.put(fixedSize(header.recipientId!!, RECIPIENT_ID_SIZE))
        }

        if (hasRoute) {
            val cleaned = header.route!!.map { hop -> fixedSize(hop, SENDER_ID_SIZE) }
            val count = cleaned.size.coerceAtMost(255)
            buffer.put(count.toByte())
            cleaned.take(count).forEach { hop -> buffer.put(hop) }
        }

        if (isCompressed) {
            val originalSize = originalPayloadSize ?: payload.size
            if (version >= 2) {
                buffer.putInt(originalSize)
            } else {
                buffer.putShort(originalSize.toShort())
            }
        }
        buffer.put(payload)

        if (hasSignature) {
            buffer.put(fixedSize(header.signature!!, SIGNATURE_SIZE))
        }

        val raw = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(raw)

        if (!enablePadding) return raw

        val optimalSize = MessagePadding.optimalBlockSize(raw.size)
        return MessagePadding.pad(raw, optimalSize)
    }

    override fun decode(bytes: ByteArray): Packet? {
        if (!enablePadding) {
            return decodeCore(bytes)
        }

        decodeCore(bytes)?.let { return it }
        val unpadded = MessagePadding.unpad(bytes)
        if (unpadded.contentEquals(bytes)) return null
        return decodeCore(unpadded)
    }

    private fun decodeCore(raw: ByteArray): Packet? {
        if (raw.size < HEADER_SIZE_V1 + SENDER_ID_SIZE) return null

        val buffer = ByteBuffer.wrap(raw).apply { order(ByteOrder.BIG_ENDIAN) }

        val version = buffer.get().toInt() and 0xFF
        if (version != 1 && version != 2) return null

        val typeCode = buffer.get().toInt() and 0xFF
        val type = PacketType.fromCode(typeCode) ?: return null

        val ttl = buffer.get().toInt() and 0xFF
        val timestamp = buffer.getLong()

        val flags = buffer.get().toInt() and 0xFF
        val hasRecipient = (flags and PacketFlags.HAS_RECIPIENT) != 0
        val hasSignature = (flags and PacketFlags.HAS_SIGNATURE) != 0
        val isCompressed = (flags and PacketFlags.IS_COMPRESSED) != 0
        val hasRoute = (flags and PacketFlags.HAS_ROUTE) != 0 && version >= 2

        val payloadLength = if (version >= 2) {
            buffer.getInt()
        } else {
            buffer.getShort().toInt() and 0xFFFF
        }

        val headerSize = if (version >= 2) HEADER_SIZE_V2 else HEADER_SIZE_V1
        var expectedSize = headerSize + SENDER_ID_SIZE + payloadLength
        if (hasRecipient) expectedSize += RECIPIENT_ID_SIZE
        if (hasSignature) expectedSize += SIGNATURE_SIZE

        var routeCount = 0
        if (hasRoute) {
            val routeOffset = headerSize + SENDER_ID_SIZE + if (hasRecipient) RECIPIENT_ID_SIZE else 0
            if (raw.size < routeOffset + 1) return null
            routeCount = raw[routeOffset].toInt() and 0xFF
            expectedSize += 1 + (routeCount * SENDER_ID_SIZE)
        }

        if (raw.size < expectedSize) return null

        val senderId = ByteArray(SENDER_ID_SIZE)
        buffer.get(senderId)

        val recipientId = if (hasRecipient) {
            val recipientBytes = ByteArray(RECIPIENT_ID_SIZE)
            buffer.get(recipientBytes)
            recipientBytes
        } else null

        val route = if (hasRoute) {
            val count = buffer.get().toInt() and 0xFF
            if (count == 0) {
                null
            } else {
                val hops = mutableListOf<ByteArray>()
                repeat(count) {
                    val hop = ByteArray(SENDER_ID_SIZE)
                    buffer.get(hop)
                    hops.add(hop)
                }
                hops
            }
        } else null

        val payload = if (isCompressed) {
            val sizeFieldBytes = if (version >= 2) 4 else 2
            if (payloadLength < sizeFieldBytes) return null

            val originalSize = if (version >= 2) {
                buffer.getInt()
            } else {
                buffer.getShort().toInt() and 0xFFFF
            }

            val compressedSize = payloadLength - sizeFieldBytes
            val compressedPayload = ByteArray(compressedSize)
            buffer.get(compressedPayload)

            if (!enableCompression) return null

            if (compressedSize > 0) {
                val ratio = originalSize.toDouble() / compressedSize.toDouble()
                if (ratio > 50_000.0) return null
            }

            CompressionUtil.decompress(compressedPayload, originalSize) ?: return null
        } else {
            val payloadBytes = ByteArray(payloadLength)
            buffer.get(payloadBytes)
            payloadBytes
        }

        val signature = if (hasSignature) {
            val signatureBytes = ByteArray(SIGNATURE_SIZE)
            buffer.get(signatureBytes)
            signatureBytes
        } else null

        val header = PacketHeader(
            version = version,
            type = type,
            ttl = ttl,
            flags = flags,
            length = payloadLength,
            timestamp = timestamp,
            senderId = senderId,
            recipientId = recipientId,
            signature = signature,
            route = route
        )

        return Packet(header = header, payload = payload)
    }

    private fun fixedSize(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size == size) return bytes
        return if (bytes.size > size) {
            bytes.copyOfRange(0, size)
        } else {
            bytes + ByteArray(size - bytes.size)
        }
    }

    private companion object {
        const val HEADER_SIZE_V1 = 14
        const val HEADER_SIZE_V2 = 16
        const val SENDER_ID_SIZE = 8
        const val RECIPIENT_ID_SIZE = 8
        const val SIGNATURE_SIZE = 64
    }
}
