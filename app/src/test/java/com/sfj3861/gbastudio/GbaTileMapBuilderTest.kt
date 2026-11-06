package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Test

class GbaTileMapBuilderTest {
    private fun assets(width: Int, height: Int) = GbaPaletteResult(
        colors = listOf(0, 1),
        tiles4bpp = ByteArray((width / 8) * (height / 8) * 32),
        tileCount = (width / 8) * (height / 8),
        warnings = emptyList(),
        width = width,
        height = height
    )

    @Test fun padsSmallMapToOneScreenblock() {
        val map = GbaTileMapBuilder.build(assets(16, 8))
        assertEquals(32, map.widthTiles)
        assertEquals(32, map.heightTiles)
        assertEquals(0, map.sizeCode)
        assertEquals(0, map.entries[0])
        assertEquals(1, map.entries[1])
        assertEquals(2048, map.bytes.size)
    }

    @Test fun storesRightScreenblockAfterLeftBlockFor64By32Map() {
        val map = GbaTileMapBuilder.build(assets(64 * 8, 32 * 8))
        assertEquals(64, map.widthTiles)
        assertEquals(32, map.heightTiles)
        assertEquals(1, map.sizeCode)
        assertEquals(31, map.entries[31])
        assertEquals(32, map.entries[1024])
        assertEquals(33, map.entries[1025])
        assertEquals(2048, map.entries.size)
    }

    @Test fun storesBottomScreenblockAfterTopFor32By64Map() {
        val map = GbaTileMapBuilder.build(assets(32 * 8, 64 * 8))
        assertEquals(2, map.sizeCode)
        assertEquals(32 * 32, map.entries[1024])
    }

    @Test fun storesFourScreenblocksInHardwareOrderFor64By64Map() {
        val map = GbaTileMapBuilder.build(assets(64 * 8, 64 * 8))
        assertEquals(3, map.sizeCode)
        assertEquals(32 * 32, map.entries[1024])
        assertEquals(32, map.entries[2048])
        assertEquals(32 * 32 + 32, map.entries[3072])
    }

    @Test fun encodesPaletteBankInHighNibble() {
        val map = GbaTileMapBuilder.build(assets(8, 8), paletteBank = 3)
        assertEquals(0x3000, map.entries[0])
        assertEquals(0x00, map.bytes[0].toInt() and 255)
        assertEquals(0x30, map.bytes[1].toInt() and 255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThan1024Tiles() {
        GbaTileMapBuilder.build(assets(64 * 8, 17 * 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDimensionsLargerThan64Tiles() {
        GbaTileMapBuilder.build(assets(65 * 8, 8))
    }
}
