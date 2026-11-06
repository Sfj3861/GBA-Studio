package com.sfj3861.gbastudio

data class DisassembledInstruction(
    val address: Int,
    val opcode: Int,
    val width: Int,
    val mnemonic: String,
    val operands: String = ""
) {
    fun formatted(): String = "%08X  %s  %s".format(
        address.toLong() and 0xFFFFFFFFL,
        if (width == 2) "%04X".format(opcode and 0xFFFF) else "%08X".format(opcode),
        listOf(mnemonic, operands).filter { it.isNotBlank() }.joinToString(" ")
    )
}

/** Small, dependency-free ARM7TDMI decoder for inspection (not an assembler). */
object ArmDisassembler {
    fun decode(bytes: ByteArray, baseAddress: Int = 0, thumb: Boolean = true): List<DisassembledInstruction> {
        val result = ArrayList<DisassembledInstruction>()
        var offset = 0
        while (offset < bytes.size) {
            if (thumb) {
                if (offset + 1 >= bytes.size) break
                val op = u16(bytes, offset)
                result += decodeThumb(op, baseAddress + offset)
                offset += 2
            } else {
                if (offset + 3 >= bytes.size) break
                val op = u32(bytes, offset)
                result += decodeArm(op, baseAddress + offset)
                offset += 4
            }
        }
        return result
    }

    fun decodeThumb(op: Int, address: Int): DisassembledInstruction {
        val x = op and 0xFFFF
        val top = x ushr 11
        val pc = address + 4
        val text = when {
            x and 0xF800 == 0x2000 -> "MOVS" to "r${(x ushr 8) and 7}, #${x and 0xFF}"
            x and 0xF800 == 0x2800 -> "CMP" to "r${(x ushr 8) and 7}, #${x and 0xFF}"
            x and 0xF800 == 0x3000 -> "ADDS" to "r${(x ushr 8) and 7}, #${x and 0xFF}"
            x and 0xF800 == 0x3800 -> "SUBS" to "r${(x ushr 8) and 7}, #${x and 0xFF}"
            top in 0..2 -> {
                val names = listOf("LSL", "LSR", "ASR")
                names[top] to "r${x and 7}, r${(x ushr 3) and 7}, #${(x ushr 6) and 31}"
            }
            x and 0xF800 == 0x4800 -> {
                val target = ((pc + 2) and -4) + ((x and 0xFF) shl 2)
                "LDR" to "r${(x ushr 8) and 7}, [PC, #${(x and 0xFF) shl 2}] ; =0x%08X".format(target)
            }
            x and 0xF000 == 0xD000 && (x ushr 8 and 0xF) < 0xE -> {
                val cond = (x ushr 8) and 0xF
                val names = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE")
                val disp = signExtend((x and 0xFF) shl 1, 9)
                "B${names[cond]}" to "0x%08X".format(pc + disp)
            }
            x and 0xF800 == 0xE000 -> {
                val disp = signExtend((x and 0x7FF) shl 1, 12)
                "B" to "0x%08X".format(pc + disp)
            }
            x and 0xF800 == 0xF000 -> "BL (prefix)" to "offset=0x%03X".format(x and 0x7FF)
            x and 0xF800 == 0xF800 -> "BL (suffix)" to "offset=0x%03X".format(x and 0x7FF)
            x and 0xE000 == 0x6000 -> {
                val byteTransfer = x and 0x1000 != 0
                val load = x and 0x0800 != 0
                val rawOffset = (x ushr 6) and 31
                val offset = if (byteTransfer) rawOffset else rawOffset shl 2
                (if (load) "LDR" else "STR") + (if (byteTransfer) "B" else "") to
                    "r${x and 7}, [r${(x ushr 3) and 7}, #$offset]"
            }
            x and 0xF000 == 0x8000 -> {
                val load = x and 0x0800 != 0
                val offset = ((x ushr 6) and 31) shl 1
                (if (load) "LDRH" else "STRH") to
                    "r${x and 7}, [r${(x ushr 3) and 7}, #$offset]"
            }
            x and 0xF000 == 0x9000 -> {
                val load = x and 0x0800 != 0
                val offset = (x and 0xFF) shl 2
                (if (load) "LDR" else "STR") to "r${(x ushr 8) and 7}, [SP, #$offset]"
            }
            x and 0xF000 == 0x5000 -> "LOAD/STORE (reg)" to "op=0x${(x ushr 9) and 7}, r${x and 7}, [r${(x ushr 3) and 7}, r${(x ushr 6) and 7}]"
            x and 0xFF00 == 0x4700 -> "BX" to "r${(x ushr 3) and 15}"
            x == 0x46C0 -> "NOP" to ""
            x == 0xBE00 -> "BKPT" to ""
            else -> ".hword" to "0x%04X".format(x)
        }
        return DisassembledInstruction(address, x, 2, text.first, text.second)
    }

