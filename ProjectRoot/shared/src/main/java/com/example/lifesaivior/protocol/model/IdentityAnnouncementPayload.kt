package com.example.lifesaivior.protocol.model

import java.io.ByteArrayOutputStream

data class IdentityAnnouncementPayload(
    val nickname: String,
    val noisePublicKey: ByteArray,
    val signingPublicKey: ByteArray,
    val wifiDirectAddress: String? = null,
    val batteryLevel: Int? = null,
    val powerSavingEnabled: Boolean? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val notes: String? = null
) {
    fun encode(): ByteArray? {
        val nicknameBytes = nickname.toByteArray(Charsets.UTF_8)
        val directBytes = wifiDirectAddress?.let { macToBytes(it) }
        val batteryBytes = batteryLevel?.coerceIn(0, 100)?.let { byteArrayOf(it.toByte()) }
        val powerSavingBytes = powerSavingEnabled?.let { byteArrayOf(if (it) 1 else 0) }
        val genderBytes = toOptionalTlvBytes(gender)
        val birthDateBytes = toOptionalTlvBytes(birthDate)
        val notesBytes = toOptionalTlvBytes(notes)
        if (nicknameBytes.size > MAX_TLV_LENGTH ||
            noisePublicKey.size > MAX_TLV_LENGTH ||
            signingPublicKey.size > MAX_TLV_LENGTH ||
            (directBytes != null && directBytes.size > MAX_TLV_LENGTH) ||
            (batteryBytes != null && batteryBytes.size > MAX_TLV_LENGTH) ||
            (powerSavingBytes != null && powerSavingBytes.size > MAX_TLV_LENGTH)
        ) {
            return null
        }

        val out = ByteArrayOutputStream()
        writeTlv(out, TLV_NICKNAME, nicknameBytes)
        writeTlv(out, TLV_NOISE_PUBLIC_KEY, noisePublicKey)
        writeTlv(out, TLV_SIGNING_PUBLIC_KEY, signingPublicKey)
        if (directBytes != null) {
            writeTlv(out, TLV_WIFI_DIRECT_ADDRESS, directBytes)
        }
        if (batteryBytes != null) {
            writeTlv(out, TLV_BATTERY_LEVEL, batteryBytes)
        }
        if (powerSavingBytes != null) {
            writeTlv(out, TLV_POWER_SAVING, powerSavingBytes)
        }
        if (genderBytes != null) {
            writeTlv(out, TLV_GENDER, genderBytes)
        }
        if (birthDateBytes != null) {
            writeTlv(out, TLV_BIRTH_DATE, birthDateBytes)
        }
        if (notesBytes != null) {
            writeTlv(out, TLV_NOTES, notesBytes)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TLV_NICKNAME = 0x01
        private const val TLV_NOISE_PUBLIC_KEY = 0x02
        private const val TLV_SIGNING_PUBLIC_KEY = 0x03
        private const val TLV_WIFI_DIRECT_ADDRESS = 0x05
        private const val TLV_BATTERY_LEVEL = 0x06
        private const val TLV_POWER_SAVING = 0x07
        private const val TLV_GENDER = 0x08
        private const val TLV_BIRTH_DATE = 0x09
        private const val TLV_NOTES = 0x0A
        private const val MAX_TLV_LENGTH = 255

        fun decode(data: ByteArray): IdentityAnnouncementPayload? {
            var offset = 0
            var nickname: String? = null
            var noisePublicKey: ByteArray? = null
            var signingPublicKey: ByteArray? = null
            var wifiDirectAddress: String? = null
            var batteryLevel: Int? = null
            var powerSavingEnabled: Boolean? = null
            var gender: String? = null
            var birthDate: String? = null
            var notes: String? = null

            while (offset + 2 <= data.size) {
                val type = data[offset].toInt() and 0xFF
                val length = data[offset + 1].toInt() and 0xFF
                offset += 2

                if (offset + length > data.size) return null
                val value = data.copyOfRange(offset, offset + length)
                offset += length

                when (type) {
                    TLV_NICKNAME -> nickname = value.toString(Charsets.UTF_8)
                    TLV_NOISE_PUBLIC_KEY -> noisePublicKey = value
                    TLV_SIGNING_PUBLIC_KEY -> signingPublicKey = value
                    TLV_WIFI_DIRECT_ADDRESS -> {
                        if (value.size == 6) {
                            wifiDirectAddress = bytesToMac(value, 0)
                        }
                    }
                    TLV_BATTERY_LEVEL -> {
                        if (value.isNotEmpty()) {
                            batteryLevel = (value[0].toInt() and 0xFF).coerceIn(0, 100)
                        }
                    }
                    TLV_POWER_SAVING -> {
                        if (value.isNotEmpty()) {
                            powerSavingEnabled = (value[0].toInt() and 0xFF) != 0
                        }
                    }
                    TLV_GENDER -> {
                        gender = value.toString(Charsets.UTF_8)
                    }
                    TLV_BIRTH_DATE -> {
                        birthDate = value.toString(Charsets.UTF_8)
                    }
                    TLV_NOTES -> {
                        notes = value.toString(Charsets.UTF_8)
                    }
                    else -> Unit
                }
            }

            if (nickname == null || noisePublicKey == null || signingPublicKey == null) return null
            return IdentityAnnouncementPayload(
                nickname = nickname,
                noisePublicKey = noisePublicKey,
                signingPublicKey = signingPublicKey,
                wifiDirectAddress = wifiDirectAddress,
                batteryLevel = batteryLevel,
                powerSavingEnabled = powerSavingEnabled,
                gender = gender,
                birthDate = birthDate,
                notes = notes
            )
        }

        private fun writeTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type)
            out.write(value.size)
            out.write(value)
        }

        private fun toOptionalTlvBytes(raw: String?): ByteArray? {
            val trimmed = raw?.trim()?.ifBlank { null } ?: return null
            val bytes = trimmed.toByteArray(Charsets.UTF_8)
            return if (bytes.size <= MAX_TLV_LENGTH) bytes else bytes.copyOf(MAX_TLV_LENGTH)
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
