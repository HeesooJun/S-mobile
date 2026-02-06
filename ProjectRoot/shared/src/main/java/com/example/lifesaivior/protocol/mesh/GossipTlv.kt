package com.example.lifesaivior.protocol.mesh

import java.util.Locale

object GossipTlv {
    const val DIRECT_NEIGHBORS_TYPE = 0x04

    fun encodeNeighbors(peerIds: List<String>): ByteArray {
        val unique = peerIds.distinct().take(10)
        val valueBytes = unique.flatMap { id -> hexStringPeerIdTo8Bytes(id).toList() }.toByteArray()
        val length = valueBytes.size.coerceAtMost(255)
        return byteArrayOf(DIRECT_NEIGHBORS_TYPE.toByte(), length.toByte()) +
            valueBytes.copyOf(length)
    }

    fun decodeNeighborsFromAnnouncementPayload(payload: ByteArray): List<String>? {
        val result = mutableListOf<String>()
        var offset = 0
        while (offset + 2 <= payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val length = payload[offset + 1].toInt() and 0xFF
            offset += 2
            if (offset + length > payload.size) break
            val value = payload.copyOfRange(offset, offset + length)
            offset += length

            if (type == DIRECT_NEIGHBORS_TYPE) {
                var pos = 0
                while (pos + 8 <= value.size) {
                    val idBytes = value.copyOfRange(pos, pos + 8)
                    result.add(bytesToPeerIdHex(idBytes))
                    pos += 8
                }
                return result
            }
        }
        return null
    }

    private fun hexStringPeerIdTo8Bytes(hexString: String): ByteArray {
        val clean = hexString.lowercase(Locale.US).take(16)
        val result = ByteArray(8)
        var idx = 0
        var out = 0
        while (idx + 1 < clean.length && out < 8) {
            val byteStr = clean.substring(idx, idx + 2)
            val byteValue = byteStr.toIntOrNull(16)?.toByte() ?: 0
            result[out++] = byteValue
            idx += 2
        }
        return result
    }

    private fun bytesToPeerIdHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (byte in bytes.take(8)) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }
}
