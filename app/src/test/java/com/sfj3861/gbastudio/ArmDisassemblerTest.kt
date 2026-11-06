package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmDisassemblerTest {
    @Test fun decodesThumbImmediateMove() {
        val decoded = ArmDisassembler.decodeThumb(0x2007, 0x08000000)
        assertEquals("MOVS", decoded.mnemonic)
        assertEquals("r0, #7", decoded.operands)
    

    @Test fun decodesThumbBxAndKeepsReservedVariantAsData() {
        assertEquals("BX", ArmDisassembler.decodeThumb(0x4700, 0x08000000).mnemonic)
        assertEquals(".hword", ArmDisassembler.decodeThumb(0x4780, 0x08000000).mnemonic)
    }

    @Test fun preservesTrailingBytesInBothInstructionModes() {
        val thumb = ArmDisassembler.decode(byteArrayOf(0x34), 0x08000000, thumb = true)
        val arm = ArmDisassembler.decode(byteArrayOf(0x34, 0x12), 0x08000000, thumb = false)
        assertEquals(1, thumb.size)
        assertEquals(1, thumb.single().width)
        assertEquals(".byte", thumb.single().mnemonic)
        assertEquals(2, arm.size)
        assertTrue(arm.all { it.width == 1 && it.mnemonic == ".byte" })
    }

    @Test fun alignsThumbPcRelativeLiteralAddress() {
        val decoded = ArmDisassembler.decodeThumb(0x4800, 0x08000002)
        assertEquals("LDR", decoded.mnemonic)
        assertTrue(decoded.operands.endsWith("=0x08000004"))
    }

    @Test fun decodesArmMultiplyAndMultiplyAccumulate() {
        val mul = ArmDisassembler.decodeArm(0xE0020190.toInt(), 0x08000000)
        val mla = ArmDisassembler.decodeArm(0xE0223190.toInt(), 0x08000000)
        assertEquals("MUL", mul.mnemonic)
        assertEquals("r2, r0, r1", mul.operands)
        assertEquals("MLA", mla.mnemonic)
        assertEquals("r2, r0, r1, r3", mla.operands)
    }

    @Test fun doesNotAppendSetFlagsSuffixToArmTestInstruction() {
        val decoded = ArmDisassembler.decodeArm(0xE3100001.toInt(), 0x08000000)
        assertEquals("TST", decoded.mnemonic)
        assertEquals("r0, #0x00000001", decoded.operands)
    }

    @Test fun rejectsReservedThumbBxVariantAsData() {
        val decoded = ArmDisassembler.decodeThumb(0x4780, 0x08000000)
        assertEquals(".hword", decoded.mnemonic)
    }

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
