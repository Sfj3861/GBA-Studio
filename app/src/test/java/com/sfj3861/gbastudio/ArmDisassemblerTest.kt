package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmDisassemblerTest {
    @Test fun decodesThumbImmediateMove() {
        val decoded = ArmDisassembler.decodeThumb(0x2007, 0x08000000)
        assertEquals("MOVS", decoded.mnemonic)
        assertEquals("r0, #7", decoded.operands)
    }

    @Test fun decodesArmBranch() {
        val decoded = ArmDisassembler.decodeArm(0xEA000000.toInt(), 0x08000000)
        assertEquals("B", decoded.mnemonic)
        assertEquals("0x08000008", decoded.operands)
    }

    @Test fun decodesThumbByteLoadAndStore() {
        val load = ArmDisassembler.decodeThumb(0x7801, 0x08000000)
        val store = ArmDisassembler.decodeThumb(0x7002, 0x08000000)
        assertEquals("LDRB", load.mnemonic)
        assertEquals("r1, [r0, #0]", load.operands)
        assertEquals("STRB", store.mnemonic)
        assertEquals("r2, [r0, #0]", store.operands)
    }

    @Test fun decodesThumbHalfwordAndStackRelativeLoad() {
        val halfword = ArmDisassembler.decodeThumb(0x8801, 0x08000000)
        val stack = ArmDisassembler.decodeThumb(0x9802, 0x08000000)
        assertEquals("LDRH", halfword.mnemonic)
        assertEquals("r1, [r0, #0]", halfword.operands)
        assertEquals("LDR", stack.mnemonic)
        assertEquals("r0, [SP, #8]", stack.operands)
    }

    @Test fun decodesRotatedArmImmediate() {
        val decoded = ArmDisassembler.decodeArm(0xE3A004FF.toInt(), 0x08000000)
        assertEquals("MOV", decoded.mnemonic)
        assertTrue(decoded.operands.endsWith("#0xFF000000"))
    }

    @Test fun unknownUnconditionalArmEncodingRemainsData() {
        val decoded = ArmDisassembler.decodeArm(0xFA000000.toInt(), 0x08000000)
        assertEquals(".word", decoded.mnemonic)
    }
}
