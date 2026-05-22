package com.example.lifesaivior.protocol.model

import com.example.lifesaivior.protocol.sync.SyncDefaults

data class RequestSyncPayload(
    val p: Int,
    val m: Long,
    val data: ByteArray
) {
    fun encode(): ByteArray {
        val out = ArrayList<Byte>()
        fun putTlv(type: Int, value: ByteArray) {
            out.add(type.toByte())
            val len = value.size
            out.add(((len ushr 8) and 0xFF).toByte())
            out.add((len and 0xFF).toByte())
            out.addAll(value.toList())
        }
        putTlv(0x01, byteArrayOf(p.toByte()))
        val m32 = m.coerceAtMost(0xffff_ffffL)
        putTlv(
            0x02,
            byteArrayOf(
                ((m32 ushr 24) and 0xFF).toByte(),
                ((m32 ushr 16) and 0xFF).toByte(),
                ((m32 ushr 8) and 0xFF).toByte(),
                (m32 and 0xFF).toByte()
            )
        )
        putTlv(0x03, data)
        return out.toByteArray()
    }

    companion object {
        const val MAX_ACCEPT_FILTER_BYTES: Int = SyncDefaults.MAX_ACCEPT_FILTER_BYTES

        fun decode(data: ByteArray): RequestSyncPayload? {
            var offset = 0
            var p: Int? = null
            var m: Long? = null
            var payload: ByteArray? = null

            while (offset + 3 <= data.size) {
                val type = data[offset].toInt() and 0xFF
                offset += 1
                val len = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                offset += 2
                if (offset + len > data.size) return null
                val value = data.copyOfRange(offset, offset + len)
                offset += len
                when (type) {
                    0x01 -> if (len == 1) p = value[0].toInt() and 0xFF
                    0x02 -> if (len == 4) {
                        val mm = ((value[0].toLong() and 0xFF) shl 24) or
                            ((value[1].toLong() and 0xFF) shl 16) or
                            ((value[2].toLong() and 0xFF) shl 8) or
                            (value[3].toLong() and 0xFF)
                        m = mm
                    }
                    0x03 -> {
                        if (value.size > MAX_ACCEPT_FILTER_BYTES) return null
                        payload = value
                    }
                }
            }

            val pp = p ?: return null
            val mm = m ?: return null
            val dd = payload ?: return null
            if (pp < 1 || mm <= 0L) return null
            return RequestSyncPayload(pp, mm, dd)
        }
    }
}
