package com.sfj3861.gbastudio

data class GbaTileMapResult(
    val widthTiles: Int,
    val heightTiles: Int,
    val sizeCode: Int,
    /** Entries are stored in GBA screenblock order, not a flat visual row-major matrix. */
    val entries: IntArray,
    val bytes: ByteArray
)

/**
 * Builds a GBA text-background map for 4bpp tiles.
 * GBA screenblocks are 32x32 entries and 64x32 / 32x64 / 64x64 maps
 * are stored as multiple screenblocks, not one row-major rectangle.
 */
object GbaTileMapBuilder {
    const val SCREENBLOCK_SIDE = 32
    const val MAX_DIMENSION_TILES = 64
    const val MAX_TILE_INDEX = 1023

    fun build(assets: GbaPaletteResult, paletteBank: Int = 0): GbaTileMapResult {
        require(paletteBank in 0..15) { "La paleta de fondo debe estar entre 0 y 15." }
        require(assets.width > 0 && assets.height > 0 &&
            assets.width % 8 == 0 && assets.height % 8 == 0) {
            "Las dimensiones del recurso deben ser múltiplos de 8."
        }
        val sourceWidth = assets.width / 8
        val sourceHeight = assets.height / 8
        require(sourceWidth <= MAX_DIMENSION_TILES && sourceHeight <= MAX_DIMENSION_TILES) {
            "Un mapa GBA no puede superar 64×64 tiles."
        }
        val tileCount = sourceWidth.toLong() * sourceHeight.toLong()
        require(tileCount in 1..(MAX_TILE_INDEX + 1).toLong()) {
            "El mapa necesita más de 1024 índices de tile."
        }

        val mapWidth = if (sourceWidth <= 32) 32 else 64
        val mapHeight = if (sourceHeight <= 32) 32 else 64
        val sizeCode = when {
            mapWidth == 32 && mapHeight == 32 -> 0
            mapWidth == 64 && mapHeight == 32 -> 1
            mapWidth == 32 && mapHeight == 64 -> 2
            else -> 3
        }
        val entries = IntArray(mapWidth * mapHeight)
        val blockColumns = mapWidth / SCREENBLOCK_SIDE

        for (y in 0 until sourceHeight) {
            for (x in 0 until sourceWidth) {
                val tileIndex = y * sourceWidth + x
                val screenblock = (y / SCREENBLOCK_SIDE) * blockColumns + (x / SCREENBLOCK_SIDE)
                val localIndex = (y % SCREENBLOCK_SIDE) * SCREENBLOCK_SIDE + (x % SCREENBLOCK_SIDE)
                val storageIndex = screenblock * SCREENBLOCK_SIDE * SCREENBLOCK_SIDE + localIndex
                entries[storageIndex] = tileIndex or (paletteBank shl 12)
            }
        }

        val bytes = ByteArray(entries.size * 2)
        entries.forEachIndexed { index, entry ->
            bytes[index * 2] = entry.toByte()
            bytes[index * 2 + 1] = (entry ushr 8).toByte()
        }
        return GbaTileMapResult(mapWidth, mapHeight, sizeCode, entries, bytes)
    }
}
