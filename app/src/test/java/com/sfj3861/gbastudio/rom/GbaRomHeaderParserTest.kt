package com.sfj3861.gbastudio.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaRomHeaderParserTest {
    @Test
    fun parsesHeaderAndValidChecksum() {
        val bytes = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
        "TEST GAME".toByteArray().copyInto(bytes, 0xA0)
        "ABCD".toByteArray().copyInto(bytes, 0xAC)
        "01".toByteArray().copyInto(bytes, 0xB0)
        bytes[0xBC] = 2
        bytes[0xBD] = GbaRomHeaderParser.calculateChecksum(bytes).toByte()

        val header = GbaRomHeaderParser.parse(bytes)

        assertEquals("TEST GAME", header.title)
        assertEquals("ABCD", header.gameCode)
        assertEquals("01", header.makerCode)
        assertEquals(2, header.softwareVersion)
        assertTrue(header.checksumValid)
    }

    @Test
    fun detectsInvalidChecksum() {
        val bytes = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
        bytes[0xBD] = 0

        assertFalse(GbaRomHeaderParser.parse(bytes).checksumValid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortInput() {
        GbaRomHeaderParser.parse(ByteArray(32))
    }
}
