package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaTileMapPreviewRendererTest {
    @Test fun reconstructsScreenblocksInVisualOrder() {
        val sourceWidth = 64
        val sourceHeight = 64
        val patterns = mapOf(
            (0 to 0) to 0x00,
            (1 to 0) to 0x11,
            (0 to 1) to 0x22,
            (1 to 1) to 0x33
        )
        val tiles = ByteArray(sourceWidth * sourceHeight * 32)
        for (y in 0 until sourceHeight) for (x in 0 until sourceWidth) {
            val key = (if (x < 32) 0 else 1) to (if (y < 32) 0 else 1)
            val offset = (y * sourceWidth + x) * 32
            tiles.fill(patterns.getValue(key).toByte(), offset, offset + 32)
        }
        val assets = GbaPaletteResult(
            colors = listOf(0, 0x001F, 0x03E0, 0x7C00),
            tiles4bpp = tiles,
            tileCount = sourceWidth * sourceHeight,
            warnings = emptyList(),
            width = sourceWidth * 8,
            height = sourceHeight * 8
        )
        val map = GbaTileMapBuilder.build(assets)
        val pixels = GbaTileMapPreviewRenderer.renderArgbPixels(map, assets.colors)

        assertEquals(512 * 512, pixels.size)
        assertEquals(0, pixels[0])
        assertEquals(0xFFFF0000.toInt(), pixels[32 * 8 * 512])
        assertEquals(0xFF00FF00.toInt(), pixels[(32 * 8) * 512])
        assertEquals(0xFF0000FF.toInt(), pixels[(32 * 8) * 512 + 32 * 8])
        assertTrue(map.uniqueTileCount == 4)
    }

    @Test fun supportsNonzeroPaletteBankMetadata() {
        val assets = GbaPaletteResult(
            listOf(0, 0x001F),
            ByteArray(32) { 0x11 },
            1,
            emptyList(),
            width = 8,
            height = 8
        )
        val map = GbaTileMapBuilder.build(assets, paletteBank = 5)
        val pixels = GbaTileMapPreviewRenderer.renderArgbPixels(map, assets.colors)
        assertEquals(0xFFFF0000.toInt(), pixels[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnEntryThatReferencesAnUnknownTile() {
        val assets = GbaPaletteResult(
            listOf(0, 0x001F), ByteArray(32), 1, emptyList(), width = 8, height = 8
        )
        val map = GbaTileMapBuilder.build(assets).copy(entries = intArrayOf(3) + IntArray(1023))
        GbaTileMapPreviewRenderer.renderArgbPixels(map, assets.colors)
    }
}