    fun decodeArm(op: Int, address: Int): DisassembledInstruction {
        val cond = (op ushr 28) and 0xF
        val suffix = listOf("EQ","NE","CS","CC","MI","PL","VS","VC","HI","LS","GE","LT","GT","LE","","")[cond]
        val pc = address + 8
        val decoded = when {
            op and 0x0FFFFFF0 == 0x012FFF10 -> "BX$suffix" to "r${op and 15}"
            op and 0x0E000000 == 0x0A000000 -> {
                val disp = signExtend((op and 0xFFFFFF) shl 2, 26)
                (if (op and (1 shl 24) != 0) "BL$suffix" else "B$suffix") to "0x%08X".format(pc + disp)
            }
            op and 0x0C000000 == 0x04000000 -> decodeArmTransfer(op, suffix)
            op and 0x0C000000 == 0 -> {
                val names = arrayOf("AND","EOR","SUB","RSB","ADD","ADC","SBC","RSC","TST","TEQ","CMP","CMN","ORR","MOV","BIC","MVN")
                val code = (op ushr 21) and 15
                val rd = (op ushr 12) and 15
                val rn = (op ushr 16) and 15
                val operand = if (op and (1 shl 25) != 0) "#${op and 0xFF}" else "r${op and 15}"
                names[code] + suffix + (if (op and (1 shl 20) != 0) "S" else "") to
                    (if (code == 13 || code == 15) "r$rd, $operand" else "r$rd, r$rn, $operand")
            }
            else -> ".word" to "0x%08X".format(op)
        }
        return DisassembledInstruction(address, op, 4, decoded.first, decoded.second)
    }

    private fun decodeArmTransfer(op: Int, suffix: String): Pair<String, String> {
        val registerOffset = op and (1 shl 25) != 0
        val preIndexed = op and (1 shl 24) != 0
        val add = op and (1 shl 23) != 0
        val byteTransfer = op and (1 shl 22) != 0
        val writeBack = op and (1 shl 21) != 0
        val load = op and (1 shl 20) != 0
        val rn = (op ushr 16) and 15
        val rd = (op ushr 12) and 15
        val offset = if (registerOffset) {
            val rm = op and 15
            val shiftType = (op ushr 5) and 3
            val shiftAmount = (op ushr 7) and 31
            val shiftName = arrayOf("LSL", "LSR", "ASR", "ROR")[shiftType]
            if (shiftAmount == 0) "r${rm}" else "r${rm}, $shiftName #$shiftAmount"
        } else "#${op and 0xFFF}"
        val signedOffset = (if (add) "" else "-") + offset
        val base = "r$rn"
        val address = if (preIndexed) {
            "[$base, $signedOffset]" + (if (writeBack) "!" else "")
        } else {
            "[$base], $signedOffset"
        }
        val mnemonic = (if (load) "LDR" else "STR") + (if (byteTransfer) "B" else "") + suffix
        return mnemonic to "r$rd, $address"
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, i: Int) = u16(b, i) or (u16(b, i + 2) shl 16)
    private fun signExtend(value: Int, bits: Int): Int = (value shl (32 - bits)) shr (32 - bits)
}
