package com.sfj3861.gbastudio.rom

/**
 * Parsed metadata and structural checks from the fixed 0xC0-byte GBA ROM header.
 * Logo presence is only a byte-range check; this parser does not verify the BIOS logo checksum.
 */
data class GbaRomHeader(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val softwareVersion: Int,
    val storedChecksum: Int,
    val expectedChecksum: Int,
    val fixedValue: Int,
    val hasLogoData: Boolean,
    val entryPointInstruction: Int,
    val entryPointAddress: Long?
) {
    val checksumValid: Boolean get() = storedChecksum == expectedChecksum
    val fixedValueValid: Boolean get() = fixedValue == 0x96
    val hasEntryBranch: Boolean get() = entryPointAddress != null
    val metadataFieldsValid: Boolean get() = checksumValid && fixedValueValid
}

object GbaRomHeaderParser {
    const val HEADER_SIZE = 0xC0
    private const val ROM_BASE = 0x08000000L

    fun parse(bytes: ByteArray): GbaRomHeader {
        require(bytes.size >= HEADER_SIZE) {
            "El archivo es demasiado pequeño: hacen falta al menos 192 bytes."
        }
        val instruction = read32(bytes, 0)
        val branchTarget = decodeEntryBranch(instruction)
        return GbaRomHeader(
            title = decodeAscii(bytes, 0xA0, 12),
            gameCode = decodeAscii(bytes, 0xAC, 4),
            makerCode = decodeAscii(bytes, 0xB0, 2),
            softwareVersion = bytes[0xBC].toInt() and 0xFF,
            storedChecksum = bytes[0xBD].toInt() and 0xFF,
            expectedChecksum = calculateChecksum(bytes),
            fixedValue = bytes[0xB2].toInt() and 0xFF,
            hasLogoData = (0x04 until 0xA0).any { bytes[it].toInt() != 0 },
            entryPointInstruction = instruction,
            entryPointAddress = branchTarget
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

    private fun decodeEntryBranch(instruction: Int): Long? {
        if (((instruction ushr 28) and 15) != 14) return null
        if ((instruction and 0x0E000000) != 0x0A000000) return null
        val immediate = instruction and 0xFFFFFF
        val signedOffset = (immediate shl 8) shr 6
        return ROM_BASE + 8L + signedOffset.toLong()
    }

    private fun read32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 3].toInt() and 255) shl 24)

    private fun decodeAscii(bytes: ByteArray, offset: Int, length: Int): String {
        return buildString(length) {
            for (index in offset until offset + length) {
                val value = bytes[index].toInt() and 0xFF
                append(if (value in 0x20..0x7E) value.toChar() else ' ')
            }
        }.trim()
    }
}
