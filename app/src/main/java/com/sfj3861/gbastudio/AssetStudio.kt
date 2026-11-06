package com.sfj3861.gbastudio

import android.graphics.Bitmap
import android.graphics.Color

data class GbaPaletteResult(
    val colors: List<Int>,
    val tiles4bpp: ByteArray,
    val tileCount: Int,
    val warnings: List<String>,
    val sourceColorCount: Int = (colors.size - 1).coerceAtLeast(0)
)

object AssetStudio {
    const val TILE_SIZE = 8
    const val MAX_PIXELS = 4 * 1024 * 1024

    /** Converts an image to 4bpp tiled data and a BGR555 palette with transparent index 0. */
    fun convertTo4Bpp(bitmap: Bitmap): GbaPaletteResult {
        require(bitmap.width > 0 && bitmap.height > 0) { "La imagen está vacía." }
        val warnings = mutableListOf<String>()
        if (bitmap.width % TILE_SIZE != 0 || bitmap.height % TILE_SIZE != 0) {
            warnings += "La imagen no es múltiplo de 8; los píxeles sobrantes se recortan."
        }
        val width = bitmap.width / TILE_SIZE * TILE_SIZE
        val height = bitmap.height / TILE_SIZE * TILE_SIZE
        require(width > 0 && height > 0) { "La imagen debe medir al menos 8×8 píxeles." }
        require(width.toLong() * height <= MAX_PIXELS) { "La imagen recortada supera el límite de 16 megapíxeles." }

        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val quantized = GbaPaletteQuantizer.quantize(argb)
        if (quantized.sourceColorCount > 15) {
            warnings += "Se han encontrado ${quantized.sourceColorCount} colores visibles; se han reducido a 15 mediante proximidad de color."
        }

        val tilesWide = width / TILE_SIZE
        val tilesHigh = height / TILE_SIZE
        val tileCount = tilesWide * tilesHigh
        val tileBytes = ByteArray(tileCount * 32)
        var tileIndex = 0
        for (ty in 0 until height step TILE_SIZE) {
            for (tx in 0 until width step TILE_SIZE) {
                for (py in 0 until TILE_SIZE) {
                    for (px in 0 until TILE_SIZE step 2) {
                        val a = quantized.indices[(ty + py) * width + tx + px]
                        val b = quantized.indices[(ty + py) * width + tx + px + 1]
                        tileBytes[tileIndex * 32 + py * 4 + px / 2] = (a or (b shl 4)).toByte()
                    }
                }
                tileIndex++
            }
        }
        return GbaPaletteResult(quantized.palette, tileBytes, tileCount, warnings, quantized.sourceColorCount)
    }

    fun toBgr555(color: Int): Int {
        val r = (Color.red(color) shr 3) and 31
        val g = (Color.green(color) shr 3) and 31
        val b = (Color.blue(color) shr 3) and 31
        return r or (g shl 5) or (b shl 10)
    }

    fun paletteBytes(colors: List<Int>): ByteArray {
        require(colors.size <= 16) { "Una paleta 4bpp no puede superar 16 entradas." }
        val out = ByteArray(colors.size * 2)
        colors.forEachIndexed { i, color ->
            val value = color and 0x7FFF
            out[i * 2] = value.toByte()
            out[i * 2 + 1] = (value ushr 8).toByte()
        }
        return out
    }
}
