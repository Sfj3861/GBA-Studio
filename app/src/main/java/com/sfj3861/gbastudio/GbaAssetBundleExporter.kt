package com.sfj3861.gbastudio

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates a portable asset package ready to be added to a GBA source project.
 * Includes raw binary assets and a C source/header pair with typed arrays.
 */
object GbaAssetBundleExporter {
    fun createZip(assets: GbaPaletteResult, map: GbaTileMapResult): ByteArray {
        val output = ByteArrayOutputStream()
        writeZip(assets, map, output)
        return output.toByteArray()
    }

    fun writeZip(assets: GbaPaletteResult, map: GbaTileMapResult, output: OutputStream) {
        validate(assets, map)
        val entries = linkedMapOf(
            "tiles.4bpp" to map.uniqueTiles4bpp,
            "palette.pal" to AssetStudio.paletteBytes(assets.colors),
            "background.map" to map.bytes,
            "asset.h" to header(assets, map).toByteArray(StandardCharsets.UTF_8),
            "asset.c" to cSource(assets, map).toByteArray(StandardCharsets.UTF_8),
            "asset.json" to manifest(assets, map).toByteArray(StandardCharsets.UTF_8),
            "README.txt" to readme(map).toByteArray(StandardCharsets.UTF_8)
        )

        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }

    private fun validate(assets: GbaPaletteResult, map: GbaTileMapResult) {
        require(assets.width > 0 && assets.height > 0 &&
            assets.width % 8 == 0 && assets.height % 8 == 0 &&
            assets.width.toLong() * assets.height.toLong() <= AssetStudio.MAX_PIXELS.toLong()) {
            "Las dimensiones de origen no son válidas."
        }
        val expectedSourceTiles = (assets.width.toLong() / 8L) * (assets.height.toLong() / 8L)
        require(expectedSourceTiles in 1..(AssetStudio.MAX_PIXELS / 64).toLong() &&
            assets.tileCount.toLong() == expectedSourceTiles) {
            "El recuento de tiles de origen no coincide con las dimensiones."
        }
        require(assets.tiles4bpp.size.toLong() == assets.tileCount.toLong() * 32L) {
            "Los datos 4bpp de origen no coinciden con el recuento de tiles."
        }
        require(assets.colors.isNotEmpty() && assets.colors.size <= 16 && assets.colors[0] == 0) {
            "La paleta debe tener entre 1 y 16 entradas y reservar el índice cero."
        }
        require(assets.colors.all { it in 0..0x7FFF }) {
            "La paleta contiene un color fuera del formato BGR555."
        }
        validate4BppIndices(assets.tiles4bpp, assets.colors.size, "Los tiles de origen")
        require(map.widthTiles in 32..64 && map.heightTiles in 32..64 &&
            map.widthTiles % 32 == 0 && map.heightTiles % 32 == 0) {
            "Las dimensiones del mapa no coinciden con screenblocks GBA."
        }
        require(map.paletteBank in 0..15) { "Banco de paleta fuera de rango." }
        require(map.uniqueTileCount in 1..1024 &&
            map.uniqueTiles4bpp.size == map.uniqueTileCount * 32) {
            "Los datos únicos no coinciden con el recuento de tiles."
        }
        validate4BppIndices(map.uniqueTiles4bpp, assets.colors.size, "Los tiles únicos")
        require(map.entries.size == map.widthTiles * map.heightTiles &&
            map.bytes.size == map.entries.size * 2) {
            "El tamaño de la tabla de mapa no coincide con sus dimensiones."
        }
        map.entries.forEachIndexed { index, entry ->
            require((entry and 0x03FF) < map.uniqueTileCount) {
                "La entrada $index referencia un tile inexistente."
            }
            require(((entry ushr 12) and 15) == map.paletteBank) {
                "La entrada $index usa otro banco de paleta."
            }
            val encoded = (map.bytes[index * 2].toInt() and 255) or
                ((map.bytes[index * 2 + 1].toInt() and 255) shl 8)
            require(encoded == entry) { "Los bytes del mapa no coinciden con la entrada $index." }
        }
        val expectedWidth = if (assets.width / 8 <= 32) 32 else 64
        val expectedHeight = if (assets.height / 8 <= 32) 32 else 64
        require(map.widthTiles == expectedWidth && map.heightTiles == expectedHeight) {
            "Las dimensiones del mapa no coinciden con la imagen de origen."
        }
        val expectedSizeCode = when {
            expectedWidth == 32 && expectedHeight == 32 -> 0
            expectedWidth == 64 && expectedHeight == 32 -> 1
            expectedWidth == 32 && expectedHeight == 64 -> 2
            else -> 3
        }
        require(map.sizeCode == expectedSizeCode) { "El código de tamaño no coincide con el mapa." }
    }

    private fun validate4BppIndices(data: ByteArray, paletteSize: Int, label: String) {
        data.forEachIndexed { byteIndex, packed ->
            val value = packed.toInt() and 255
            val low = value and 15
            val high = value ushr 4
            require(low < paletteSize && high < paletteSize) {
                "$label contienen un índice de paleta fuera de rango en el byte $byteIndex."
            }
        }
    }

