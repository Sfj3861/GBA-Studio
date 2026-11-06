package com.sfj3861.gbastudio

/**
 * Decodes the row-major 8x8 tile stream produced by Asset Studio into ARGB pixels.
 * Palette entry zero is transparent; other entries are opaque BGR555 colors.
 */
object GbaTileDecoder {
    fun decodeArgbPixels(width: Int, height: Int, tiles4bpp: ByteArray, paletteBgr555: List<Int>): IntArray {
        require(width > 0 && height > 0 && width % 8 == 0 && height % 8 == 0) {
            "Las dimensiones deben ser múltiplos de 8."
        }
        require(paletteBgr555.isNotEmpty() && paletteBgr555.size <= 16) {
            "La paleta 4bpp debe contener entre 1 y 16 entradas."
        }
        val tileCount = (width / 8) * (height / 8)
        val requiredBytes = tileCount.toLong() * 32L
        require(requiredBytes <= tiles4bpp.size.toLong()) {
            "Los datos de tiles están incompletos."
        }
        val colors = IntArray(paletteBgr555.size) { index ->
            if (index == 0) 0 else bgr555ToArgb(paletteBgr555[index])
        }
        val pixels = IntArray(width * height)
        var tileIndex = 0
        for (tileY in 0 until height step 8) {
            for (tileX in 0 until width step 8) {
                val tileOffset = tileIndex * 32
                for (row in 0 until 8) {
                    for (pair in 0 until 4) {
                        val packed = tiles4bpp[tileOffset + row * 4 + pair].toInt() and 255
                        val first = packed and 15
                        val second = (packed ushr 4) and 15
                        require(first < colors.size && second < colors.size) {
                            "Un tile referencia un índice fuera de la paleta."
                        }
                        val x = tileX + pair * 2
                        val y = tileY + row
                        pixels[y * width + x] = colors[first]
                        pixels[y * width + x + 1] = colors[second]
                    }
                }
                tileIndex++
            }
        }
        return pixels
    }

    private fun bgr555ToArgb(value: Int): Int {
        val red5 = value and 31
        val green5 = (value ushr 5) and 31
        val blue5 = (value ushr 10) and 31
        val red = (red5 * 255 + 15) / 31
        val green = (green5 * 255 + 15) / 31
        val blue = (blue5 * 255 + 15) / 31
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
