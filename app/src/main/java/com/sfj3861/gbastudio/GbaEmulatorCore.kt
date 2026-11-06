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
    entryOffset: Int = 0,
    initialRegisters: IntArray? = null
) {
    private val memory = ByteArray(0x02000000)
    private val ewram = ByteArray(256 * 1024)
    private val iwram = ByteArray(32 * 1024)
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
        require(initialRegisters == null || initialRegisters.size == 16) { "El estado inicial debe contener exactamente 16 registros." }
        initialRegisters?.copyInto(regs)
        regs[15] = ROM_BASE + entryOffset
        setFlag(T_FLAG, startInThumb)
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
            op and 0xF800 == 0x4800 -> {
                val destination = (op ushr 8) and 7
                val address = ((pc + 4) and -4) + ((op and 0xFF) shl 2)
                regs[destination] = readMemory32(address and -4)
            }
            op and 0xF000 == 0x5000 -> executeThumbRegisterTransfer(op)
            op and 0xE000 == 0x6000 -> executeThumbImmediateTransfer(op)
            op and 0xF000 == 0x8000 -> {
                val load = op and 0x0800 != 0
                val address = regs[(op ushr 3) and 7] + (((op ushr 6) and 31) shl 1)
                val register = op and 7
                if (load) regs[register] = readMemory16(address and -2)
                else writeMemory16(address and -2, regs[register])
            }
            op and 0xF000 == 0x9000 -> {
                val load = op and 0x0800 != 0
                val register = (op ushr 8) and 7
                val address = regs[13] + ((op and 0xFF) shl 2)
                if (load) regs[register] = readMemory32(address and -4)
                else writeMemory32(address and -4, regs[register])
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
                setFlag(T_FLAG, thumb)
                regs[15] = target and (if (thumb) -2 else -4)
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
            op and 0x0FFFFFF0 == 0x012FFF10 -> {
                val target = regs[op and 15]
                thumb = target and 1 != 0
                setFlag(T_FLAG, thumb)
                regs[15] = target and (if (thumb) -2 else -4)
            }
            op and 0x0E000000 == 0x0A000000 -> {
                val displacement = signExtend((op and 0xFFFFFF) shl 2, 26)
                if (op and (1 shl 24) != 0) regs[14] = pc + 4
                regs[15] = pc + 8 + displacement
            }
            op and 0x0FC000F0 == 0x00000090 -> executeArmMultiply(op, pc)
            op and 0x0C000000 == 0x04000000 -> executeArmSingleTransfer(op, pc)
            op and 0x0E000090 == 0x00000090 -> throw IllegalArgumentException("Transferencia ARM halfword/miscelánea todavía no implementada.")
            op and 0x0C000000 == 0 -> executeArmDataProcessing(op, pc)
            else -> throw IllegalArgumentException("ARM opcode no implementado: %08X".format(op))
        }
    }

    private fun executeArmMultiply(op: Int, pc: Int) {
        val accumulate = op and (1 shl 21) != 0
        val setFlags = op and (1 shl 20) != 0
        val rd = (op ushr 16) and 15
        val rn = (op ushr 12) and 15
        val rs = (op ushr 8) and 15
        val rm = op and 15
        require(rd != 15 && rs != 15 && rm != 15 && (!accumulate || rn != 15) && rd != rm) {
            "Registros no válidos para MUL/MLA ARM7TDMI."
        }
        var result = regs[rm] * regs[rs]
        if (accumulate) result += regs[rn]
        regs[rd] = result
        if (setFlags) setNz(result)
        regs[15] = pc + 4
    }

    private fun executeArmSingleTransfer(op: Int, pc: Int) {
        require(op and (1 shl 25) == 0) {
            "Transferencia ARM con offset de registro todavía no implementada."
        }
        val preIndexed = op and (1 shl 24) != 0
        val addOffset = op and (1 shl 23) != 0
        val byteTransfer = op and (1 shl 22) != 0
        val writeBack = op and (1 shl 21) != 0
        val load = op and (1 shl 20) != 0
        val rn = (op ushr 16) and 15
        val rd = (op ushr 12) and 15
        require(!(rn == 15 && (!preIndexed || writeBack))) { "Writeback con PC como base no soportado." }
        require(!(load && rd == rn && (!preIndexed || writeBack))) { "LDR con writeback sobre el mismo registro base no soportado." }
        val base = regs[rn]
        val offset = op and 0xFFF
        val adjusted = if (addOffset) base + offset else base - offset
        val address = if (preIndexed) adjusted else base

        if (load) {
            regs[rd] = if (byteTransfer) {
                readMemory8(address)
            } else {
                val alignedAddress = address and -4
                Integer.rotateRight(readMemory32(alignedAddress), (address and 3) * 8)
            }
        } else {
            if (byteTransfer) writeMemory8(address, regs[rd])
            else writeMemory32(address and -4, regs[rd])
        }
        if (!preIndexed || writeBack) regs[rn] = adjusted
        if (rd != 15 || !load) regs[15] = pc + 4
        else regs[15] = regs[15] and -4
    }

    private fun executeThumbRegisterTransfer(op: Int) {
        val operation = (op ushr 9) and 7
        val address = regs[(op ushr 3) and 7] + regs[(op ushr 6) and 7]
        val destination = op and 7
        when (operation) {
            0 -> writeMemory32(address and -4, regs[destination])
            1 -> writeMemory16(address and -2, regs[destination])
            2 -> writeMemory8(address, regs[destination])
            3 -> regs[destination] = signExtend(readMemory8(address), 8)
            4 -> regs[destination] = Integer.rotateRight(readMemory32(address and -4), (address and 3) * 8)
            5 -> regs[destination] = readMemory16(address and -2)
            6 -> regs[destination] = readMemory8(address)
            7 -> regs[destination] = signExtend(readMemory16(address and -2), 16)
        }
    }

    private fun executeThumbImmediateTransfer(op: Int) {
        val byteTransfer = op and 0x1000 != 0
        val load = op and 0x0800 != 0
        val offset = (op ushr 6) and 31
        val address = regs[(op ushr 3) and 7] + if (byteTransfer) offset else offset shl 2
        val register = op and 7
        if (load) {
            regs[register] = if (byteTransfer) readMemory8(address)
            else Integer.rotateRight(readMemory32(address and -4), (address and 3) * 8)
        } else {
            if (byteTransfer) writeMemory8(address, regs[register])
            else writeMemory32(address and -4, regs[register])
        }
    }

    private fun mapMemory(address: Int, size: Int): Pair<ByteArray, Int> {
        require(size > 0) { "Tamaño de acceso de memoria no válido." }
        val start = address.toLong() and 0xFFFFFFFFL
        val end = start + size.toLong()
        val romBase = ROM_BASE.toLong()
        val ewramBase = EWRAM_BASE.toLong()
        val iwramBase = IWRAM_BASE.toLong()
        return when {
            start >= romBase && end <= romBase + romSize ->
                memory to (start - romBase).toInt()
            start >= ewramBase && end <= ewramBase + ewram.size ->
                ewram to (start - ewramBase).toInt()
            start >= iwramBase && end <= iwramBase + iwram.size ->
                iwram to (start - iwramBase).toInt()
            else -> throw IllegalArgumentException("Acceso a memoria no implementado: 0x%08X".format(address))
        }
    }

    private fun readMemory8(address: Int): Int {
        val (region, offset) = mapMemory(address, 1)
        return region[offset].toInt() and 255
    }

    private fun readMemory16(address: Int): Int {
        val (region, offset) = mapMemory(address, 2)
        return (region[offset].toInt() and 255) or ((region[offset + 1].toInt() and 255) shl 8)
    }

    private fun readMemory32(address: Int): Int {
        val (region, offset) = mapMemory(address, 4)
        return (region[offset].toInt() and 255) or
            ((region[offset + 1].toInt() and 255) shl 8) or
            ((region[offset + 2].toInt() and 255) shl 16) or
            ((region[offset + 3].toInt() and 255) shl 24)
    }

    private fun writeMemory8(address: Int, value: Int) {
        val (region, offset) = mapMemory(address, 1)
        require(region !== memory) { "La región ROM es de solo lectura." }
        region[offset] = value.toByte()
    }

    private fun writeMemory16(address: Int, value: Int) {
        val (region, offset) = mapMemory(address, 2)
        require(region !== memory) { "La región ROM es de solo lectura." }
        region[offset] = value.toByte()
        region[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeMemory32(address: Int, value: Int) {
        val (region, offset) = mapMemory(address, 4)
        require(region !== memory) { "La región ROM es de solo lectura." }
        region[offset] = value.toByte()
        region[offset + 1] = (value ushr 8).toByte()
        region[offset + 2] = (value ushr 16).toByte()
        region[offset + 3] = (value ushr 24).toByte()
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
            2 -> { result = lhs - rhs; arithmetic = true; carry = unsigned(lhs) >= unsigned(rhs); overflow = signedSubOverflowLong(lhs, rhs, 0) }
            3 -> { result = rhs - lhs; arithmetic = true; carry = unsigned(rhs) >= unsigned(lhs); overflow = signedSubOverflowLong(rhs, lhs, 0) }
            4 -> { result = lhs + rhs; arithmetic = true; carry = unsigned(lhs) + unsigned(rhs) > 0xFFFFFFFFL; overflow = signedAddOverflowLong(lhs, rhs, 0) }
            5 -> {
                val unsignedSum = unsigned(lhs) + unsigned(rhs) + carryIn
                result = (lhs.toLong() + rhs.toLong() + carryIn).toInt()
                arithmetic = true
                carry = unsignedSum > 0xFFFFFFFFL
                overflow = signedAddOverflowLong(lhs, rhs, carryIn)
            }
            6 -> {
                val borrow = 1 - carryIn
                result = lhs - rhs - borrow
                arithmetic = true
                carry = unsigned(lhs) >= unsigned(rhs) + borrow
                overflow = signedSubOverflowLong(lhs, rhs, borrow)
            }
            7 -> {
                val borrow = 1 - carryIn
                result = rhs - lhs - borrow
                arithmetic = true
                carry = unsigned(rhs) >= unsigned(lhs) + borrow
                overflow = signedSubOverflowLong(rhs, lhs, borrow)
            }
            8 -> result = lhs and rhs
            9 -> result = lhs xor rhs
            10 -> { result = lhs - rhs; arithmetic = true; carry = unsigned(lhs) >= unsigned(rhs); overflow = signedSubOverflowLong(lhs, rhs, 0) }
            11 -> { result = lhs + rhs; arithmetic = true; carry = unsigned(lhs) + unsigned(rhs) > 0xFFFFFFFFL; overflow = signedAddOverflowLong(lhs, rhs, 0) }
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
        if (amount == 0) {
            return when (shiftType) {
                0 -> Operand2(regs[rm], null)
                1 -> Operand2(0, regs[rm] < 0) // LSR #32
                2 -> Operand2(if (regs[rm] < 0) -1 else 0, regs[rm] < 0) // ASR #32
                else -> {
                    val result = (if (flag(C_FLAG)) Int.MIN_VALUE else 0) or (regs[rm] ushr 1)
                    Operand2(result, regs[rm] and 1 != 0) // RRX
                }
            }
        }
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

    private fun signedAddOverflowLong(lhs: Int, rhs: Int, carryIn: Int): Boolean {
        val result = lhs.toLong() + rhs.toLong() + carryIn
        return result < Int.MIN_VALUE.toLong() || result > Int.MAX_VALUE.toLong()
    }

    private fun signedSubOverflowLong(lhs: Int, rhs: Int, borrowIn: Int): Boolean {
        val result = lhs.toLong() - rhs.toLong() - borrowIn
        return result < Int.MIN_VALUE.toLong() || result > Int.MAX_VALUE.toLong()
    }

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
        const val T_FLAG = 1 shl 5
    }
}
