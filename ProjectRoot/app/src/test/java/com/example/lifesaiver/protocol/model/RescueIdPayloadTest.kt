package com.example.lifesaiver.protocol.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RescueIdPayloadTest {

    @Test
    fun encodeDecode_fullFields() {
        val payload = RescueIdPayload(
            name = "홍길동",
            birthDate = "19991231",
            gender = RescueIdPayload.Gender.F
        )

        val decoded = RescueIdPayload.decode(payload.encode())
        assertNotNull(decoded)
        decoded!!

        assertEquals("홍길동", decoded.name)
        assertEquals("19991231", decoded.birthDate)
        assertEquals(RescueIdPayload.Gender.F, decoded.gender)
    }

    @Test
    fun encodeDecode_optionalFields() {
        val payload = RescueIdPayload(name = "name-only")
        val decoded = RescueIdPayload.decode(payload.encode())

        assertNotNull(decoded)
        decoded!!
        assertEquals("name-only", decoded.name)
        assertNull(decoded.birthDate)
        assertEquals(RescueIdPayload.Gender.UNKNOWN, decoded.gender)
    }

    @Test
    fun decode_ignoresUnknownTlv() {
        val base = RescueIdPayload(name = "test").encode()
        val unknownTlv = byteArrayOf(
            0x7F.toByte(), 0x00, 0x01, 0x42
        )
        val combined = base + unknownTlv

        val decoded = RescueIdPayload.decode(combined)
        assertNotNull(decoded)
        decoded!!
        assertEquals("test", decoded.name)
    }
}