    private fun header(assets: GbaPaletteResult, map: GbaTileMapResult): String = """
        #pragma once
        #include <stdint.h>

        /* Generated by GBA Studio. Map entries are stored in GBA screenblock order. */
        #define GBA_ASSET_SOURCE_WIDTH_PIXELS ${assets.width}
        #define GBA_ASSET_SOURCE_HEIGHT_PIXELS ${assets.height}
        #define GBA_ASSET_TILE_COUNT ${map.uniqueTileCount}
        #define GBA_BG_MAP_WIDTH_TILES ${map.widthTiles}
        #define GBA_BG_MAP_HEIGHT_TILES ${map.heightTiles}
        #define GBA_BG_SIZE_CODE ${map.sizeCode}
        #define GBA_BG_PALETTE_BANK ${map.paletteBank}
        #define GBA_BG_MAP_ENTRY_COUNT ${map.entries.size}

        extern const uint8_t gba_asset_tiles[${map.uniqueTiles4bpp.size}];
        extern const uint16_t gba_asset_palette[${assets.colors.size}];
        extern const uint16_t gba_asset_map[${map.entries.size}];

        #define GBA_BG_SCREEN_BASE_BLOCK 16
        void gba_asset_load_bg0(void);
    """.trimIndent() + "\n"

    private fun cSource(assets: GbaPaletteResult, map: GbaTileMapResult): String = buildString {
        append("#include \"asset.h\"\n\n")
        append("const uint8_t gba_asset_tiles[${map.uniqueTiles4bpp.size}] = {\n")
        appendArray(map.uniqueTiles4bpp.map { "0x%02X".format(it.toInt() and 255) })
        append("};\n\n")
        append("const uint16_t gba_asset_palette[${assets.colors.size}] = {\n")
        appendArray(assets.colors.map { "0x%04X".format(it) }, valuesPerLine = 8)
        append("};\n\n")
        append("const uint16_t gba_asset_map[${map.entries.size}] = {\n")
        appendArray(map.entries.map { "0x%04X".format(it) }, valuesPerLine = 8)
        append("};\n\n")
        append(
            """
            void gba_asset_load_bg0(void) {
                volatile uint16_t *vram = (volatile uint16_t *)0x06000000;
                volatile uint16_t *bgPalette = (volatile uint16_t *)0x05000000;
                volatile uint16_t *bg0cnt = (volatile uint16_t *)0x04000008;
                volatile uint16_t *dispcnt = (volatile uint16_t *)0x04000000;

                for (unsigned int i = 0; i < sizeof(gba_asset_tiles) / 2; ++i) {
                    const unsigned int low = gba_asset_tiles[i * 2];
                    const unsigned int high = gba_asset_tiles[i * 2 + 1];
                    vram[i] = (uint16_t)(low | (high << 8));
                }

                for (unsigned int i = 0; i < 16; ++i) {
                    bgPalette[GBA_BG_PALETTE_BANK * 16 + i] =
                        (i < ${assets.colors.size}) ? gba_asset_palette[i] : 0;
                }

                volatile uint16_t *screenblock =
                    vram + (GBA_BG_SCREEN_BASE_BLOCK * 1024);
                for (unsigned int i = 0; i < GBA_BG_MAP_ENTRY_COUNT; ++i) {
                    screenblock[i] = gba_asset_map[i];
                }

                *bg0cnt = (GBA_BG_SCREEN_BASE_BLOCK << 8) | (GBA_BG_SIZE_CODE << 14);
                *dispcnt = (uint16_t)(*dispcnt | (1u << 8));
            }
            """.trimIndent()
        )
    }

    private fun StringBuilder.appendArray(values: List<String>, valuesPerLine: Int = 12) {
        values.chunked(valuesPerLine).forEach { line ->
            append("    ")
            append(line.joinToString(", "))
            append(",\n")
        }
    }

    private fun manifest(assets: GbaPaletteResult, map: GbaTileMapResult): String = """
        {
          "format": "gba-studio-assets-v1",
          "graphics": {
            "sourceWidthPixels": ${assets.width},
            "sourceHeightPixels": ${assets.height},
            "tileWidth": 8,
            "tileHeight": 8,
            "bitDepth": 4,
            "tileOrder": "row-major-source",
            "tilesFile": "tiles.4bpp",
            "uniqueTileCount": ${map.uniqueTileCount},
            "paletteFile": "palette.pal",
            "paletteFormat": "BGR555-little-endian",
            "paletteEntries": ${assets.colors.size},
            "transparentIndex": 0
          },
          "background": {
            "mapFile": "background.map",
            "mapWidthTiles": ${map.widthTiles},
            "mapHeightTiles": ${map.heightTiles},
            "screenSizeCode": ${map.sizeCode},
            "entryFormat": "GBA-text-background-16bit",
            "screenblockOrder": "top-left, top-right, bottom-left, bottom-right",
            "paletteBank": ${map.paletteBank},
            "uniqueTilesFile": "tiles.4bpp"
          },
          "integration": {
            "cSource": "asset.c",
            "cHeader": "asset.h"
          }
        }
    """.trimIndent() + "\n"

    private fun readme(map: GbaTileMapResult): String = """
        GBA Studio asset bundle

        Files:
        - tiles.4bpp: deduplicated 8x8 4bpp graphics, 32 bytes per tile.
        - palette.pal: BGR555 little-endian palette, 2 bytes per entry; index 0 is transparent.
        - background.map: 16-bit GBA text-background entries in screenblock order.
        - asset.c / asset.h: ready-to-compile typed C arrays and declarations.
        - asset.json: machine-readable metadata.

        The map uses palette bank ${map.paletteBank}. Tile indices in background.map address tiles.4bpp.
        Call gba_asset_load_bg0() from asset.c to copy tiles, palette and map to VRAM,
        configure BG0 and enable it. This routine uses screenbase block 16 and character base 0.
        It does not configure display mode, blending, windowing, DMA, or game-specific logic.
    """.trimIndent() + "\n"
}
