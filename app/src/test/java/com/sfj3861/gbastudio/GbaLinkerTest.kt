package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaLinkerTest {
    @Test fun writesGbaHeaderFieldsAndEntryBranch() {
        val result = GbaLinker.linkRawSections(
            listOf(GbaSection("payload", byteArrayOf(1, 2, 3, 4))),
            "TEST GAME", "ABCD", "01", version = 7
        )
        assertEquals(0x080000C0.toInt(), result.sections.single().address)
        assertEquals(1, result.image[0xC0].toInt())
        assertEquals("TEST GAME", String(result.image, 0xA0, 9))
        assertEquals(0x96.toByte(), result.image[0xB2])
        assertEquals(7.toByte(), result.image[0xBC])
        assertEquals(0xEA00002E.toInt(), read32(result.image, 0))
        assertTrue(GbaRomHeader.hasValidComplement(result.image))
        assertFalse(GbaRomHeader.hasLogo(result.image))
        assertTrue(result.warnings.any { it.contains("logo") })
    }

    @Test fun copiesProvidedLogoAndComputesHeaderComplement() {
        val logo = ByteArray(GbaRomHeader.LOGO_SIZE) { (it * 13).toByte() }
        val result = GbaLinker.linkRawSections(
            listOf(GbaSection("code", byteArrayOf(0, 0, 0, 0))),
            "LOGO TEST", "ABCD", "01", nintendoLogo = logo
        )
        assertTrue(GbaRomHeader.hasLogo(result.image))
        assertTrue(logo.contentEquals(result.image.copyOfRange(0x04, 0xA0)))
        assertTrue(GbaRomHeader.hasValidComplement(result.image))
        assertFalse(result.warnings.any { it.contains("logo") })
    }

    @Test fun resolvesAbsoluteSymbolRelocationIntoRamSection() {
        val result = GbaLinker.linkRawSections(
            listOf(
                GbaSection("code", byteArrayOf(0, 0, 0, 0), relocations = listOf(
                    GbaRelocation(0, "dataStart", GbaRelocation.Type.ABS32)
                )),
                GbaSection("data", byteArrayOf(9, 8, 7, 6), region = GbaSection.Region.EWRAM)
            ),
            "RELOC TEST",
            symbols = listOf(GbaSymbol("dataStart", "data", 0))
        )
        assertEquals(GbaLinker.EWRAM_BASE, read32(result.image, 0xC0))
        assertEquals(9, result.image[0xC4].toInt())
    }

    @Test fun appliesArmBranchRelocation() {
        val result = GbaLinker.linkRawSections(
            listOf(
                GbaSection("code", byteArrayOf(0x00, 0x00, 0x00, 0xEA.toByte()),
                    relocations = listOf(GbaRelocation(0, "target", GbaRelocation.Type.ARM_BRANCH24))),
                GbaSection("target", byteArrayOf(0, 0, 0, 0))
            ),
            "ARM BRANCH",
            symbols = listOf(GbaSymbol("target", "target", 0))
        )
        assertEquals(0xEAFFFFFF.toInt(), read32(result.image, 0xC0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverlappingRelocations() {
        GbaLinker.linkRawSections(
            listOf(GbaSection("code", ByteArray(8), relocations = listOf(
                GbaRelocation(0, "target", GbaRelocation.Type.ABS32),
                GbaRelocation(2, "target", GbaRelocation.Type.ABS32)
            )), GbaSection("target", ByteArray(4))),
            "OVERLAP",
            symbols = listOf(GbaSymbol("target", "target", 0))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingRomEntrySection() {
        GbaLinker.linkRawSections(
            listOf(GbaSection("data", byteArrayOf(1), region = GbaSection.Region.EWRAM)),
            "NO ENTRY"
        )
    }


    @Test fun appliesRel32RelocationFromRomSection() {
        val result = GbaLinker.linkRawSections(
            listOf(
                GbaSection("code", byteArrayOf(0, 0, 0, 0), relocations = listOf(
                    GbaRelocation(0, "target", GbaRelocation.Type.REL32)
                )),
                GbaSection("target", byteArrayOf(1, 2, 3, 4))
            ),
            "REL32 TEST",
            symbols = listOf(GbaSymbol("target", "target", 0))
        )
        assertEquals(4, read32(result.image, 0xC0))
    }

    @Test fun appliesThumbBranchRelocationToEarlierRomSection() {
        val result = GbaLinker.linkRawSections(
            listOf(
                GbaSection("target", byteArrayOf(1, 2, 3, 4)),
                GbaSection("branch", byteArrayOf(0x00, 0xE0.toByte()), alignment = 2,
                    relocations = listOf(GbaRelocation(0, "target", GbaRelocation.Type.THUMB_BRANCH11)))
            ),
            "THUMB BRANCH",
            symbols = listOf(GbaSymbol("target", "target", 0))
        )
        assertEquals(0xE7FC, (result.image[0xC4].toInt() and 255) or
            ((result.image[0xC5].toInt() and 255) shl 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArmBranchRelocationWithWrongOpcode() {
        GbaLinker.linkRawSections(
            listOf(
                GbaSection("code", byteArrayOf(0, 0, 0, 0), relocations = listOf(
                    GbaRelocation(0, "target", GbaRelocation.Type.ARM_BRANCH24)
                )),
                GbaSection("target", byteArrayOf(0, 0, 0, 0))
            ),
            "BAD ARM",
            symbols = listOf(GbaSymbol("target", "target", 0))
        )
    }

    private fun read32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 3].toInt() and 255) shl 24)
}
