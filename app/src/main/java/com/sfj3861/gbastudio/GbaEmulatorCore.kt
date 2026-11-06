package com.sfj3861.gbastudio

data class EmulatorSnapshot(
    val pc: Int,
    val registers: IntArray,
    val thumb: Boolean,
    val steps: Long,
    val halted: Boolean,
    val message: String
)

/**
 * Experimental instruction-stepper for a small ARM7TDMI subset.
 * It is not a complete GBA emulator: graphics, audio, DMA, timers, interrupts and full
 * instruction coverage are not implemented yet.
 */
class GbaEmulatorCore(rom: ByteArray) {
    private val memory = ByteArray(0x02000000)
    private val regs = IntArray(16)
    private var cpsr = 0
    private var thumb = false
    private var steps = 0L
    private var halted = false
    private val romSize = rom.size
    private var message = "CPU preparado"

    init {
        require(rom.size >= 0xC0) { "El archivo es demasiado pequeño para ser una ROM GBA." }
        require(rom.size <= 32 * 1024 * 1024) { "ROM superior a 32 MiB." }
        rom.copyInto(memory, 0x08000000 - 0x08000000, 0, minOf(rom.size, 0x02000000))
        regs[15] = 0x08000000
    }

    fun snapshot() = EmulatorSnapshot(regs[15], regs.copyOf(), thumb, steps, halted, message)

    fun step(): EmulatorSnapshot {
        if (halted) return snapshot()
        val pc = regs[15]
        val offset = pc - 0x08000000
        if (offset < 0 || offset + (if (thumb) 1 else 3) >= romSize) {
            halted = true
            message = "PC fuera del área ROM de prueba"
            return snapshot()
        }
        return try {
            if (thumb) executeThumb(read16(offset), pc) else executeArm(read32(offset), pc)
            steps++
            message = "Instrucción ejecutada"
            snapshot()
        } catch (e: Exception) {
            halted = true
            message = e.message ?: "Instrucción no soportada"
            snapshot()
        }
    }

    private fun executeThumb(op: Int, pc: Int) {
        val x = op and 0xFFFF
        regs[15] = pc + 2
        when {
            x and 0xF800 == 0x2000 -> regs[(x ushr 8) and 7] = x and 255
            x and 0xF800 == 0x3000 -> { val r = (x ushr 8) and 7; regs[r] += x and 255 }
            x and 0xF800 == 0x3800 -> { val r = (x ushr 8) and 7; regs[r] -= x and 255 }
            x and 0xF800 == 0xE000 -> regs[15] = pc + 4 + ((x shl 21) shr 20)
            x and 0xFF00 == 0x4700 -> {
                val target = regs[(x ushr 3) and 15]
                thumb = target and 1 != 0
                regs[15] = target and if (thumb) -2 else -4
            }
            x == 0x46C0 -> Unit
            else -> throw IllegalArgumentException("Thumb opcode no implementado: %04X".format(x))
        }
    }

    private fun executeArm(op: Int, pc: Int) {
        if (op ushr 28 != 0xE) {
            regs[15] = pc + 4
            return
        }
        when {
            op and 0x0E000000 == 0x0A000000 -> {
                val displacement = (op shl 8) shr 6
                regs[15] = pc + 8 + displacement
                if (op and (1 shl 24) != 0) regs[14] = pc + 4
            }
            op and 0x0C000000 == 0 -> {
                val opcode = (op ushr 21) and 15
                val rn = regs[(op ushr 16) and 15]
                val rd = (op ushr 12) and 15
                val operand = if (op and (1 shl 25) != 0) op and 255 else regs[op and 15]
                when (opcode) {
                    0 -> regs[rd] = rn and operand
                    1 -> regs[rd] = rn xor operand
                    2 -> regs[rd] = rn - operand
                    4 -> regs[rd] = rn + operand
                    12 -> regs[rd] = rn or operand
                    13 -> regs[rd] = operand
                    14 -> regs[rd] = rn and operand.inv()
                    15 -> regs[rd] = operand.inv()
                    else -> throw IllegalArgumentException("ARM data opcode no implementado: $opcode")
                }
                if (rd == 15) regs[15] = regs[15] and -4 else regs[15] = pc + 4
            }
            else -> throw IllegalArgumentException("ARM opcode no implementado: %08X".format(op))
        }
    }

    private fun read16(offset: Int) = (memory[offset].toInt() and 255) or ((memory[offset + 1].toInt() and 255) shl 8)
    private fun read32(offset: Int) = read16(offset) or (read16(offset + 2) shl 16)
}
