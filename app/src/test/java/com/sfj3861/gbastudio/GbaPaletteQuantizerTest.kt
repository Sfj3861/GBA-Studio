package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaPaletteQuantizerTest {
    @Test fun reservesIndexZeroForTransparencyAndKeepsOpaqueBlack() {
        val result = GbaPaletteQuantizer.quantize(
            intArrayOf(0x00000000, 0xFF000000.toInt(), 0x7FFFFFFF.toInt())
        )
        assertEquals(1, result.sourceColorCount)
        assertEquals(0, result.indices[0])
        assertEquals(1, result.indices[1])
        assertEquals(0, result.indices[2])
        assertEquals(0, result.palette[0])
        assertEquals(0, result.palette[1])
    }

    @Test fun reducesMoreThanFifteenColorsToNearestPaletteEntry() {
        val pixels = IntArray(16) { index ->
            val channel = index * 16
            (0xFF shl 24) or (channel shl 16) or (channel shl 8) or channel
        }
        val result = GbaPaletteQuantizer.quantize(pixels)
        assertEquals(16, result.sourceColorCount)
        assertEquals(16, result.palette.size)
        assertTrue(result.indices.all { it in 1..15 })
    }

    @Test fun mostFrequentColorsAreSelectedFirst() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val result = GbaPaletteQuantizer.quantize(intArrayOf(red, red, red, blue))
        assertEquals(GbaPaletteQuantizer.toBgr555(red), result.palette[1])
        assertEquals(1, result.indices[0])
        assertEquals(2, result.indices[3])
    }
}
