package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class GbaTileDecoderTest {
    @Test fun decodesLowAndHighNibblesAndTransparency() {
        val tiles = ByteArray(32) { 0x21 }
        tiles[0] = 0x10
        val red = GbaPaletteQuantizer.toBgr555(0xFFFF0000.toInt())
        val green = GbaPaletteQuantizer.toBgr555(0xFF00FF00.toInt())

        val pixels = GbaTileDecoder.decodeArgbPixels(8, 8, tiles, listOf(0, red, green))

        assertEquals(0, pixels[0])
        assertEquals(0xFFFF0000.toInt(), pixels[1])
        assertEquals(0xFFFF0000.toInt(), pixels[2])
        assertEquals(0xFF00FF00.toInt(), pixels[3])
    }

    @Test fun decodesMultipleTilesInRowMajorOrder() {
        val tiles = ByteArray(64)
        tiles.fill(0x11, 0, 32)
        tiles.fill(0x22, 32, 64)
        val red = GbaPaletteQuantizer.toBgr555(0xFFFF0000.toInt())
        val green = GbaPaletteQuantizer.toBgr555(0xFF00FF00.toInt())

        val pixels = GbaTileDecoder.decodeArgbPixels(16, 8, tiles, listOf(0, red, green))

        assertEquals(0xFFFF0000.toInt(), pixels[0])
        assertEquals(0xFF00FF00.toInt(), pixels[8])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTileDataThatIsTooShort() {
        GbaTileDecoder.decodeArgbPixels(8, 8, ByteArray(31), listOf(0, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPaletteIndicesOutsidePalette() {
        GbaTileDecoder.decodeArgbPixels(8, 8, ByteArray(32) { 0xFF.toByte() }, listOf(0, 1))
    }
}
