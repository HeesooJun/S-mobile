package com.example.lifesaiver.protocol.codec

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressionUtil {
    private const val COMPRESSION_THRESHOLD_BYTES = 100

    fun shouldCompress(data: ByteArray): Boolean {
        if (data.size < COMPRESSION_THRESHOLD_BYTES) return false

        val byteFrequency = mutableMapOf<Byte, Int>()
        for (byte in data) {
            byteFrequency[byte] = (byteFrequency[byte] ?: 0) + 1
        }
        val uniqueByteRatio = byteFrequency.size.toDouble() / minOf(data.size, 256).toDouble()
        return uniqueByteRatio < 0.9
    }

    fun compress(data: ByteArray): ByteArray? {
        if (data.size < COMPRESSION_THRESHOLD_BYTES) return null

        return try {
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            deflater.setInput(data)
            deflater.finish()

            val outputStream = ByteArrayOutputStream(data.size)
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            deflater.end()

            val compressedData = outputStream.toByteArray()
            if (compressedData.isNotEmpty() && compressedData.size < data.size) {
                compressedData
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decompress(compressedData: ByteArray, originalSize: Int): ByteArray? {
        if (originalSize <= 0) return null

        return try {
            val inflater = Inflater(true)
            inflater.setInput(compressedData)

            val decompressedBuffer = ByteArray(originalSize)
            val actualSize = inflater.inflate(decompressedBuffer)
            inflater.end()

            when {
                actualSize == originalSize -> decompressedBuffer
                actualSize > 0 -> decompressedBuffer.copyOfRange(0, actualSize)
                else -> null
            }
        } catch (e: Exception) {
            try {
                val inflater = Inflater(false)
                inflater.setInput(compressedData)

                val decompressedBuffer = ByteArray(originalSize)
                val actualSize = inflater.inflate(decompressedBuffer)
                inflater.end()

                when {
                    actualSize == originalSize -> decompressedBuffer
                    actualSize > 0 -> decompressedBuffer.copyOfRange(0, actualSize)
                    else -> null
                }
            } catch (fallback: Exception) {
                null
            }
        }
    }
}
