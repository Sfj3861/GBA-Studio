package com.sfj3861.gbastudio

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaAssetBundleExporterTest {
    @Test fun writesAllMatchingAssetFilesAndManifest() {
        val assets = GbaPaletteResult(
            colors = listOf(0, 0x001F, 0x03E0),
            tiles4bpp = ByteArray(64) { if (it < 32) 0x11 else 0x22 },
            tileCount = 2,
            warnings = emptyList(),
            width = 16,
            height = 8
        )
        val map = GbaTileMapBuilder.build(assets, paletteBank = 3)
        val zipBytes = GbaAssetBundleExporter.createZip(assets, map)
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }

        assertEquals(
            setOf("tiles.4bpp", "palette.pal", "background.map", "asset.h", "asset.c", "asset.json", "README.txt"),
            files.keys
        )
        assertArrayEquals(map.uniqueTiles4bpp, files["tiles.4bpp"])
        assertArrayEquals(AssetStudio.paletteBytes(assets.colors), files["palette.pal"])
        assertArrayEquals(map.bytes, files["background.map"])
        assertTrue(String(files.getValue("asset.json")).contains("\"screenSizeCode\": 0"))
        assertTrue(String(files.getValue("asset.json")).contains("\"uniqueTileCount\": 2"))
        assertTrue(String(files.getValue("asset.h")).contains("#define GBA_BG_SIZE_CODE 0"))
        assertTrue(String(files.getValue("asset.h")).contains("#define GBA_BG_PALETTE_BANK 3"))
        assertTrue(String(files.getValue("asset.h")).contains("extern const uint16_t gba_asset_map[1024]"))
        assertTrue(String(files.getValue("asset.json")).contains("\"paletteBank\": 3"))
        assertTrue(String(files.getValue("README.txt")).contains("palette bank 3"))
        val cSource = String(files.getValue("asset.c"))
        assertTrue(cSource.contains("#include \"asset.h\""))
        assertTrue(cSource.contains("const uint8_t gba_asset_tiles[32]"))
        assertTrue(cSource.contains("const uint16_t gba_asset_palette[3]"))
        assertTrue(cSource.contains("const uint16_t gba_asset_map[1024]"))
    }


    @Test(expected = IllegalArgumentException::class)
    fun rejectsMapBytesThatDoNotMatchEntries() {
        val assets = GbaPaletteResult(listOf(0, 1), ByteArray(32), 1, emptyList(), width = 8, height = 8)
        val map = GbaTileMapBuilder.build(assets).copy(bytes = ByteArray(2048))
        GbaAssetBundleExporter.createZip(assets, map)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMapThatReferencesMissingTile() {
        val assets = GbaPaletteResult(listOf(0, 1), ByteArray(32), 1, emptyList(), width = 8, height = 8)
        val valid = GbaTileMapBuilder.build(assets)
        val entries = valid.entries.copyOf().also { it[0] = 1 }
        val bytes = valid.bytes.copyOf().also { it[0] = 1 }
        GbaAssetBundleExporter.createZip(assets, valid.copy(entries = entries, bytes = bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMapWithInconsistentTileBytes() {
        val assets = GbaPaletteResult(listOf(0, 1), ByteArray(32), 1, emptyList(), width = 8, height = 8)
        val map = GbaTileMapBuilder.build(assets).copy(uniqueTiles4bpp = ByteArray(31))
        GbaAssetBundleExporter.createZip(assets, map)
    }
}
