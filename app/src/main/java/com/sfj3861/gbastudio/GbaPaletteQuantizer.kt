package com.sfj3861.gbastudio

data class QuantizedGbaPixels(
    val palette: List<Int>,
    val indices: IntArray,
    val sourceColorCount: Int
)

/**
 * Deterministic 4bpp palette reduction. Palette index 0 is reserved for transparency;
 * up to 15 opaque BGR555 colors are selected by frequency and remaining colors are
 * mapped to their nearest selected color.
 */
object GbaPaletteQuantizer {
    private const val COLOR_COUNT = 1 shl 15
    private const val CHANNEL_MASK = 31

    fun quantize(argbPixels: IntArray): QuantizedGbaPixels {
        val histogram = IntArray(COLOR_COUNT)
        val source = IntArray(argbPixels.size) { -1 }
        for (i in argbPixels.indices) {
            val argb = argbPixels[i]
            if ((argb ushr 24) >= 128) {
                val color = toBgr555(argb)
                source[i] = color
                histogram[color]++
            }
        }

        val uniqueColors = histogram.count { it > 0 }
        val selected = histogram.indices
            .asSequence()
            .filter { histogram[it] > 0 }
            .sortedWith(compareByDescending<Int> { histogram[it] }.thenBy { it })
            .take(15)
            .toList()

        val palette = listOf(0) + selected
        val indices = source
        if (selected.isNotEmpty()) {
            for (i in source.indices) {
                val color = source[i]
                if (color < 0) {
                    indices[i] = 0
                } else {
                    var bestIndex = 0
                    var bestDistance = Int.MAX_VALUE
                    for (candidateIndex in selected.indices) {
                        val candidate = selected[candidateIndex]
                        val dr = (color and CHANNEL_MASK) - (candidate and CHANNEL_MASK)
                        val dg = ((color ushr 5) and CHANNEL_MASK) - ((candidate ushr 5) and CHANNEL_MASK)
                        val db = ((color ushr 10) and CHANNEL_MASK) - ((candidate ushr 10) and CHANNEL_MASK)
                        val distance = dr * dr + dg * dg + db * db
                        if (distance < bestDistance) {
                            bestDistance = distance
                            bestIndex = candidateIndex + 1
                        }
                    }
                    indices[i] = bestIndex
                }
            }
        } else {
            indices.fill(0)
        }
        return QuantizedGbaPixels(palette, indices, uniqueColors)
    }

    fun toBgr555(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((r ushr 3) and 31) or (((g ushr 3) and 31) shl 5) or (((b ushr 3) and 31) shl 10)
    }
}
