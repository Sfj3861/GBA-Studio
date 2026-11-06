package com.sfj3861.gbastudio

data class GbaSection(
    val name: String,
    val data: ByteArray,
    val alignment: Int = 4,
    val region: Region = Region.ROM
) {
    enum class Region { ROM, EWRAM, IWRAM }
}

data class LinkedSection(val name: String, val address: Int, val size: Int, val fileOffset: Int?)
data class GbaLinkResult(val image: ByteArray, val sections: List<LinkedSection>, val warnings: List<String>)

object GbaLinker {
    const val ROM_BASE = 0x08000000
    const val EWRAM_BASE = 0x02000000
    const val IWRAM_BASE = 0x03000000
    private const val HEADER_SIZE = 0xC0
    private const val MAX_ROM_SIZE = 32 * 1024 * 1024

    /**
     * Places already-linked/raw sections into the GBA memory map and emits a ROM image.
     * It does not yet consume ELF object files or apply ARM relocations.
     */
    fun linkRawSections(
        sections: List<GbaSection>,
        title: String,
        gameCode: String = "GBA0",
        makerCode: String = "00",
        version: Int = 0
    ): GbaLinkResult {
        require(sections.isNotEmpty()) { "Añade al menos una sección." }
        require(title.isNotBlank()) { "El título no puede estar vacío." }
        require(gameCode.length == 4 && gameCode.all { it.isLetterOrDigit() }) { "Game Code debe tener 4 caracteres." }
        require(makerCode.length == 2 && makerCode.all { it.isLetterOrDigit() }) { "Maker Code debe tener 2 caracteres." }
        require(version in 0..255) { "Versión fuera de rango." }

        var romCursor = HEADER_SIZE
        var ewramCursor = EWRAM_BASE
        var iwramCursor = IWRAM_BASE
        val placements = mutableListOf<LinkedSection>()
        sections.forEach { section ->
            require(section.alignment > 0 && section.alignment <= 4096 && section.alignment and (section.alignment - 1) == 0) {
                "Alineación inválida en ${section.name}."
            }
            when (section.region) {
                GbaSection.Region.ROM -> {
                    romCursor = align(romCursor, section.alignment)
                    placements += LinkedSection(section.name, ROM_BASE + romCursor, section.data.size, romCursor)
                    romCursor += section.data.size
                }
                GbaSection.Region.EWRAM -> {
                    ewramCursor = align(ewramCursor, section.alignment)
                    placements += LinkedSection(section.name, ewramCursor, section.data.size, null)
                    ewramCursor += section.data.size
                }
                GbaSection.Region.IWRAM -> {
                    iwramCursor = align(iwramCursor, section.alignment)
                    placements += LinkedSection(section.name, iwramCursor, section.data.size, null)
                    iwramCursor += section.data.size
                }
            }
        }
        require(ewramCursor <= EWRAM_BASE + 256 * 1024) { "Las secciones superan la EWRAM disponible." }
        require(iwramCursor <= IWRAM_BASE + 32 * 1024) { "Las secciones superan la IWRAM disponible." }
        require(romCursor <= MAX_ROM_SIZE) { "La imagen supera el límite de 32 MiB." }

        val image = ByteArray(align(romCursor, 4))
        sections.forEachIndexed { index, section ->
            val offset = placements[index].fileOffset
            if (offset != null) section.data.copyInto(image, offset)
        }
        writeAscii(image, 0xA0, 12, title.uppercase().take(12))
        writeAscii(image, 0xAC, 4, gameCode.uppercase())
        writeAscii(image, 0xB0, 2, makerCode.uppercase())
        image[0xB2] = version.toByte()
        // GBA header complement checksum over 0xA0..0xBC. Logo bytes remain zero
        // until the user supplies a licensed/valid header template.
        var sum = 0
        for (i in 0xA0..0xBC) sum += image[i].toInt() and 0xFF
        image[0xBD] = (-(sum + 0x19) and 0xFF).toByte()
        return GbaLinkResult(
            image,
            placements,
            listOf(
                "Generador de imagen ROM experimental: coloca secciones raw, no enlaza ELF ni resuelve relocaciones.",
                "El bloque de logo de Nintendo no se incluye; esta imagen no garantiza arranque en BIOS hardware."
            )
        )
    }

    private fun align(value: Int, alignment: Int) = (value + alignment - 1) and -alignment
    private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
        for (i in 0 until length) target[offset + i] = (value.getOrNull(i)?.code ?: 0).toByte()
    }
}
