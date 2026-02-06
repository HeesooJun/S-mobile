package com.example.lifesaivior.protocol.model

import java.io.ByteArrayOutputStream

enum class CallHandshakeAction(val code: Int) {
    START(1),
    END(2),
    ACK(3),
    UWB_SYNC(4);

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
    val directDeviceAddress: String? = null,
    val uwbDeviceAddress: String? = null,
    val uwbControllerAddress: String? = null,
    val uwbControllerChannel: Int? = null,
    val uwbControllerPreambleIndex: Int? = null,
    val uwbSessionId: Int? = null
) {
    fun encode(): ByteArray {
        val rawNameBytes = callerName?.trim().orEmpty().toByteArray(Charsets.UTF_8)
        val nameBytes = if (rawNameBytes.size <= 0xFF) rawNameBytes else rawNameBytes.copyOf(0xFF)
        val directBytes = directDeviceAddress?.let { macToBytes(it) }
        val uwbBytes = uwbDeviceAddress?.let { addressToBytes(it) }
        val controllerBytes = uwbControllerAddress?.let { addressToBytes(it) }
        val hasUwbController =
            controllerBytes != null &&
                uwbControllerChannel != null &&
                uwbControllerPreambleIndex != null &&
                uwbSessionId != null

        val payload = ByteArrayOutputStream()
        payload.write(action.code)
        var flags = 0
        if (wifiAwareSupported) flags = flags or FLAG_WIFI_AWARE
        if (wifiDirectSupported) flags = flags or FLAG_WIFI_DIRECT
        if (useOpus) flags = flags or FLAG_USE_OPUS
        if (state != null) flags = flags or FLAG_STATE
        if (rttCm != null) flags = flags or FLAG_RTT
        if (directBytes != null) flags = flags or FLAG_DIRECT_ADDR
        if (uwbBytes != null) flags = flags or FLAG_UWB_ADDR
        if (hasUwbController) flags = flags or FLAG_UWB_CONTROLLER
        payload.write(flags)
        payload.write(nameBytes.size)
        if (nameBytes.isNotEmpty()) {
            payload.write(nameBytes)
        }
        if (directBytes != null) {
            payload.write(directBytes)
        }
        if (rttCm != null) {
            val clamped = rttCm.coerceIn(0, 0xFFFF)
            payload.write((clamped shr 8) and 0xFF)
            payload.write(clamped and 0xFF)
        }
        if (state != null) {
            payload.write(state.code)
        }
        if (uwbBytes != null) {
            payload.write(uwbBytes.size and 0xFF)
            payload.write(uwbBytes)
        }
        if (hasUwbController) {
            val controllerAddressBytes = controllerBytes ?: return payload.toByteArray()
            payload.write(controllerAddressBytes.size and 0xFF)
            payload.write(controllerAddressBytes)
            val channel = (uwbControllerChannel ?: 0).coerceIn(0, 0xFFFF)
            val preamble = (uwbControllerPreambleIndex ?: 0).coerceIn(0, 0xFFFF)
            payload.write((channel shr 8) and 0xFF)
            payload.write(channel and 0xFF)
            payload.write((preamble shr 8) and 0xFF)
            payload.write(preamble and 0xFF)
            val sid = uwbSessionId ?: 0
            payload.write((sid ushr 24) and 0xFF)
            payload.write((sid ushr 16) and 0xFF)
            payload.write((sid ushr 8) and 0xFF)
            payload.write(sid and 0xFF)
        }
        return payload.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): CallHandshakePayload? {
            return decodeV2(bytes) ?: decodeLegacy(bytes)
        }

        private fun decodeV2(bytes: ByteArray): CallHandshakePayload? {
            if (bytes.size < 3) return null
            val action = CallHandshakeAction.fromCode(bytes[0].toInt()) ?: return null
            val flags = bytes[1].toInt() and 0xFF
            val nameLen = bytes[2].toInt() and 0xFF
            var offset = 3
            if (offset + nameLen > bytes.size) return null
            val callerName = if (nameLen > 0) {
                bytes.copyOfRange(offset, offset + nameLen).toString(Charsets.UTF_8)
            } else {
                ""
            }
            offset += nameLen

            val hasState = (flags and FLAG_STATE) != 0
            val hasRtt = (flags and FLAG_RTT) != 0
            val hasDirectAddress = (flags and FLAG_DIRECT_ADDR) != 0
            val hasUwbAddress = (flags and FLAG_UWB_ADDR) != 0
            val hasUwbController = (flags and FLAG_UWB_CONTROLLER) != 0

            val directDeviceAddress = if (hasDirectAddress) {
                if (offset + 6 > bytes.size) return null
                val value = bytesToMac(bytes, offset)
                offset += 6
                value
            } else {
                null
            }

            val rttCm = if (hasRtt) {
                if (offset + 2 > bytes.size) return null
                val value = ((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF)
                offset += 2
                value
            } else {
                null
            }

            val state = if (hasState) {
                if (offset >= bytes.size) return null
                val value = CallHandshakeState.fromCode(bytes[offset].toInt())
                offset += 1
                value
            } else {
                null
            }

            val uwbDeviceAddress = if (hasUwbAddress) {
                if (offset >= bytes.size) return null
                val length = bytes[offset].toInt() and 0xFF
                offset += 1
                if (length <= 0 || offset + length > bytes.size) return null
                val value = bytesToAddress(bytes, offset, length)
                offset += length
                value
            } else {
                null
            }

            var uwbControllerAddress: String? = null
            var uwbControllerChannel: Int? = null
            var uwbControllerPreambleIndex: Int? = null
            var uwbSessionId: Int? = null
            if (hasUwbController) {
                if (offset >= bytes.size) return null
                val controllerAddressLength = bytes[offset].toInt() and 0xFF
                offset += 1
                if (controllerAddressLength <= 0 || offset + controllerAddressLength + 8 > bytes.size) return null
                uwbControllerAddress = bytesToAddress(bytes, offset, controllerAddressLength)
                offset += controllerAddressLength
                uwbControllerChannel = ((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF)
                offset += 2
                uwbControllerPreambleIndex = ((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF)
                offset += 2
                uwbSessionId = ((bytes[offset].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)
                offset += 4
            }

            if (offset != bytes.size) return null

            return CallHandshakePayload(
                action = action,
                callerName = callerName,
                wifiAwareSupported = (flags and FLAG_WIFI_AWARE) != 0,
                wifiDirectSupported = (flags and FLAG_WIFI_DIRECT) != 0,
                useOpus = (flags and FLAG_USE_OPUS) != 0,
                state = state,
                rttCm = rttCm,
                directDeviceAddress = directDeviceAddress,
                uwbDeviceAddress = uwbDeviceAddress,
                uwbControllerAddress = uwbControllerAddress,
                uwbControllerChannel = uwbControllerChannel,
                uwbControllerPreambleIndex = uwbControllerPreambleIndex,
                uwbSessionId = uwbSessionId
            )
        }

        private fun decodeLegacy(bytes: ByteArray): CallHandshakePayload? {
            if (bytes.size < 2) return null
            val action = CallHandshakeAction.fromCode(bytes[0].toInt()) ?: return null
            val flags = bytes[1].toInt()
            val hasState = (flags and FLAG_STATE) != 0
            val hasRtt = (flags and FLAG_RTT) != 0
            val hasDirectAddress = (flags and FLAG_DIRECT_ADDR) != 0
            val extrasLen = (if (hasDirectAddress) 6 else 0) + (if (hasRtt) 2 else 0) + (if (hasState) 1 else 0)
            if (bytes.size < 2 + extrasLen) return null
            val nameEnd = bytes.size - extrasLen
            val name = if (nameEnd > 2) bytes.copyOfRange(2, nameEnd).toString(Charsets.UTF_8) else ""
            var offset = nameEnd
            val directAddress = if (hasDirectAddress && offset + 6 <= bytes.size) {
                val value = bytesToMac(bytes, offset)
                offset += 6
                value
            } else {
                null
            }
            val rttCm = if (hasRtt && offset + 2 <= bytes.size) {
                val value = ((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF)
                offset += 2
                value
            } else {
                null
            }
            val state = if (hasState && offset < bytes.size) {
                CallHandshakeState.fromCode(bytes[offset].toInt())
            } else {
                null
            }
            return CallHandshakePayload(
                action = action,
                callerName = name,
                wifiAwareSupported = (flags and FLAG_WIFI_AWARE) != 0,
                wifiDirectSupported = (flags and FLAG_WIFI_DIRECT) != 0,
                useOpus = (flags and FLAG_USE_OPUS) != 0,
                state = state,
                rttCm = rttCm,
                directDeviceAddress = directAddress
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

        private fun addressToBytes(address: String): ByteArray? {
            val normalized = address.trim().lowercase().replace('-', ':')
            val parts = if (normalized.contains(':')) {
                normalized.split(':').filter { it.isNotBlank() }
            } else {
                if (normalized.length % 2 != 0) return null
                normalized.chunked(2)
            }
            if (parts.isEmpty()) return null
            if (parts.any { part ->
                    (part.length != 1 && part.length != 2) ||
                        part.any { ch -> ch !in '0'..'9' && ch !in 'a'..'f' }
                }
            ) {
                return null
            }
            return try {
                ByteArray(parts.size) { index ->
                    parts[index].padStart(2, '0').toInt(16).toByte()
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun bytesToAddress(bytes: ByteArray, offset: Int, length: Int): String {
            return (0 until length).joinToString(":") { index ->
                val value = bytes[offset + index].toInt() and 0xFF
                value.toString(16).padStart(2, '0')
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

        private const val FLAG_WIFI_AWARE = 0x01
        private const val FLAG_WIFI_DIRECT = 0x02
        private const val FLAG_USE_OPUS = 0x04
        private const val FLAG_STATE = 0x08
        private const val FLAG_RTT = 0x10
        private const val FLAG_DIRECT_ADDR = 0x20
        private const val FLAG_UWB_ADDR = 0x40
        private const val FLAG_UWB_CONTROLLER = 0x80
    }
}
