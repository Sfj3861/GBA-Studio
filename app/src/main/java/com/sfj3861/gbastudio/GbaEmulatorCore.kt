package com.sfj3861.gbastudio

data class EmulatorSnapshot(
    val pc: Int,
    val registers: IntArray,
    val thumb: Boolean,
    val steps: Long,
    val halted: Boolean,
    val message: String,
    val cpsr: Int = 0
)

/**
 * Experimental ARM7TDMI instruction stepper.
 * It implements a focused arithmetic/branch subset, not the complete GBA memory bus,
 * PPU, audio, DMA, timers, interrupts, BIOS or cartridge peripherals.
 */
class GbaEmulatorCore(
    rom: ByteArray,
    startInThumb: Boolean = false,
    entryOffset: Int = 0
) {
    private val memory = ByteArray(0x02000000)
    private val regs = IntArray(16)
    private var thumb = startInThumb
    private var steps = 0L
    private var halted = false
    private val romSize = rom.size
    private var cpsr = 0
    private var message = "CPU preparado"

    init {
        require(rom.size >= 0xC0) { "El archivo es demasiado pequeño para una ROM GBA." }
        require(rom.size <= 32 * 1024 * 1024) { "ROM superior a 32 MiB." }
        require(entryOffset >= 0 && entryOffset < rom.size) { "El offset inicial está fuera de la ROM." }
        require(if (startInThumb) entryOffset % 2 == 0 else entryOffset % 4 == 0) {
            "El punto de entrada no está alineado para el modo seleccionado."
        }
        rom.copyInto(memory, 0, 0, rom.size)
        regs[15] = ROM_BASE + entryOffset
    }

    fun snapshot() = EmulatorSnapshot(regs[15], regs.copyOf(), thumb, steps, halted, message, cpsr)

    fun step(): EmulatorSnapshot {
        if (halted) return snapshot()
        val pc = regs[15]
        val offset = pc.toLong() - ROM_BASE.toLong()
        val width = if (thumb) 2 else 4
        if (offset < 0 || offset + width > romSize.toLong()) {
            halted = true
            message = "PC fuera del área ROM de prueba"
            return snapshot()
        }
        return try {
            if (thumb) executeThumb(read16(offset.toInt()), pc)
            else executeArm(read32(offset.toInt()), pc)
            steps++
            if (!halted) message = "Instrucción ejecutada"
            snapshot()
        } catch (e: Exception) {
            halted = true
            message = e.message ?: "Instrucción no soportada"
            snapshot()
        }
    }

    private fun executeThumb(op: Int, pc: Int) {
        regs[15] = pc + 2
        when {
            op and 0xF800 == 0x2000 -> {
                val result = op and 0xFF
                regs[(op ushr 8) and 7] = result
                setNz(result)
            }
            op and 0xF800 == 0x2800 -> {
                val lhs = regs[(op ushr 8) and 7]
                val rhs = op and 0xFF
                setSubFlags(lhs, rhs, 0)
            }
            op and 0xF800 == 0x3000 -> {
                val register = (op ushr 8) and 7
                val lhs = regs[register]
                val rhs = op and 0xFF
                val result = lhs + rhs
                regs[register] = result
                setAddFlags(lhs, rhs, 0, result)
            }
            op and 0xF800 == 0x3800 -> {
                val register = (op ushr 8) and 7
                val lhs = regs[register]
                val rhs = op and 0xFF
                val result = lhs - rhs
                regs[register] = result
                setSubFlags(lhs, rhs, 0, result)
            }
            op and 0xF000 == 0xD000 && ((op ushr 8) and 15) < 14 -> {
                val condition = (op ushr 8) and 15
                if (conditionPassed(condition)) {
                    val displacement = signExtend((op and 0xFF) shl 1, 9)
                    regs[15] = pc + 4 + displacement
                }
            }
            op and 0xF800 == 0xE000 -> {
                val displacement = signExtend((op and 0x7FF) shl 1, 12)
                regs[15] = pc + 4 + displacement
            }
            op and 0xFF00 == 0x4700 -> {
                val target = regs[(op ushr 3) and 15]
                thumb = target and 1 != 0
                regs[15] = target and if (thumb) -2 else -4
            }
            op == 0x46C0 -> Unit
            else -> throw IllegalArgumentException("Thumb opcode no implementado: %04X".format(op and 0xFFFF))
        }
    }

    private fun executeArm(op: Int, pc: Int) {
        val condition = (op ushr 28) and 15
        regs[15] = pc + 8
        if (!conditionPassed(condition)) {
            regs[15] = pc + 4
            return
        }

        when {
            op and 0x0E000000 == 0x0A000000 -> {
                val displacement = signExtend((op and 0xFFFFFF) shl 2, 26)
                if (op and (1 shl 24) != 0) regs[14] = pc + 4
                regs[15] = pc + 8 + displacement
            }
            op and 0x0C000000 == 0 -> executeArmDataProcessing(op, pc)
            else -> throw IllegalArgumentException("ARM opcode no implementado: %08X".format(op))
        }
    }

    private fun executeArmDataProcessing(op: Int, pc: Int) {
        val operation = (op ushr 21) and 15
        val setFlags = op and (1 shl 20) != 0
        val rnIndex = (op ushr 16) and 15
        val rd = (op ushr 12) and 15
        val lhs = regs[rnIndex]
        val operand = decodeOperand2(op)
        val rhs = operand.value
        val carryIn = if (flag(C_FLAG)) 1 else 0
        val testOnly = operation in 8..11

        var result = 0
        var arithmetic = false
        var carry = false
        var overflow = false
        when (operation) {
            0 -> result = lhs and rhs
            1 -> result = lhs xor rhs
            2 -> { result = lhs - rhs; arithmetic = true; carry = unsigned(lhs) >= unsigned(rhs); overflow = signedSubOverflow(lhs, rhs, result) }
            3 -> { result = rhs - lhs; arithmetic = true; carry = unsigned(rhs) >= unsigned(lhs); overflow = signedSubOverflow(rhs, lhs, result) }
            4 -> { result = lhs + rhs; arithmetic = true; carry = unsigned(lhs) + unsigned(rhs) > 0xFFFFFFFFL; overflow = signedAddOverflow(lhs, rhs, result) }
            5 -> {
                val unsignedSum = unsigned(lhs) + unsigned(rhs) + carryIn
                result = (lhs.toLong() + rhs.toLong() + carryIn).toInt()
                arithmetic = true
                carry = unsignedSum > 0xFFFFFFFFL
                overflow = signedAddOverflow(lhs, rhs + carryIn, result)
            }
            6 -> {
                val borrow = 1 - carryIn
                result = lhs - rhs - borrow
                arithmetic = true
                carry = unsigned(lhs) >= unsigned(rhs) + borrow
                overflow = signedSubOverflow(lhs, rhs + borrow, result)
            }
            7 -> {
                val borrow = 1 - carryIn
                result = rhs - lhs - borrow
                arithmetic = true
                carry = unsigned(rhs) >= unsigned(lhs) + borrow
                overflow = signedSubOverflow(rhs, lhs + borrow, result)
            }
            8 -> result = lhs and rhs
            9 -> result = lhs xor rhs
            10 -> { result = lhs - rhs; arithmetic = true; carry = unsigned(lhs) >= unsigned(rhs); overflow = signedSubOverflow(lhs, rhs, result) }
            11 -> { result = lhs + rhs; arithmetic = true; carry = unsigned(lhs) + unsigned(rhs) > 0xFFFFFFFFL; overflow = signedAddOverflow(lhs, rhs, result) }
            12 -> result = lhs or rhs
            13 -> result = rhs
            14 -> result = lhs and rhs.inv()
            15 -> result = rhs.inv()
        }

        if (setFlags || testOnly) {
            if (arithmetic) setNzcv(result, carry, overflow)
            else {
                setNz(result)
                if (operand.carry != null) setFlag(C_FLAG, operand.carry)
            }
        }
        if (!testOnly) {
            if (rd == 15) {
                require(!setFlags) { "MOVS/ALU con PC y restauración SPSR no implementada." }
                regs[15] = result and -4
            } else {
                regs[rd] = result
                regs[15] = pc + 4
            }
        } else {
            regs[15] = pc + 4
        }
    }

    private data class Operand2(val value: Int, val carry: Boolean?)

    private fun decodeOperand2(op: Int): Operand2 {
        if (op and (1 shl 25) != 0) {
            val immediate = op and 0xFF
            val rotation = ((op ushr 8) and 15) * 2
            val value = Integer.rotateRight(immediate, rotation)
            return Operand2(value, if (rotation == 0) null else value < 0)
        }
        val rm = op and 15
        val shiftType = (op ushr 5) and 3
        if (op and (1 shl 4) != 0) {
            require(op and (1 shl 7) == 0) { "Codificación de desplazamiento ARM no válida." }
            val amount = regs[(op ushr 8) and 15] and 0xFF
            if (amount == 0) return Operand2(regs[rm], null)
            return shift(regs[rm], shiftType, amount, byRegister = true)
        }
        val amount = (op ushr 7) and 31
        if (amount == 0 && shiftType == 0) return Operand2(regs[rm], null)
        return shift(regs[rm], shiftType, amount, byRegister = false)
    }

    private fun shift(value: Int, type: Int, amount: Int, byRegister: Boolean): Operand2 {
        if (amount == 0) return Operand2(value, null)
        return when (type) {
            0 -> if (amount < 32) Operand2(value shl amount, value ushr (32 - amount) and 1 != 0)
                else Operand2(0, if (amount == 32) value and 1 != 0 else false)
            1 -> if (amount < 32) Operand2(value ushr amount, value ushr (amount - 1) and 1 != 0)
                else Operand2(0, if (amount == 32) value < 0 else false)
            2 -> if (amount < 32) Operand2(value shr amount, value shr (amount - 1) and 1 != 0)
                else Operand2(if (value < 0) -1 else 0, value < 0)
            else -> {
                val rotate = amount and 31
                if (rotate == 0) {
                    if (byRegister && amount != 0) Operand2(value, value < 0)
                    else Operand2(value, null)
                } else {
                    val result = Integer.rotateRight(value, rotate)
                    Operand2(result, result < 0)
                }
            }
        }
    }

    private fun conditionPassed(condition: Int): Boolean {
        val n = flag(N_FLAG)
        val z = flag(Z_FLAG)
        val c = flag(C_FLAG)
        val v = flag(V_FLAG)
        return when (condition) {
            0 -> z
            1 -> !z
            2 -> c
            3 -> !c
            4 -> n
            5 -> !n
            6 -> v
            7 -> !v
            8 -> c && !z
            9 -> !c || z
            10 -> n == v
            11 -> n != v
            12 -> !z && n == v
            13 -> z || n != v
            14 -> true
            else -> false
        }
    }

    private fun setNz(value: Int) {
        setFlag(N_FLAG, value < 0)
        setFlag(Z_FLAG, value == 0)
    }

    private fun setNzcv(value: Int, carry: Boolean, overflow: Boolean) {
        setNz(value)
        setFlag(C_FLAG, carry)
        setFlag(V_FLAG, overflow)
    }

    private fun setAddFlags(lhs: Int, rhs: Int, carryIn: Int, result: Int) {
        val unsignedResult = unsigned(lhs) + unsigned(rhs) + carryIn
        val signedResult = lhs.toLong() + rhs.toLong() + carryIn
        setNzcv(result, unsignedResult > 0xFFFFFFFFL,
            signedResult < Int.MIN_VALUE.toLong() || signedResult > Int.MAX_VALUE.toLong())
    }

    private fun setSubFlags(lhs: Int, rhs: Int, borrowIn: Int, result: Int = lhs - rhs - borrowIn) {
        val unsignedResult = unsigned(lhs) - unsigned(rhs) - borrowIn
        val signedResult = lhs.toLong() - rhs.toLong() - borrowIn
        setNzcv(result, unsignedResult >= 0,
            signedResult < Int.MIN_VALUE.toLong() || signedResult > Int.MAX_VALUE.toLong())
    }

    private fun signedAddOverflow(lhs: Int, rhs: Int, result: Int): Boolean =
        ((lhs xor result) and (rhs xor result.inv())) < 0

    private fun signedSubOverflow(lhs: Int, rhs: Int, result: Int): Boolean =
        ((lhs xor rhs) and (lhs xor result)) < 0

    private fun unsigned(value: Int): Long = value.toLong() and 0xFFFFFFFFL
    private fun flag(mask: Int): Boolean = cpsr and mask != 0
    private fun setFlag(mask: Int, enabled: Boolean) {
        cpsr = if (enabled) cpsr or mask else cpsr and mask.inv()
    }

    private fun read16(offset: Int): Int =
        (memory[offset].toInt() and 255) or ((memory[offset + 1].toInt() and 255) shl 8)

    private fun read32(offset: Int): Int =
        read16(offset) or (read16(offset + 2) shl 16)

    private fun signExtend(value: Int, bits: Int): Int =
        (value shl (32 - bits)) shr (32 - bits)

    private companion object {
        const val ROM_BASE = 0x08000000
        const val N_FLAG = 1 shl 31
        const val Z_FLAG = 1 shl 30
        const val C_FLAG = 1 shl 29
        const val V_FLAG = 1 shl 28
    }
}
