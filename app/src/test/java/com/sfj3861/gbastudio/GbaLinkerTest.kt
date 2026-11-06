package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaLinkerTest {
    @Test fun placesRawSectionAndWritesHeader() {
        val result = GbaLinker.linkRawSections(
            listOf(GbaSection("payload", byteArrayOf(1, 2, 3, 4))),
            "TEST GAME", "ABCD", "01"
        )
        assertEquals(0x080000C0.toInt(), result.sections.single().address)
        assertEquals(1, result.image[0xC0].toInt())
        assertEquals("TEST GAME", String(result.image, 0xA0, 9))
        var sum = 0
        for (i in 0xA0..0xBC) sum += result.image[i].toInt() and 255
        assertEquals(0, (sum + (result.image[0xBD].toInt() and 255) + 0x19) and 255)
        assertTrue(result.warnings.isNotEmpty())
    }
}
