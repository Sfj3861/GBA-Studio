package com.sfj3861.gbastudio.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaRomHeaderParserTest {
    @Test
    fun parsesHeaderAndValidChecksumAndEntryBranch() {
        val bytes = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
        // ARM B from 0x08000000 to 0x080000C0 (PC is 0x08000008).
        bytes[0] = 0x2E
        bytes[1] = 0x00
        bytes[2] = 0x00
        bytes[3] = 0xEA.toByte()
        bytes[4] = 0x01
        "TEST GAME".toByteArray().copyInto(bytes, 0xA0)
        "ABCD".toByteArray().copyInto(bytes, 0xAC)
        "01".toByteArray().copyInto(bytes, 0xB0)
        bytes[0xB2] = 0x96.toByte()
        bytes[0xBC] = 2
        bytes[0xBD] = GbaRomHeaderParser.calculateChecksum(bytes).toByte()

        val header = GbaRomHeaderParser.parse(bytes)

        assertEquals("TEST GAME", header.title)
        assertEquals("ABCD", header.gameCode)
        assertEquals("01", header.makerCode)
        assertEquals(2, header.softwareVersion)
        assertTrue(header.checksumValid)
        assertEquals(0x96, header.fixedValue)
        assertTrue(header.fixedValueValid)
        assertTrue(header.metadataFieldsValid)
        assertTrue(header.hasLogoData)
        assertEquals(0x080000C0L, header.entryPointAddress)
        assertTrue(header.hasEntryBranch)
    }

    @Test
    fun detectsInvalidFixedValueEvenWhenChecksumMatches() {
        val bytes = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
        bytes[0xB2] = 0
        bytes[0xBD] = GbaRomHeaderParser.calculateChecksum(bytes).toByte()

        val header = GbaRomHeaderParser.parse(bytes)
        assertTrue(header.checksumValid)
        assertFalse(header.fixedValueValid)
        assertFalse(header.metadataFieldsValid)
    }

    @Test
    fun detectsInvalidChecksumAndMissingEntryBranch() {
        val bytes = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
        bytes[0xB2] = 0x96.toByte()
        bytes[0xBD] = 0

        val header = GbaRomHeaderParser.parse(bytes)
        assertFalse(header.checksumValid)
        assertNull(header.entryPointAddress)
        assertFalse(header.hasEntryBranch)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortInput() {
        GbaRomHeaderParser.parse(ByteArray(32))
    }
}
