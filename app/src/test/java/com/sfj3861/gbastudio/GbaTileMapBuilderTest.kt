package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaTileMapBuilderTest {
    private fun assets(
        widthTiles: Int,
        heightTiles: Int,
        tilePattern: (x: Int, y: Int) -> Int = { _, _ -> 0 }
    ): GbaPaletteResult {
        val bytes = ByteArray(widthTiles * heightTiles * 32)
        for (y in 0 until heightTiles) for (x in 0 until widthTiles) {
            val pattern = tilePattern(x, y)
            val offset = (y * widthTiles + x) * 32
            bytes.fill(pattern.toByte(), offset, offset + 32)
        }
        return GbaPaletteResult(
            colors = listOf(0, 1),
            tiles4bpp = bytes,
            tileCount = widthTiles * heightTiles,
            warnings = emptyList(),
            width = widthTiles * 8,
            height = heightTiles * 8
        )
    }

    @Test fun padsSmallMapAndDeduplicatesIdenticalTiles() {
        val map = GbaTileMapBuilder.build(assets(2, 1))
        assertEquals(32, map.widthTiles)
        assertEquals(32, map.heightTiles)
        assertEquals(0, map.sizeCode)
        assertEquals(0, map.entries[0])
        assertEquals(0, map.entries[1])
        assertEquals(1, map.uniqueTileCount)
        assertEquals(32, map.uniqueTiles4bpp.size)
        assertEquals(2048, map.bytes.size)
    }

    @Test fun storesRightScreenblockAfterLeftBlockFor64By32Map() {
        val map = GbaTileMapBuilder.build(assets(64, 32) { x, _ -> if (x >= 32) 0x11 else 0x00 })
        assertEquals(64, map.widthTiles)
        assertEquals(32, map.heightTiles)
        assertEquals(1, map.sizeCode)
        assertEquals(0, map.entries[0])
        assertEquals(1, map.entries[1024])
        assertEquals(1, map.entries[1025])
        assertEquals(2, map.uniqueTileCount)
        assertEquals(2048, map.entries.size)
    }

    @Test fun storesBottomScreenblockAfterTopFor32By64Map() {
        val map = GbaTileMapBuilder.build(assets(32, 64) { _, y -> if (y >= 32) 0x11 else 0x00 })
        assertEquals(2, map.sizeCode)
        assertEquals(1, map.entries[1024])
        assertEquals(2, map.uniqueTileCount)
    }

    @Test fun storesFourScreenblocksInHardwareOrderFor64By64Map() {
        val map = GbaTileMapBuilder.build(assets(64, 64) { x, y ->
            when {
                y < 32 && x < 32 -> 0x00
                y < 32 && x >= 32 -> 0x11
                y >= 32 && x < 32 -> 0x22
                else -> 0x33
            }
        })
        assertEquals(3, map.sizeCode)
        assertEquals(4, map.uniqueTileCount)
        assertEquals(0, map.entries[0])
        assertEquals(1, map.entries[1024])
        assertEquals(2, map.entries[2048])
        assertEquals(3, map.entries[3072])
        assertEquals(128 * 32, map.uniqueTiles4bpp.size)
    }

    @Test fun encodesPaletteBankInHighNibble() {
        val map = GbaTileMapBuilder.build(assets(1, 1), paletteBank = 3)
        assertEquals(0x3000, map.entries[0])
        assertEquals(0x00, map.bytes[0].toInt() and 255)
        assertEquals(0x30, map.bytes[1].toInt() and 255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThan1024UniqueTiles() {
        val widthTiles = 64
        val heightTiles = 17
        val bytes = ByteArray(widthTiles * heightTiles * 32)
        for (index in 0 until 1025) {
            val offset = index * 32
            bytes[offset] = index.toByte()
            bytes[offset + 1] = (index ushr 8).toByte()
        }
        val assets = GbaPaletteResult(
            listOf(0, 1), bytes, widthTiles * heightTiles, emptyList(),
            width = widthTiles * 8, height = heightTiles * 8
        )
        GbaTileMapBuilder.build(assets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDimensionsLargerThan64Tiles() {
        GbaTileMapBuilder.build(assets(65, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteTileData() {
        val valid = assets(2, 1)
        GbaTileMapBuilder.build(valid.copy(tiles4bpp = ByteArray(32)))
    }
}
