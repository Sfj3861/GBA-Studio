package com.sfj3861.gbastudio

data class GbaTileMapResult(
    val widthTiles: Int,
    val heightTiles: Int,
    val sizeCode: Int,
    val paletteBank: Int,
    val uniqueTileCount: Int,
    val uniqueTiles4bpp: ByteArray,
    /** Entries are stored in GBA screenblock order, not a flat visual row-major matrix. */
    val entries: IntArray,
    val bytes: ByteArray
)

/**
 * Builds a GBA text-background map for 4bpp tiles.
 * Identical 8x8 tiles are deduplicated so maps may contain up to 4096 positions
 * while the referenced character data stays inside the 1024-tile 4bpp BG limit.
 */
object GbaTileMapBuilder {
    const val SCREENBLOCK_SIDE = 32
    const val MAX_DIMENSION_TILES = 64
    const val MAX_TILE_INDEX = 1023
    private const val TILE_BYTES = 32

    private class TileKey(private val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is TileKey && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }

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
        val tileCountLong = sourceWidth.toLong() * sourceHeight.toLong()
        require(tileCountLong in 1..(MAX_DIMENSION_TILES * MAX_DIMENSION_TILES).toLong()) {
            "El mapa debe contener entre 1 y 4096 posiciones."
        }
        val tileCount = tileCountLong.toInt()
        require(assets.tiles4bpp.size.toLong() >= tileCountLong * TILE_BYTES) {
            "Los datos 4bpp no contienen todos los tiles del mapa."
        }

        val tileIndices = IntArray(tileCount)
        val uniqueByKey = LinkedHashMap<TileKey, Int>()
        val uniqueTiles = ArrayList<ByteArray>()
        for (index in 0 until tileCount) {
            val offset = index * TILE_BYTES
            val tile = assets.tiles4bpp.copyOfRange(offset, offset + TILE_BYTES)
            val key = TileKey(tile)
            val existing = uniqueByKey[key]
            if (existing != null) {
                tileIndices[index] = existing
            } else {
                require(uniqueTiles.size <= MAX_TILE_INDEX) {
                    "El recurso contiene más de 1024 tiles únicos; reduce la paleta, el tamaño o repite gráficos."
                }
                val newIndex = uniqueTiles.size
                uniqueByKey[key] = newIndex
                uniqueTiles += tile
                tileIndices[index] = newIndex
            }
        }

        val mapWidth = if (sourceWidth <= 32) 32 else 64
        val mapHeight = if (sourceHeight <= 32) 32 else 64
        val sizeCode = when {
            mapWidth == 32 && mapHeight == 32 -> 0
            mapWidth == 64 && mapHeight == 32 -> 1
            mapWidth == 32 && mapHeight == 64 -> 2
            else -> 3
        }
        // Empty map cells still carry the selected palette bank (tile index zero).
        val entries = IntArray(mapWidth * mapHeight) { paletteBank shl 12 }
        val blockColumns = mapWidth / SCREENBLOCK_SIDE

        for (y in 0 until sourceHeight) {
            for (x in 0 until sourceWidth) {
                val sourceIndex = y * sourceWidth + x
                val screenblock = (y / SCREENBLOCK_SIDE) * blockColumns + (x / SCREENBLOCK_SIDE)
                val localIndex = (y % SCREENBLOCK_SIDE) * SCREENBLOCK_SIDE + (x % SCREENBLOCK_SIDE)
                val storageIndex = screenblock * SCREENBLOCK_SIDE * SCREENBLOCK_SIDE + localIndex
                entries[storageIndex] = tileIndices[sourceIndex] or (paletteBank shl 12)
            }
        }

        val uniqueData = ByteArray(uniqueTiles.size * TILE_BYTES)
        uniqueTiles.forEachIndexed { index, tile ->
            tile.copyInto(uniqueData, index * TILE_BYTES)
        }
        val bytes = ByteArray(entries.size * 2)
        entries.forEachIndexed { index, entry ->
            bytes[index * 2] = entry.toByte()
            bytes[index * 2 + 1] = (entry ushr 8).toByte()
        }
        return GbaTileMapResult(mapWidth, mapHeight, sizeCode, paletteBank, uniqueTiles.size, uniqueData, entries, bytes)
    }
}
