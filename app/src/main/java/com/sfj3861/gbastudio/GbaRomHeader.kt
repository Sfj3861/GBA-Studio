package com.sfj3861.gbastudio

import java.util.Locale

/**
 * Constructs the 0xC0-byte GBA ROM header.
 * The optional Nintendo logo is caller-supplied and must be exactly 156 bytes.
 */
object GbaRomHeader {
    const val HEADER_SIZE = 0xC0
    const val LOGO_OFFSET = 0x04
    const val LOGO_SIZE = 0x9C
    const val TITLE_OFFSET = 0xA0
    const val GAME_CODE_OFFSET = 0xAC
    const val MAKER_CODE_OFFSET = 0xB0
    const val FIXED_VALUE_OFFSET = 0xB2
    const val VERSION_OFFSET = 0xBC
    const val COMPLEMENT_OFFSET = 0xBD

    fun write(
        image: ByteArray,
        entryPoint: Int,
        title: String,
        gameCode: String,
        makerCode: String,
        version: Int,
        nintendoLogo: ByteArray? = null
    ): List<String> {
        require(image.size >= HEADER_SIZE) { "La imagen no contiene espacio para la cabecera GBA." }
        require(entryPoint >= GbaLinker.ROM_BASE &&
            entryPoint.toLong() < GbaLinker.ROM_BASE.toLong() + GbaLinker.MAX_ROM_SIZE) {
            "El punto de entrada debe estar en la región ROM."
        }
        require(title.isNotBlank() && title.all { it.code in 0x20..0x7E }) {
            "El título debe contener caracteres ASCII imprimibles."
        }
        require(gameCode.length == 4 && gameCode.all(::isAsciiAlphaNumeric)) {
            "Game Code debe tener 4 caracteres ASCII alfanuméricos."
        }
        require(makerCode.length == 2 && makerCode.all(::isAsciiAlphaNumeric)) {
            "Maker Code debe tener 2 caracteres ASCII alfanuméricos."
        }
        require(version in 0..255) { "Versión fuera de rango." }
        require(nintendoLogo == null || nintendoLogo.size == LOGO_SIZE) {
            "El bloque de logo GBA debe medir exactamente 156 bytes."
        }

        image.fill(0, 0, HEADER_SIZE)
        val branchDisplacement = entryPoint.toLong() - (GbaLinker.ROM_BASE.toLong() + 8L)
        require((branchDisplacement and 3L) == 0L) { "El punto de entrada ARM debe estar alineado a 4 bytes." }
        val branchWords = branchDisplacement / 4L
        require(branchWords in -0x800000L..0x7FFFFFL) {
            "El punto de entrada está fuera del alcance de la instrucción B."
        }
        write32(image, 0, 0xEA000000.toInt() or (branchWords.toInt() and 0xFFFFFF))

        if (nintendoLogo != null) nintendoLogo.copyInto(image, LOGO_OFFSET)
        writeAscii(image, TITLE_OFFSET, 12, title.uppercase(Locale.ROOT).take(12))
        writeAscii(image, GAME_CODE_OFFSET, 4, gameCode.uppercase(Locale.ROOT))
        writeAscii(image, MAKER_CODE_OFFSET, 2, makerCode.uppercase(Locale.ROOT))

        // Fixed byte 0x96 at 0xB2; software version is at 0xBC.
        image[FIXED_VALUE_OFFSET] = 0x96.toByte()
        image[0xB3] = 0
        image[0xB4] = 0
        for (offset in 0xB5..0xBB) image[offset] = 0
        image[VERSION_OFFSET] = version.toByte()

        var sum = 0
        for (offset in TITLE_OFFSET..VERSION_OFFSET) {
            sum = (sum + (image[offset].toInt() and 0xFF)) and 0xFF
        }
        image[COMPLEMENT_OFFSET] = ((-(sum + 0x19)) and 0xFF).toByte()

        return if (nintendoLogo == null) {
            listOf("No se suministró el bloque de logo GBA de 156 bytes; la BIOS puede rechazar la imagen al arrancar.")
        } else emptyList()
    }

    fun hasValidComplement(image: ByteArray): Boolean {
        if (image.size < HEADER_SIZE) return false
        var sum = 0
        for (offset in TITLE_OFFSET..VERSION_OFFSET) {
            sum = (sum + (image[offset].toInt() and 0xFF)) and 0xFF
        }
        return ((sum + (image[COMPLEMENT_OFFSET].toInt() and 0xFF) + 0x19) and 0xFF) == 0
    }

    fun hasLogo(image: ByteArray): Boolean {
        if (image.size < HEADER_SIZE) return false
        return (LOGO_OFFSET until LOGO_OFFSET + LOGO_SIZE).any { image[it].toInt() != 0 }
    }

    private fun isAsciiAlphaNumeric(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'

    private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
        for (index in 0 until length) {
            target[offset + index] = (value.getOrNull(index)?.code ?: 0).toByte()
        }
    }

    private fun write32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }
}
