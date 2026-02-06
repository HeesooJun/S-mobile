package com.example.lifesaivior.protocol.profile

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ProfileTlv {
    const val TYPE_NAME = 0x01
    const val TYPE_GENDER = 0x02
    const val TYPE_BIRTH_DATE = 0x03
    const val TYPE_NOTES = 0x04
    const val TYPE_UPDATED_AT = 0x05
    const val TYPE_SCHEMA_VERSION = 0x06
    const val TYPE_KIND = 0x07

    const val KIND_UPDATE = 0x01
    const val KIND_REQUEST = 0x02
    const val KIND_RESPONSE = 0x03

    data class Decoded(
        val kind: Int?,
        val updatedAt: Long?,
        val schemaVersion: Int?,
        val name: String?,
        val gender: Char?,
        val birthDate: String?,
        val notes: String?
    )

    sealed class DecodeResult {
        data class Success(val decoded: Decoded) : DecodeResult()
        data class Failure(val reason: String) : DecodeResult()
    }

    fun encodeUpdate(
        name: String?,
        gender: Char?,
        birthDate: String?,
        notes: String?,
        updatedAt: Long,
        schemaVersion: Int = 1,
        kind: Int = KIND_UPDATE
    ): ByteArray {
        val result = ArrayList<Byte>()
        addTlv(result, TYPE_KIND, byteArrayOf(kind.toByte()))
        addTlv(result, TYPE_SCHEMA_VERSION, byteArrayOf(schemaVersion.toByte()))
        addTlv(result, TYPE_UPDATED_AT, longToBytes(updatedAt))
        if (!name.isNullOrBlank()) {
            addTlv(result, TYPE_NAME, name.toByteArray(Charsets.UTF_8))
        }
        if (gender != null) {
            addTlv(result, TYPE_GENDER, byteArrayOf(gender.code.toByte()))
        }
        if (!birthDate.isNullOrBlank()) {
            addTlv(result, TYPE_BIRTH_DATE, birthDate.toByteArray(Charsets.UTF_8))
        }
        if (!notes.isNullOrBlank()) {
            addTlv(result, TYPE_NOTES, notes.toByteArray(Charsets.UTF_8))
        }
        return result.toByteArray()
    }

    fun decodeIfProfile(payload: ByteArray): DecodeResult? {
        if (payload.size < 2) return null
        val firstType = payload[0].toInt() and 0xFF
        if (firstType != TYPE_KIND) return null
        return decode(payload)
    }

    fun decode(payload: ByteArray): DecodeResult {
        var offset = 0
        var kind: Int? = null
        var updatedAt: Long? = null
        var schemaVersion: Int? = null
        var name: String? = null
        var gender: Char? = null
        var birthDate: String? = null
        var notes: String? = null

        while (offset + 2 <= payload.size) {
            val type = payload[offset].toInt() and 0xFF
            val length = payload[offset + 1].toInt() and 0xFF
            offset += 2

            if (offset + length > payload.size) {
                return DecodeResult.Failure("TLV length overflow: type=$type length=$length")
            }

            val value = payload.copyOfRange(offset, offset + length)
            offset += length

            when (type) {
                TYPE_KIND -> {
                    if (length != 1) {
                        return DecodeResult.Failure("kind length invalid: $length")
                    }
                    kind = value[0].toInt() and 0xFF
                }
                TYPE_UPDATED_AT -> {
                    if (length != 8) {
                        return DecodeResult.Failure("updatedAt length invalid: $length")
                    }
                    val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
                    updatedAt = buffer.long
                }
                TYPE_SCHEMA_VERSION -> {
                    if (length != 1) {
                        return DecodeResult.Failure("schemaVersion length invalid: $length")
                    }
                    schemaVersion = value[0].toInt() and 0xFF
                }
                TYPE_NAME -> {
                    name = value.toString(Charsets.UTF_8)
                }
                TYPE_GENDER -> {
                    if (length >= 1) {
                        gender = (value[0].toInt() and 0xFF).toChar()
                    }
                }
                TYPE_BIRTH_DATE -> {
                    birthDate = value.toString(Charsets.UTF_8)
                }
                TYPE_NOTES -> {
                    notes = value.toString(Charsets.UTF_8)
                }
                else -> {
                    // Unknown TLV type: ignore for forward compatibility.
                }
            }
        }

        return DecodeResult.Success(
            Decoded(
                kind = kind,
                updatedAt = updatedAt,
                schemaVersion = schemaVersion,
                name = name,
                gender = gender,
                birthDate = birthDate,
                notes = notes
            )
        )
    }

    private fun addTlv(out: MutableList<Byte>, type: Int, value: ByteArray) {
        val length = value.size.coerceAtMost(255)
        out.add(type.toByte())
        out.add(length.toByte())
        for (i in 0 until length) {
            out.add(value[i])
        }
    }

    private fun longToBytes(value: Long): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(value)
        return buffer.array()
    }
}
