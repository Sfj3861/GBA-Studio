package com.sfj3861.gbastudio

import android.graphics.Bitmap
import android.graphics.Color

data class GbaPaletteResult(val colors: List<Int>, val tiles4bpp: ByteArray, val tileCount: Int, val warnings: List<String>)

object AssetStudio {
    const val TILE_SIZE = 8

    /** Converts an image to 4bpp tiled data and a 16-colour BGR555 palette. */
    fun convertTo4Bpp(bitmap: Bitmap): GbaPaletteResult {
        require(bitmap.width > 0 && bitmap.height > 0)
        val warnings = mutableListOf<String>()
        if (bitmap.width % TILE_SIZE != 0 || bitmap.height % TILE_SIZE != 0) {
            warnings += "La imagen no es múltiplo de 8; los píxeles sobrantes se recortan."
        }
        val width = bitmap.width / TILE_SIZE * TILE_SIZE
        val height = bitmap.height / TILE_SIZE * TILE_SIZE
        require(width > 0 && height > 0) { "La imagen debe medir al menos 8×8 píxeles." }
        val palette = linkedMapOf<Int, Int>()
        palette[0] = 0 // GBA sprite convention: palette index 0 is transparent.
        val indices = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val argb = bitmap.getPixel(x, y)
            val color = if (Color.alpha(argb) < 128) 0 else {
                val r = (Color.red(argb) shr 3) and 31
                val g = (Color.green(argb) shr 3) and 31
                val b = (Color.blue(argb) shr 3) and 31
                r or (g shl 5) or (b shl 10)
            }
            if (color != 0 && !palette.containsKey(color)) palette[color] = palette.size
            indices[y * width + x] = if (color == 0) 0 else palette.getValue(color)
        }
        if (palette.size > 16) warnings += "La imagen supera los 15 colores visibles disponibles cuando se reserva el índice 0 para transparencia; los colores posteriores se aproximan."
        val colors = palette.keys.take(16).toList()
        val tileBytes = ByteArray((width / 8) * (height / 8) * 32)
        var tileIndex = 0
        for (ty in 0 until height step 8) for (tx in 0 until width step 8) {
            for (py in 0 until 8) for (px in 0 until 8 step 2) {
                val a = indices[(ty + py) * width + tx + px].coerceAtMost(15)
                val b = indices[(ty + py) * width + tx + px + 1].coerceAtMost(15)
                tileBytes[tileIndex * 32 + py * 4 + px / 2] = (a or (b shl 4)).toByte()
            }
            tileIndex++
        }
        return GbaPaletteResult(colors, tileBytes, tileIndex, warnings)
    }

    fun toBgr555(color: Int): Int {
        val r = (Color.red(color) shr 3) and 31
        val g = (Color.green(color) shr 3) and 31
        val b = (Color.blue(color) shr 3) and 31
        return r or (g shl 5) or (b shl 10)
    }

    fun paletteBytes(colors: List<Int>): ByteArray {
        val out = ByteArray(colors.size * 2)
        colors.forEachIndexed { i, color ->
            val value = color and 0xFFFF
            out[i * 2] = value.toByte()
            out[i * 2 + 1] = (value ushr 8).toByte()
        }
        return out
    }
}
