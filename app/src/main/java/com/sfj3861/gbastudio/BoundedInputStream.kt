package com.sfj3861.gbastudio

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads an input stream without allowing an untrusted document to allocate unlimited memory.
 * The stream is consumed but is not closed by this helper; callers retain ownership.
 */
fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "El límite de lectura no puede ser negativo." }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) {
            val single = read()
            if (single < 0) break
            if (total >= maxBytes) {
                throw IOException("El archivo supera el límite permitido de $maxBytes bytes.")
            }
            output.write(single)
            total++
            continue
        }
        if (count > maxBytes - total) {
            throw IOException("El archivo supera el límite permitido de $maxBytes bytes.")
        }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
