package com.example.lifesaiver.protocol.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FileTransferPayloadTest {

    @Test
    fun encodeDecode_roundTrip() {
        val content = "voice-data".toByteArray()
        val payload = FileTransferPayload(
            fileName = "note.m4a",
            fileSize = content.size.toLong(),
            mimeType = "audio/mp4",
            content = content
        )

        val decoded = FileTransferPayload.decode(payload.encode())
        assertNotNull(decoded)
        decoded!!
        assertEquals("note.m4a", decoded.fileName)
        assertEquals(content.size.toLong(), decoded.fileSize)
        assertEquals("audio/mp4", decoded.mimeType)
        assertArrayEquals(content, decoded.content)
    }
}
