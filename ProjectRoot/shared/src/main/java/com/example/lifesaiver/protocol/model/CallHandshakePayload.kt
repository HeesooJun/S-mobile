package com.example.lifesaiver.protocol.model

enum class CallHandshakeAction(val code: Int) {
    START(1),
    END(2),
    ACK(3);

    companion object {
        fun fromCode(code: Int): CallHandshakeAction? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

enum class CallHandshakeState(val code: Int) {
    AWARE_TRY(1),
    AWARE_OK(2),
    AWARE_FAIL(3),
    DIRECT_TRY(4),
    DIRECT_OK(5),
    DIRECT_FAIL(6);

    companion object {
        fun fromCode(code: Int): CallHandshakeState? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

data class CallHandshakePayload(
    val action: CallHandshakeAction,
    val callerName: String? = null,
    val wifiAwareSupported: Boolean = false,
    val wifiDirectSupported: Boolean = false,
    val useOpus: Boolean = true,
    val state: CallHandshakeState? = null,
    val rttCm: Int? = null,
    val directDeviceAddress: String? = null
) {
    fun encode(): ByteArray {
        val nameBytes = callerName?.trim().orEmpty().toByteArray(Charsets.UTF_8)
        val directBytes = directDeviceAddress?.let { Companion.macToBytes(it) }
        val extraLen =
            (if (directBytes != null) 6 else 0) + (if (rttCm != null) 2 else 0) + (if (state != null) 1 else 0)
        val payload = ByteArray(2 + nameBytes.size + extraLen)
        payload[0] = action.code.toByte()
        var flags = 0
        if (wifiAwareSupported) flags = flags or 0x01
        if (wifiDirectSupported) flags = flags or 0x02
        if (useOpus) flags = flags or 0x04
        if (state != null) flags = flags or 0x08
        if (rttCm != null) flags = flags or 0x10
        if (directBytes != null) flags = flags or 0x20
        payload[1] = flags.toByte()
        if (nameBytes.isNotEmpty()) {
            System.arraycopy(nameBytes, 0, payload, 2, nameBytes.size)
        }
        var offset = 2 + nameBytes.size
        if (directBytes != null) {
            System.arraycopy(directBytes, 0, payload, offset, directBytes.size)
            offset += directBytes.size
        }
        if (rttCm != null) {
            val clamped = rttCm.coerceIn(0, 0xFFFF)
            payload[offset] = ((clamped shr 8) and 0xFF).toByte()
            payload[offset + 1] = (clamped and 0xFF).toByte()
            offset += 2
        }
        if (state != null) {
            payload[offset] = state.code.toByte()
        }
        return payload
    }

    companion object {
        fun decode(bytes: ByteArray): CallHandshakePayload? {
            if (bytes.size < 2) return null
            val action = CallHandshakeAction.fromCode(bytes[0].toInt()) ?: return null
            val flags = bytes[1].toInt()
            val wifiAwareSupported = (flags and 0x01) != 0
            val wifiDirectSupported = (flags and 0x02) != 0
            val useOpus = (flags and 0x04) != 0
            val hasState = (flags and 0x08) != 0
            val hasRtt = (flags and 0x10) != 0
            val hasDirectAddr = (flags and 0x20) != 0
            val extrasLen =
                (if (hasDirectAddr) 6 else 0) + (if (hasRtt) 2 else 0) + (if (hasState) 1 else 0)
            if (bytes.size < 2 + extrasLen) return null
            val nameEnd = bytes.size - extrasLen
            val name = if (nameEnd > 2) {
                bytes.copyOfRange(2, nameEnd).toString(Charsets.UTF_8)
            } else {
                ""
            }
            var offset = nameEnd
            val directDeviceAddress = if (hasDirectAddr && offset + 5 < bytes.size) {
                bytesToMac(bytes, offset)
            } else {
                null
            }
            if (hasDirectAddr) {
                offset += 6
            }
            val rttCm = if (hasRtt && offset + 1 < bytes.size) {
                ((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF)
            } else {
                null
            }
            if (hasRtt) {
                offset += 2
            }
            val state = if (hasState && offset < bytes.size) {
                CallHandshakeState.fromCode(bytes[offset].toInt())
            } else {
                null
            }
            return CallHandshakePayload(
                action = action,
                callerName = name,
                wifiAwareSupported = wifiAwareSupported,
                wifiDirectSupported = wifiDirectSupported,
                useOpus = useOpus,
                state = state,
                rttCm = rttCm,
                directDeviceAddress = directDeviceAddress
            )
        }

        private fun macToBytes(address: String): ByteArray? {
            val parts = address.trim().split(":")
            if (parts.size != 6) return null
            return try {
                ByteArray(6) { index -> parts[index].toInt(16).toByte() }
            } catch (_: Exception) {
                null
            }
        }

        private fun bytesToMac(bytes: ByteArray, offset: Int): String {
            val sb = StringBuilder()
            for (i in 0 until 6) {
                val b = bytes[offset + i].toInt() and 0xFF
                if (i > 0) sb.append(':')
                sb.append(b.toString(16).padStart(2, '0'))
            }
            return sb.toString()
        }
    }
}
