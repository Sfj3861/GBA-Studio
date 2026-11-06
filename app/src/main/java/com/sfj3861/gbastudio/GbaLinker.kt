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
data class GbaLinkResult(
    val image: ByteArray,
    val sections: List<LinkedSection>,
    val symbols: Map<String, Int>,
    val warnings: List<String>
)

object GbaLinker {
    const val ROM_BASE = 0x08000000
    const val EWRAM_BASE = 0x02000000
    const val IWRAM_BASE = 0x03000000
    const val HEADER_SIZE = 0xC0
    const val MAX_ROM_SIZE = 32 * 1024 * 1024
    private const val EWRAM_SIZE = 256 * 1024
    private const val IWRAM_SIZE = 32 * 1024

    /**
     * Links raw sections with named symbols and a small relocation set.
     * This is not an ELF linker and does not provide startup/runtime code.
     * EWRAM/IWRAM sections are stored in ROM as load data; startup code must copy
     * them to their runtime addresses. The BIOS logo block is optional and caller-supplied.
     */
    fun linkRawSections(
        sections: List<GbaSection>,
        title: String,
        gameCode: String = "GBA0",
        makerCode: String = "00",
        version: Int = 0,
        symbols: List<GbaSymbol> = emptyList(),
        nintendoLogo: ByteArray? = null
    ): GbaLinkResult {
        require(sections.isNotEmpty()) { "Añade al menos una sección." }
        require(sections.any { it.region == GbaSection.Region.ROM && it.data.isNotEmpty() }) {
            "Añade una sección ROM no vacía para contener el punto de entrada."
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
        require(nintendoLogo == null || nintendoLogo.size == GbaRomHeader.LOGO_SIZE) {
            "El bloque de logo GBA debe medir exactamente 156 bytes."
        }
        require(sections.all { it.name.isNotBlank() }) { "Los nombres de sección no pueden estar vacíos." }
        require(sections.map { it.name }.distinct().size == sections.size) {
            "Los nombres de sección deben ser únicos."
        }
        require(symbols.all { it.name.isNotBlank() && it.sectionName.isNotBlank() }) {
            "Los nombres de los símbolos y sus secciones no pueden estar vacíos."
        }
        require(symbols.map { it.name }.distinct().size == symbols.size) { "Hay símbolos duplicados." }

        var romCursor = HEADER_SIZE
        var ewramCursor = EWRAM_BASE
        var iwramCursor = IWRAM_BASE
        val placements = mutableListOf<LinkedSection>()

        sections.forEach { section ->
            require(section.alignment in 1..4096 &&
                (section.alignment and (section.alignment - 1)) == 0) {
                "Alineación inválida en ${section.name}."
            }
            romCursor = align(romCursor, section.alignment)
            val nextFileOffset = romCursor.toLong() + section.data.size.toLong()
            require(nextFileOffset <= MAX_ROM_SIZE) { "La imagen supera el límite de 32 MiB." }

            val address = when (section.region) {
                GbaSection.Region.ROM -> ROM_BASE + romCursor
                GbaSection.Region.EWRAM -> {
                    ewramCursor = align(ewramCursor, section.alignment)
                    val next = ewramCursor.toLong() + section.data.size.toLong()
                    require(next <= EWRAM_BASE.toLong() + EWRAM_SIZE) {
                        "Las secciones superan la EWRAM disponible."
                    }
                    ewramCursor.also { ewramCursor = next.toInt() }
                }
                GbaSection.Region.IWRAM -> {
                    iwramCursor = align(iwramCursor, section.alignment)
                    val next = iwramCursor.toLong() + section.data.size.toLong()
                    require(next <= IWRAM_BASE.toLong() + IWRAM_SIZE) {
                        "Las secciones superan la IWRAM disponible."
                    }
                    iwramCursor.also { iwramCursor = next.toInt() }
                }
            }
            placements += LinkedSection(section.name, address, section.data.size, romCursor)
            romCursor = nextFileOffset.toInt()
        }

        val sectionByName = placements.associateBy { it.name }
        val symbolAddresses = symbols.associate { symbol ->
            val section = sectionByName[symbol.sectionName]
                ?: error("Sección no encontrada para símbolo ${symbol.name}.")
            require(symbol.offset in 0..section.size) {
                "Offset fuera de rango para símbolo ${symbol.name}."
            }
            val address = section.address.toLong() + symbol.offset.toLong()
            require(address in 0..0xFFFFFFFFL) { "Dirección fuera de rango para símbolo ${symbol.name}." }
            symbol.name to address.toInt()
        }

        val image = ByteArray(align(romCursor, 4))
        sections.forEachIndexed { index, section ->
            val placement = placements[index]
            val relocated = section.data.copyOf()
            validateRelocations(section)
            section.relocations.forEach { relocation ->
                val width = relocationWidth(relocation.type)
                require(relocation.offset >= 0 &&
                    relocation.offset.toLong() + width <= relocated.size.toLong()) {
                    "Relocación fuera de rango en ${section.name}."
                }
                val target = symbolAddresses[relocation.symbol]
                    ?: error("Símbolo no resuelto: ${relocation.symbol}.")
                val targetWithAddend = target.toLong() + relocation.addend.toLong()
                require(targetWithAddend in 0..0xFFFFFFFFL) {
                    "Destino de relocación fuera del espacio de direcciones."
                }
                val place = placement.address.toLong() + relocation.offset.toLong()

                when (relocation.type) {
                    GbaRelocation.Type.ABS32 -> {
                        require((place and 3L) == 0L) { "ABS32 requiere alineación de 4 bytes." }
                        write32(relocated, relocation.offset, targetWithAddend.toInt())
                    }
                    GbaRelocation.Type.REL32 -> {
                        require((place and 3L) == 0L) { "REL32 requiere alineación de 4 bytes." }
                        val displacement = targetWithAddend - place
                        require(displacement in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                            "Desplazamiento REL32 fuera de rango."
                        }
                        write32(relocated, relocation.offset, displacement.toInt())
                    }
                    GbaRelocation.Type.ARM_BRANCH24 -> {
                        require((place and 3L) == 0L) { "ARM_BRANCH24 requiere alineación de 4 bytes." }
                        val instruction = read32(relocated, relocation.offset)
                        require((instruction and 0x0E000000) == 0x0A000000) {
                            "ARM_BRANCH24 requiere una instrucción B/BL."
                        }
                        val displacement = targetWithAddend - (place + 8L)
                        require((displacement and 3L) == 0L &&
                            displacement in -0x02000000L..0x01FFFFFCL) {
                            "Destino ARM branch fuera de rango o desalineado."
                        }
                        write32(
                            relocated,
                            relocation.offset,
                            (instruction and -0x1000000) or ((displacement.toInt() shr 2) and 0xFFFFFF)
                        )
                    }
                    GbaRelocation.Type.THUMB_BRANCH11 -> {
                        require((place and 1L) == 0L) { "THUMB_BRANCH11 requiere alineación de 2 bytes." }
                        val instruction = read16(relocated, relocation.offset)
                        require((instruction and 0xF800) == 0xE000) {
                            "THUMB_BRANCH11 requiere una instrucción B."
                        }
                        val displacement = targetWithAddend - (place + 4L)
                        require((displacement and 1L) == 0L && displacement in -2048L..2046L) {
                            "Destino Thumb branch fuera de rango o desalineado."
                        }
                        write16(
                            relocated,
                            relocation.offset,
                            (instruction and 0xF800) or ((displacement.toInt() shr 1) and 0x7FF)
                        )
                    }
                }
            }
            relocated.copyInto(image, placement.fileOffset)
        }

        val entrySectionIndex = sections.indices.first { index ->
            sections[index].region == GbaSection.Region.ROM && sections[index].data.isNotEmpty()
        }
        val warnings = GbaRomHeader.write(
            image = image,
            entryPoint = placements[entrySectionIndex].address,
            title = title,
            gameCode = gameCode,
            makerCode = makerCode,
            version = version,
            nintendoLogo = nintendoLogo
        ).toMutableList()
        if (nintendoLogo != null) warnings += "El bloque de logo suministrado se copia sin verificarlo contra la referencia de BIOS."
        warnings += "Linker experimental: no parsea ELF ni implementa el ABI completo de relocaciones ARM."
        if (sections.any { it.region != GbaSection.Region.ROM }) {
            warnings += "Las secciones EWRAM/IWRAM se guardan como datos de carga en ROM; falta startup para copiarlas a RAM."
        }
        warnings += "No se genera startup ni runtime; una cabecera válida por sí sola no garantiza una ROM ejecutable."
        return GbaLinkResult(image, placements, symbolAddresses, warnings)
    }

    private fun validateRelocations(section: GbaSection) {
        val sorted = section.relocations.sortedBy { it.offset }
        var previousEnd = -1L
        sorted.forEach { relocation ->
            val width = relocationWidth(relocation.type)
            require(relocation.offset >= 0 &&
                relocation.offset.toLong() + width <= section.data.size.toLong()) {
                "Relocación fuera de rango en ${section.name}."
            }
            require(relocation.offset.toLong() >= previousEnd) {
                "Hay relocaciones solapadas en ${section.name}."
            }
            require(relocation.symbol.isNotBlank()) { "Una relocación de ${section.name} no tiene símbolo." }
            previousEnd = relocation.offset.toLong() + width
        }
    }

    private fun relocationWidth(type: GbaRelocation.Type): Int =
        if (type == GbaRelocation.Type.THUMB_BRANCH11) 2 else 4

    private fun isAsciiAlphaNumeric(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'

    private fun align(value: Int, alignment: Int): Int =
        (value + alignment - 1) and -alignment

    private fun read16(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun read32(bytes: ByteArray, offset: Int) =
        read16(bytes, offset) or (read16(bytes, offset + 2) shl 16)

    private fun write16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun write32(bytes: ByteArray, offset: Int, value: Int) {
        write16(bytes, offset, value)
        write16(bytes, offset + 2, value ushr 16)
    }
}
