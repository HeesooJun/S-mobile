package com.example.lifesaiver.protocol.model

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RescueIdPayload(
    val name: String? = null,
    val birthDate: String? = null,
    val gender: Gender = Gender.UNKNOWN
) {
    enum class Gender(val code: Int) {
        UNKNOWN(0),
        M(1),
        F(2),
        OTHER(3);

        companion object {
            fun fromCode(code: Int): Gender {
                return entries.firstOrNull { it.code == code } ?: UNKNOWN
            }
        }
    }

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        name?.let { value ->
            writeTlv(out, TLV_NAME, value.toByteArray(Charsets.UTF_8))
        }
        birthDate?.let { value ->
            writeTlv(out, TLV_BIRTH_DATE, value.toByteArray(Charsets.UTF_8))
        }
        if (gender != Gender.UNKNOWN) {
            writeTlv(out, TLV_GENDER, byteArrayOf(gender.code.toByte()))
        }
        return out.toByteArray()
    }

    companion object {
        private const val TLV_NAME = 0x01
        private const val TLV_BIRTH_DATE = 0x02
        private const val TLV_GENDER = 0x03

        fun decode(bytes: ByteArray): RescueIdPayload? {
            var index = 0
            var name: String? = null
            var birthDate: String? = null
            var gender = Gender.UNKNOWN

            while (index + 3 <= bytes.size) {
                val type = bytes[index].toInt() and 0xFF
                val length = ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                    (bytes[index + 2].toInt() and 0xFF)
                index += 3

                if (length < 0 || index + length > bytes.size) return null
                val value = bytes.copyOfRange(index, index + length)
                index += length

                when (type) {
                    TLV_NAME -> name = value.toString(Charsets.UTF_8)
                    TLV_BIRTH_DATE -> birthDate = value.toString(Charsets.UTF_8)
                    TLV_GENDER -> {
                        val code = value.firstOrNull()?.toInt() ?: 0
                        gender = Gender.fromCode(code)
                    }
                    else -> Unit
                }
            }

            return RescueIdPayload(name = name, birthDate = birthDate, gender = gender)
        }

        private fun writeTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type)
            val lengthBytes = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.size.toShort()).array()
            out.write(lengthBytes)
            out.write(value)
        }
    }
}
