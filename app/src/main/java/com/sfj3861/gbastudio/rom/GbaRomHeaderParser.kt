package com.sfj3861.gbastudio.rom

/**
 * Parsed fields from the fixed 0xC0-byte Game Boy Advance ROM header.
 * This parser only reads metadata; it does not modify or execute the ROM.
 */
data class GbaRomHeader(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val softwareVersion: Int,
    val storedChecksum: Int,
    val expectedChecksum: Int
) {
    val checksumValid: Boolean get() = storedChecksum == expectedChecksum
}

object GbaRomHeaderParser {
    const val HEADER_SIZE = 0xC0

    fun parse(bytes: ByteArray): GbaRomHeader {
        require(bytes.size >= HEADER_SIZE) {
            "El archivo es demasiado pequeño: hacen falta al menos 192 bytes."
        }

        val expectedChecksum = calculateChecksum(bytes)
        return GbaRomHeader(
            title = decodeAscii(bytes, 0xA0, 12),
            gameCode = decodeAscii(bytes, 0xAC, 4),
            makerCode = decodeAscii(bytes, 0xB0, 2),
            softwareVersion = bytes[0xBC].toInt() and 0xFF,
            storedChecksum = bytes[0xBD].toInt() and 0xFF,
            expectedChecksum = expectedChecksum
        )
    }

    fun calculateChecksum(bytes: ByteArray): Int {
        require(bytes.size >= HEADER_SIZE) {
            "La cabecera debe contener al menos 192 bytes."
        }
        var sum = 0
        for (index in 0xA0..0xBC) {
            sum = (sum + (bytes[index].toInt() and 0xFF)) and 0xFF
        }
        return (-(sum + 0x19)) and 0xFF
    }

    private fun decodeAscii(bytes: ByteArray, offset: Int, length: Int): String {
        return buildString(length) {
            for (index in offset until offset + length) {
                val value = bytes[index].toInt() and 0xFF
                append(if (value in 0x20..0x7E) value.toChar() else ' ')
            }
        }.trim()
    }
}
