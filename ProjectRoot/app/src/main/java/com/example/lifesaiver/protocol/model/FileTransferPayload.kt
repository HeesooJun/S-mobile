package com.example.lifesaiver.protocol.model

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BitChat-compatible file transfer TLV payload (v2)
 * TLVs:
 * 0x01 FILE_NAME  -> type(1) + len(2) + value
 * 0x02 FILE_SIZE  -> type(1) + len(2=4) + value(4)
 * 0x03 MIME_TYPE  -> type(1) + len(2) + value
 * 0x04 CONTENT    -> type(1) + len(4) + value
 */
data class FileTransferPayload(
    val fileName: String?,
    val fileSize: Long,
    val mimeType: String?,
    val content: ByteArray
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        fileName?.takeIf { it.isNotBlank() }?.let { name ->
            writeShortTlv(out, TLV_FILE_NAME, name.toByteArray(Charsets.UTF_8))
        }
        if (fileSize >= 0) {
            val sizeBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(fileSize.coerceAtMost(UInt.MAX_VALUE.toLong()).toInt())
                .array()
            writeShortTlv(out, TLV_FILE_SIZE, sizeBytes)
        }
        mimeType?.takeIf { it.isNotBlank() }?.let { mime ->
            writeShortTlv(out, TLV_MIME_TYPE, mime.toByteArray(Charsets.UTF_8))
        }
        writeLongTlv(out, TLV_CONTENT, content)
        return out.toByteArray()
    }

    companion object {
        private const val TLV_FILE_NAME = 0x01
        private const val TLV_FILE_SIZE = 0x02
        private const val TLV_MIME_TYPE = 0x03
        private const val TLV_CONTENT = 0x04

        fun decode(bytes: ByteArray): FileTransferPayload? {
            var index = 0
            var fileName: String? = null
            var fileSize: Long = -1
            var mimeType: String? = null
            var content: ByteArray? = null

            while (index < bytes.size) {
                val type = bytes[index].toInt() and 0xFF
                index += 1

                if (type == TLV_CONTENT) {
                    if (index + 4 > bytes.size) return null
                    val len = readInt(bytes, index)
                    index += 4
                    if (len < 0 || index + len > bytes.size) return null
                    val value = bytes.copyOfRange(index, index + len)
                    index += len
                    content = if (content == null) value else content + value
                    continue
                }

                if (index + 2 > bytes.size) return null
                val len = readShort(bytes, index)
                index += 2
                if (len < 0 || index + len > bytes.size) return null
                val value = bytes.copyOfRange(index, index + len)
                index += len

                when (type) {
                    TLV_FILE_NAME -> fileName = value.toString(Charsets.UTF_8)
                    TLV_FILE_SIZE -> {
                        if (len == 4) {
                            fileSize = readInt(value, 0).toLong() and 0xFFFFFFFFL
                        }
                    }
                    TLV_MIME_TYPE -> mimeType = value.toString(Charsets.UTF_8)
                    else -> Unit
                }
            }

            val resolvedContent = content ?: return null
            val resolvedSize = if (fileSize >= 0) fileSize else resolvedContent.size.toLong()
            return FileTransferPayload(
                fileName = fileName,
                fileSize = resolvedSize,
                mimeType = mimeType ?: "application/octet-stream",
                content = resolvedContent
            )
        }

        private fun writeShortTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type)
            val lenBytes = ByteBuffer.allocate(2)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort(value.size.toShort())
                .array()
            out.write(lenBytes)
            out.write(value)
        }

        private fun writeLongTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type)
            val lenBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value.size)
                .array()
            out.write(lenBytes)
            out.write(value)
        }

        private fun readShort(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xFF) shl 8) or
                (bytes[offset + 1].toInt() and 0xFF)
        }

        private fun readInt(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }
    }
}
