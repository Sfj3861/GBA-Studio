package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
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
}
