package com.sfj3861.gbastudio

/**
 * Renders a GBA screenblock tilemap back to row-major ARGB pixels.
 * This validates the exported tile indices, screenblock order and palette-bank metadata.
 */
object GbaTileMapPreviewRenderer {
    fun renderArgbPixels(map: GbaTileMapResult, paletteBgr555: List<Int>): IntArray {
        require(map.widthTiles in 32..64 && map.heightTiles in 32..64 &&
            map.widthTiles % 32 == 0 && map.heightTiles % 32 == 0) {
            "El mapa debe estar formado por screenblocks de 32×32."
        }
        require(map.paletteBank in 0..15) { "Banco de paleta fuera de rango." }
        require(paletteBgr555.isNotEmpty() && paletteBgr555.size <= 16) {
            "La paleta 4bpp debe contener entre 1 y 16 entradas."
        }
        require(map.uniqueTileCount in 1..1024 &&
            map.uniqueTiles4bpp.size == map.uniqueTileCount * 32) {
            "Los gráficos únicos del mapa no son válidos."
        }
        require(map.entries.size == map.widthTiles * map.heightTiles &&
            map.bytes.size == map.entries.size * 2) {
            "El tamaño de la tabla de mapa no coincide con sus dimensiones."
        }
        val width = map.widthTiles * 8
        val height = map.heightTiles * 8
        require(width.toLong() * height <= 4L * 1024L * 1024L) {
            "La vista previa supera el límite de 4 megapíxeles."
        }

        val tileAtlas = GbaTileDecoder.decodeArgbPixels(
            8, map.uniqueTileCount * 8, map.uniqueTiles4bpp, paletteBgr555
        )
        val pixels = IntArray(width * height)
        val blockColumns = map.widthTiles / 32

        for (tileY in 0 until map.heightTiles) {
            for (tileX in 0 until map.widthTiles) {
                val screenblock = (tileY / 32) * blockColumns + (tileX / 32)
                val localIndex = (tileY % 32) * 32 + (tileX % 32)
                val storageIndex = screenblock * 1024 + localIndex
                val entry = map.entries[storageIndex]
                val paletteBank = (entry ushr 12) and 15
                require(paletteBank == map.paletteBank) {
                    "Una entrada del mapa utiliza un banco de paleta diferente."
                }
                val tileIndex = entry and 0x03FF
                require(tileIndex < map.uniqueTileCount) {
                    "Una entrada del mapa referencia un tile inexistente."
                }
                for (py in 0 until 8) {
                    val sourceOffset = (tileIndex * 8 + py) * 8
                    val targetOffset = (tileY * 8 + py) * width + tileX * 8
                    for (px in 0 until 8) {
                        pixels[targetOffset + px] = tileAtlas[sourceOffset + px]
                    }
                }
            }
        }
        return pixels
    }
}
