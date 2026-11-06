package com.sfj3861.gbastudio

data class GbaRelocation(
    val offset: Int,
    val symbol: String,
    val type: Type,
    val addend: Int = 0
) {
    enum class Type { ABS32, REL32, ARM_BRANCH24, THUMB_BRANCH11 }
}

data class GbaSection(
    val name: String,
    val data: ByteArray,
    val alignment: Int = 4,
    val region: Region = Region.ROM,
    val relocations: List<GbaRelocation> = emptyList()
) {
    enum class Region { ROM, EWRAM, IWRAM }
}

data class GbaSymbol(val name: String, val sectionName: String, val offset: Int)
data class LinkedSection(val name: String, val address: Int, val size: Int, val fileOffset: Int)
data class GbaLinkResult(val image: ByteArray, val sections: List<LinkedSection>, val symbols: Map<String, Int>, val warnings: List<String>)

object GbaLinker {
    const val ROM_BASE = 0x08000000
    const val EWRAM_BASE = 0x02000000
    const val IWRAM_BASE = 0x03000000
    private const val HEADER_SIZE = 0xC0
    private const val MAX_ROM_SIZE = 32 * 1024 * 1024

    /**
     * Links raw sections with named symbols and a small relocation set.
     * This is not an ELF linker and does not provide startup/runtime code.
     */
    fun linkRawSections(
        sections: List<GbaSection>,
        title: String,
        gameCode: String = "GBA0",
        makerCode: String = "00",
        version: Int = 0,
        symbols: List<GbaSymbol> = emptyList()
    ): GbaLinkResult {
        require(sections.isNotEmpty()) { "Añade al menos una sección." }
        require(title.isNotBlank()) { "El título no puede estar vacío." }
        require(gameCode.length == 4 && gameCode.all { it.isLetterOrDigit() }) { "Game Code debe tener 4 caracteres." }
        require(makerCode.length == 2 && makerCode.all { it.isLetterOrDigit() }) { "Maker Code debe tener 2 caracteres." }
        require(version in 0..255) { "Versión fuera de rango." }
        require(sections.map { it.name }.distinct().size == sections.size) { "Los nombres de sección deben ser únicos." }
        require(symbols.map { it.name }.distinct().size == symbols.size) { "Hay símbolos duplicados." }

        var romCursor = HEADER_SIZE
        var ewramCursor = EWRAM_BASE
        var iwramCursor = IWRAM_BASE
        val placements = mutableListOf<LinkedSection>()
        sections.forEach { section ->
            require(section.alignment > 0 && section.alignment <= 4096 && (section.alignment and (section.alignment - 1)) == 0) {
                "Alineación inválida en ${section.name}."
            }
            romCursor = align(romCursor, section.alignment)
            val address = when (section.region) {
                GbaSection.Region.ROM -> ROM_BASE + romCursor
                GbaSection.Region.EWRAM -> {
                    ewramCursor = align(ewramCursor, section.alignment)
                    ewramCursor.also { ewramCursor += section.data.size }
                }
                GbaSection.Region.IWRAM -> {
                    iwramCursor = align(iwramCursor, section.alignment)
                    iwramCursor.also { iwramCursor += section.data.size }
                }
            }
            placements += LinkedSection(section.name, address, section.data.size, romCursor)
            romCursor += section.data.size
        }
        require(ewramCursor <= EWRAM_BASE + 256 * 1024) { "Las secciones superan la EWRAM disponible." }
        require(iwramCursor <= IWRAM_BASE + 32 * 1024) { "Las secciones superan la IWRAM disponible." }
        require(romCursor <= MAX_ROM_SIZE) { "La imagen supera el límite de 32 MiB." }

        val sectionByName = placements.associateBy { it.name }
        val symbolAddresses = symbols.associate { symbol ->
            val section = sectionByName[symbol.sectionName] ?: error("Sección no encontrada para símbolo ${symbol.name}.")
            require(symbol.offset in 0..section.size) { "Offset fuera de rango para símbolo ${symbol.name}." }
            symbol.name to (section.address + symbol.offset)
        }
        val image = ByteArray(align(romCursor, 4))
        sections.forEachIndexed { index, section ->
            val placement = placements[index]
            val relocated = section.data.copyOf()
            section.relocations.forEach { relocation ->
                require(relocation.offset >= 0 && relocation.offset + 4 <= relocated.size) {
                    "Relocación fuera de rango en ${section.name}."
                }
                val target = symbolAddresses[relocation.symbol]
                    ?: error("Símbolo no resuelto: ${relocation.symbol}.")
                val place = placement.address + relocation.offset
                when (relocation.type) {
                    GbaRelocation.Type.ABS32 -> write32(relocated, relocation.offset, target + relocation.addend)
                    GbaRelocation.Type.REL32 -> write32(relocated, relocation.offset, target + relocation.addend - place)
                    GbaRelocation.Type.ARM_BRANCH24 -> {
                        val instruction = read32(relocated, relocation.offset)
                        require(instruction ushr 24 == 0xEA || instruction ushr 24 == 0xEB) {
                            "ARM_BRANCH24 requiere una instrucción B/BL."
                        }
                        val displacement = target + relocation.addend - (place + 8)
                        require((displacement and 3) == 0 && displacement in -0x02000000..0x01FFFFFC) {
                            "Destino ARM branch fuera de rango o desalineado."
                        }
                        write32(relocated, relocation.offset, (instruction and -0x1000000) or ((displacement shr 2) and 0xFFFFFF))
                    }
                    GbaRelocation.Type.THUMB_BRANCH11 -> {
                        require(relocation.offset + 2 <= relocated.size) { "Relocación Thumb fuera de rango." }
                        val instruction = read16(relocated, relocation.offset)
                        require(instruction and 0xF800 == 0xE000) { "THUMB_BRANCH11 requiere una instrucción B." }
                        val displacement = target + relocation.addend - (place + 4)
                        require((displacement and 1) == 0 && displacement in -2048..2046) {
                            "Destino Thumb branch fuera de rango o desalineado."
                        }
                        write16(relocated, relocation.offset, (instruction and 0xF800) or ((displacement shr 1) and 0x7FF))
                    }
                }
            }
            relocated.copyInto(image, placement.fileOffset)
        }
        writeAscii(image, 0xA0, 12, title.uppercase().take(12))
        writeAscii(image, 0xAC, 4, gameCode.uppercase())
        writeAscii(image, 0xB0, 2, makerCode.uppercase())
        image[0xB2] = version.toByte()
        var sum = 0
        for (i in 0xA0..0xBC) sum += image[i].toInt() and 0xFF
        image[0xBD] = ((-(sum + 0x19)) and 0xFF).toByte()
        return GbaLinkResult(
            image,
            placements,
            symbolAddresses,
            listOf(
                "Linker experimental de secciones raw: admite símbolos y relocaciones ABS32, REL32, ARM_BRANCH24 y THUMB_BRANCH11; no lee ELF.",
                "Las secciones EWRAM/IWRAM se guardan con carga en ROM; todavía falta el startup que las copia a RAM.",
                "No se incluye el bloque de logo de Nintendo ni runtime: no se garantiza el arranque en BIOS/hardware."
            )
        )
    }

    private fun align(value: Int, alignment: Int) = (value + alignment - 1) and -alignment
    private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
        for (i in 0 until length) target[offset + i] = (value.getOrNull(i)?.code ?: 0).toByte()
    }
    private fun read16(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun read32(bytes: ByteArray, offset: Int) = read16(bytes, offset) or (read16(bytes, offset + 2) shl 16)
    private fun write16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun write32(bytes: ByteArray, offset: Int, value: Int) {
        write16(bytes, offset, value)
        write16(bytes, offset + 2, value ushr 16)
    }
}
