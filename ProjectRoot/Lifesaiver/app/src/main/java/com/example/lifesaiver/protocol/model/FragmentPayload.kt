package com.example.lifesaiver.protocol.model

import kotlin.random.Random

/**
 * Fragment payload structure:
 * - 8 bytes: fragment ID
 * - 2 bytes: index (big-endian)
 * - 2 bytes: total (big-endian)
 * - 1 byte: original message type
 * - N bytes: fragment data
 */
data class FragmentPayload(
    val fragmentId: ByteArray,
    val index: Int,
    val total: Int,
    val originalType: Int,
    val data: ByteArray
) {
    fun encode(): ByteArray {
        val payload = ByteArray(HEADER_SIZE + data.size)
        System.arraycopy(fragmentId, 0, payload, 0, FRAGMENT_ID_SIZE)
        payload[8] = ((index shr 8) and 0xFF).toByte()
        payload[9] = (index and 0xFF).toByte()
        payload[10] = ((total shr 8) and 0xFF).toByte()
        payload[11] = (total and 0xFF).toByte()
        payload[12] = (originalType and 0xFF).toByte()
        if (data.isNotEmpty()) {
            System.arraycopy(data, 0, payload, HEADER_SIZE, data.size)
        }
        return payload
    }

    fun isValid(): Boolean {
        return fragmentId.size == FRAGMENT_ID_SIZE &&
            index >= 0 &&
            total > 0 &&
            index < total &&
            data.isNotEmpty()
    }

    fun getFragmentIdString(): String {
        return fragmentId.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val HEADER_SIZE = 13
        const val FRAGMENT_ID_SIZE = 8

        fun decode(payload: ByteArray): FragmentPayload? {
            if (payload.size < HEADER_SIZE) return null
            return try {
                val fragmentId = payload.copyOfRange(0, FRAGMENT_ID_SIZE)
                val index = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
                val total = ((payload[10].toInt() and 0xFF) shl 8) or (payload[11].toInt() and 0xFF)
                val originalType = payload[12].toInt() and 0xFF
                val data = if (payload.size > HEADER_SIZE) {
                    payload.copyOfRange(HEADER_SIZE, payload.size)
                } else {
                    ByteArray(0)
                }
                FragmentPayload(fragmentId, index, total, originalType, data)
            } catch (_: Exception) {
                null
            }
        }

        fun generateFragmentId(): ByteArray {
            val fragmentId = ByteArray(FRAGMENT_ID_SIZE)
            Random.nextBytes(fragmentId)
            return fragmentId
        }
    }
}
