package com.example.lifesaiver.protocol.model

enum class DeviceControlCommand(val code: Int) {
    WAKE_SCREEN(1),
    BEEP(2),
    VIBRATE(3),
    HIGH_TONE(4),
    STOP_ALERTS(5),
    POWER_SAVE_ON(6),
    POWER_SAVE_OFF(7);

    companion object {
        fun fromCode(code: Int): DeviceControlCommand? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

data class DeviceControlPayload(
    val command: DeviceControlCommand,
    val durationMs: Int = 1_500,
    val intensity: Int = 2,
    val frequencyHz: Int? = null
) {
    fun encode(): ByteArray {
        val safeDuration = durationMs.coerceIn(100, 60_000)
        val safeIntensity = intensity.coerceIn(0, 3)
        val safeFrequency = frequencyHz?.coerceIn(500, 20_000)
        val hasFrequency = safeFrequency != null
        val flags = if (hasFrequency) {
            FLAG_HAS_FREQUENCY or safeIntensity
        } else {
            safeIntensity
        }
        val payload = ByteArray(7)
        payload[0] = VERSION.toByte()
        payload[1] = command.code.toByte()
        payload[2] = flags.toByte()
        payload[3] = ((safeDuration shr 8) and 0xFF).toByte()
        payload[4] = (safeDuration and 0xFF).toByte()
        val freq = safeFrequency ?: 0
        payload[5] = ((freq shr 8) and 0xFF).toByte()
        payload[6] = (freq and 0xFF).toByte()
        return payload
    }

    companion object {
        private const val VERSION = 1
        private const val FLAG_HAS_FREQUENCY = 0x80

        fun decode(bytes: ByteArray): DeviceControlPayload? {
            if (bytes.size < 7) return null
            val version = bytes[0].toInt() and 0xFF
            if (version != VERSION) return null
            val command = DeviceControlCommand.fromCode(bytes[1].toInt() and 0xFF) ?: return null
            val flags = bytes[2].toInt() and 0xFF
            val intensity = flags and 0x03
            val duration = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
            val hasFrequency = (flags and FLAG_HAS_FREQUENCY) != 0
            val frequency = if (hasFrequency) {
                ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
            } else {
                null
            }
            return DeviceControlPayload(
                command = command,
                durationMs = duration.coerceIn(100, 60_000),
                intensity = intensity.coerceIn(0, 3),
                frequencyHz = frequency?.takeIf { it in 500..20_000 }
            )
        }
    }
}
