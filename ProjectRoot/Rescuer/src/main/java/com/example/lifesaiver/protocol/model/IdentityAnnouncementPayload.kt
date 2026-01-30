package com.example.lifesaiver.protocol.model

import java.io.ByteArrayOutputStream

data class IdentityAnnouncementPayload(
    val nickname: String,
    val noisePublicKey: ByteArray,
    val signingPublicKey: ByteArray
) {
    fun encode(): ByteArray? {
        val nicknameBytes = nickname.toByteArray(Charsets.UTF_8)
        if (nicknameBytes.size > MAX_TLV_LENGTH ||
            noisePublicKey.size > MAX_TLV_LENGTH ||
            signingPublicKey.size > MAX_TLV_LENGTH
        ) {
            return null
        }

        val out = ByteArrayOutputStream()
        writeTlv(out, TLV_NICKNAME, nicknameBytes)
        writeTlv(out, TLV_NOISE_PUBLIC_KEY, noisePublicKey)
        writeTlv(out, TLV_SIGNING_PUBLIC_KEY, signingPublicKey)
        return out.toByteArray()
    }

    companion object {
        private const val TLV_NICKNAME = 0x01
        private const val TLV_NOISE_PUBLIC_KEY = 0x02
        private const val TLV_SIGNING_PUBLIC_KEY = 0x03
        private const val MAX_TLV_LENGTH = 255

        fun decode(data: ByteArray): IdentityAnnouncementPayload? {
            var offset = 0
            var nickname: String? = null
            var noisePublicKey: ByteArray? = null
            var signingPublicKey: ByteArray? = null

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
                    else -> Unit
                }
            }

            if (nickname == null || noisePublicKey == null || signingPublicKey == null) return null
            return IdentityAnnouncementPayload(nickname, noisePublicKey, signingPublicKey)
        }

        private fun writeTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type)
            out.write(value.size)
            out.write(value)
        }
    }
}
