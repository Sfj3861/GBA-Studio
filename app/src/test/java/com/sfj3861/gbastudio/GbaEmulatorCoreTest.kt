package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaEmulatorCoreTest {
    @Test fun executesArmImmediateArithmeticAndUpdatesFlags() {
        val core = GbaEmulatorCore(romOf(
            0xE3A00005.toInt(), // MOV r0, #5
            0xE2801003.toInt(), // ADD r1, r0, #3
            0xE2512008.toInt()  // SUBS r2, r1, #8
        ))

        core.step()
        core.step()
        val state = core.step()

        assertEquals(5, state.registers[0])
        assertEquals(8, state.registers[1])
        assertEquals(0, state.registers[2])
        assertTrue(state.cpsr and (1 shl 30) != 0) // Z
        assertTrue(state.cpsr and (1 shl 29) != 0) // C: no borrow
        assertFalse(state.cpsr and (1 shl 31) != 0) // N
        assertFalse(state.halted)
    }

    @Test fun decodesRotatedArmImmediateOperand() {
        val core = GbaEmulatorCore(romOf(0xE3A004FF.toInt())) // MOV r0, #0xFF000000
        val state = core.step()
        assertEquals(0xFF000000.toInt(), state.registers[0])
    }

    @Test fun readsArmProgramCounterWithArchitecturalPipelineOffset() {
        val core = GbaEmulatorCore(romOf(0xE1A0000F.toInt())) // MOV r0, PC
        val state = core.step()
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 8, state.registers[0])
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 4, state.pc)
    }

    @Test fun skipsArmInstructionWhenConditionFails() {
        val core = GbaEmulatorCore(romOf(
            0xE3B00000.toInt(), // MOVS r0, #0 (sets Z)
            0x1A000000,         // BNE +0: must be skipped
            0xE3A01001.toInt()  // MOV r1, #1
        ))
        core.step()
        core.step()
        val state = core.step()
        assertEquals(1, state.registers[1])
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 12, state.pc)
    }

    @Test fun thumbBranchSignExtendsNegativeOffsetCorrectly() {
        val rom = ByteArray(0xC0)
        rom[0] = 0xFF.toByte()
        rom[1] = 0xE7.toByte() // B -2
        val core = GbaEmulatorCore(rom, startInThumb = true)
        val state = core.step()
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 2, state.pc)
        assertTrue(state.thumb)
    }

    @Test fun unsupportedArmInstructionStopsStepperWithReason() {
        val core = GbaEmulatorCore(romOf(0xEF000000.toInt()))
        val state = core.step()
        assertTrue(state.halted)
        assertTrue(state.message.contains("no implementado"))
    }


    @Test fun bxSwitchesFromArmToThumbMode() {
        val rom = ByteArray(0xC2)
        val bxR0 = 0xE12FFF10.toInt()
        rom[0] = bxR0.toByte()
        rom[1] = (bxR0 ushr 8).toByte()
        rom[2] = (bxR0 ushr 16).toByte()
        rom[3] = (bxR0 ushr 24).toByte()
        rom[0xC0] = 0x01
        rom[0xC1] = 0x20 // MOVS r0, #1 in Thumb
        val initial = IntArray(16).also { it[0] = GbaEmulatorCoreTestBase.ROM_BASE + 0xC1 }
        val core = GbaEmulatorCore(rom, initialRegisters = initial)

        val switched = core.step()
        assertTrue(switched.thumb)
        assertTrue(switched.cpsr and (1 shl 5) != 0)
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 0xC0, switched.pc)

        val executed = core.step()
        assertEquals(1, executed.registers[0])
        assertTrue(executed.thumb)
    }


    @Test fun storesAndLoadsArmWordThroughEwram() {
        val core = GbaEmulatorCore(
            romOf(0xE5801000.toInt(), 0xE5902000.toInt()),
            initialRegisters = IntArray(16).also {
                it[0] = 0x02000000
                it[1] = 0x12345678
            }
        )
        core.step()
        val state = core.step()
        assertEquals(0x12345678, state.registers[2])
        assertFalse(state.halted)
    }

    @Test fun storesAndLoadsThumbWordThroughIwram() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x01
        rom[1] = 0x60.toByte() // STR r1, [r0]
        rom[2] = 0x02
        rom[3] = 0x68 // LDR r2, [r0]
        val core = GbaEmulatorCore(
            rom,
            startInThumb = true,
            initialRegisters = IntArray(16).also {
                it[0] = 0x03000000
                it[1] = 0xCAFEBABE.toInt()
            }
        )
        core.step()
        val state = core.step()
        assertEquals(0xCAFEBABE.toInt(), state.registers[2])
        assertTrue(state.thumb)
        assertFalse(state.halted)
    }

    @Test fun executesArmStoreAndLoadMultipleWithIncrementAfterWriteback() {
        val core = GbaEmulatorCore(
            romOf(
                0xE8A00006.toInt(), // STMIA r0!, {r1,r2}
                0xE8B50018.toInt()  // LDMIA r5!, {r3,r4}
            ),
            initialRegisters = IntArray(16).also {
                it[0] = 0x02000000
                it[5] = 0x02000000
                it[1] = 0x11223344
                it[2] = 0x55667788
            }
        )

        core.step()
        val loaded = core.step()

        assertEquals(0x11223344, loaded.registers[3])
        assertEquals(0x55667788, loaded.registers[4])
        assertEquals(0x02000008, loaded.registers[0])
        assertEquals(0x02000008, loaded.registers[5])
        assertFalse(loaded.halted)
    }

    @Test fun executesArmMultiply() {
        val core = GbaEmulatorCore(
            romOf(0xE0020190.toInt()), // MUL r2, r0, r1
            initialRegisters = IntArray(16).also {
                it[0] = 6
                it[1] = 7
            }
        )
        val state = core.step()
        assertEquals(42, state.registers[2])
        assertFalse(state.halted)
    }

    @Test fun storesArmProgramCounterWithStorePipelineOffset() {
        val core = GbaEmulatorCore(
            romOf(0xE580F000.toInt(), 0xE5901000.toInt()), // STR pc, [r0]; LDR r1, [r0]
            initialRegisters = IntArray(16).also { it[0] = 0x02000000 }
        )

        core.step()
        val state = core.step()

        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 12, state.registers[1])
        assertFalse(state.halted)
    }

    @Test fun refusesWritesToRom() {
        val core = GbaEmulatorCore(
            romOf(0xE5801000.toInt()), // STR r1, [r0]
            initialRegisters = IntArray(16).also {
                it[0] = GbaEmulatorCoreTestBase.ROM_BASE
                it[1] = 0x12345678
            }
        )
        val state = core.step()
        assertTrue(state.halted)
        assertTrue(state.message.contains("solo lectura"))
    }


    @Test fun executesArmMultiplyAccumulate() {
        val core = GbaEmulatorCore(
            romOf(0xE0223190.toInt()), // MLA r2, r0, r1, r3
            initialRegisters = IntArray(16).also {
                it[0] = 6
                it[1] = 7
                it[3] = 5
            }
        )
        val state = core.step()
        assertEquals(47, state.registers[2])
        assertFalse(state.halted)
    }

    @Test fun refusesToWriteToRomFromThumbCode() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x41
        rom[1] = 0x60.toByte() // STR r1, [r0]
        val core = GbaEmulatorCore(
            rom,
            startInThumb = true,
            initialRegisters = IntArray(16).also {
                it[0] = GbaEmulatorCoreTestBase.ROM_BASE
                it[1] = 0x12345678
            }
        )
        val state = core.step()
        assertTrue(state.halted)
        assertTrue(state.message.contains("solo lectura"))
    }

    @Test fun rejectsUnsupportedArmHalfwordTransferInsteadOfExecutingAsAlu() {
        val core = GbaEmulatorCore(romOf(0xE1D000B0.toInt()))
        val state = core.step()
        assertTrue(state.halted)
        assertTrue(state.message.contains("halfword"))
    }


    @Test fun tracksSignedOverflowOnArmAdd() {
        val core = GbaEmulatorCore(
            romOf(0xE2910001.toInt()), // ADDS r0, r1, #1
            initialRegisters = IntArray(16).also { it[1] = 0x7FFFFFFF }
        )
        val state = core.step()
        assertEquals(0x80000000.toInt(), state.registers[0])
        assertTrue(state.cpsr and (1 shl 31) != 0)
        assertTrue(state.cpsr and (1 shl 28) != 0)
        assertFalse(state.cpsr and (1 shl 29) != 0)
    }

    @Test fun tracksBorrowOnArmSubtract() {
        val core = GbaEmulatorCore(
            romOf(0xE2510001.toInt()), // SUBS r0, r1, #1
            initialRegisters = IntArray(16).also { it[1] = 0 }
        )
        val state = core.step()
        assertEquals(-1, state.registers[0])
        assertTrue(state.cpsr and (1 shl 31) != 0)
        assertFalse(state.cpsr and (1 shl 29) != 0)
        assertFalse(state.cpsr and (1 shl 28) != 0)
    }

    @Test fun rejectsReservedThumbBxVariant() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x80.toByte()
        rom[1] = 0x47
        val state = GbaEmulatorCore(rom, startInThumb = true).step()
        assertTrue(state.halted)
        assertTrue(state.message.contains("Thumb opcode"))
    }


    @Test fun executesThumbShiftAndArithmeticInstructions() {
        val rom = ByteArray(0xC0)
        val instructions = intArrayOf(0x0048, 0x1CC8, 0x4348) // LSLS r0,r1,#1; ADDS r0,r1,#3; MULS r0,r1
        instructions.forEachIndexed { index, value ->
            rom[index * 2] = value.toByte()
            rom[index * 2 + 1] = (value ushr 8).toByte()
        }
        val core = GbaEmulatorCore(
            rom, startInThumb = true,
            initialRegisters = IntArray(16).also { it[1] = 0x40000000 }
        )
        val shifted = core.step()
        assertEquals(0x80000000.toInt(), shifted.registers[0])
        assertTrue(shifted.cpsr and (1 shl 31) != 0)
        val added = core.step()
        assertEquals(0x40000003, added.registers[0])
        val multiplied = core.step()
        assertEquals(0xC0000000.toInt(), multiplied.registers[0])
        assertFalse(multiplied.halted)
    }

    @Test fun pushPopRestoresRegistersAndBranchesToSavedPc() {
        val rom = ByteArray(0xC2)
        val instructions = intArrayOf(0xB503, 0x2000, 0x2100, 0xBD03)
        instructions.forEachIndexed { index, value ->
            rom[index * 2] = value.toByte()
            rom[index * 2 + 1] = (value ushr 8).toByte()
        }
        rom[0xC0] = 0x09
        rom[0xC1] = 0x22 // MOVS r2, #9
        val initial = IntArray(16).also {
            it[0] = 0x11111111
            it[1] = 0x22222222
            it[13] = 0x02000100
            it[14] = GbaEmulatorCoreTestBase.ROM_BASE + 0xC0
        }
        val core = GbaEmulatorCore(rom, startInThumb = true, initialRegisters = initial)
        repeat(4) { core.step() }
        val restored = core.snapshot()
        assertEquals(0x11111111, restored.registers[0])
        assertEquals(0x22222222, restored.registers[1])
        assertEquals(0x02000100, restored.registers[13])
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 0xC0, restored.pc)
        val afterReturn = core.step()
        assertEquals(9, afterReturn.registers[2])
    }

    @Test fun executesThumbMultipleStoreAndLoadWithWriteback() {
        val rom = ByteArray(0xC0)
        val instructions = intArrayOf(0xC306, 0x2100, 0x2200, 0x3B08, 0xCB06)
        instructions.forEachIndexed { index, value ->
            rom[index * 2] = value.toByte()
            rom[index * 2 + 1] = (value ushr 8).toByte()
        }
        val core = GbaEmulatorCore(
            rom, startInThumb = true,
            initialRegisters = IntArray(16).also {
                it[1] = 0x11111111
                it[2] = 0x22222222
                it[3] = 0x02000000
            }
        )
        repeat(instructions.size) { core.step() }
        val state = core.snapshot()
        assertEquals(0x11111111, state.registers[1])
        assertEquals(0x22222222, state.registers[2])
        assertEquals(0x02000008, state.registers[3])
        assertFalse(state.halted)
    }

    @Test fun computesThumbPcRelativeAddress() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x00
        rom[1] = 0xA0.toByte() // ADD r0, PC, #0
        val state = GbaEmulatorCore(rom, startInThumb = true).step()
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 4, state.registers[0])
    }

    @Test fun combinesThumbLongBranchPrefixAndSuffix() {
        val rom = ByteArray(0xC0)
        rom[0] = 0x00
        rom[1] = 0xF0.toByte()
        rom[2] = 0x00
        rom[3] = 0xF8.toByte()
        val core = GbaEmulatorCore(rom, startInThumb = true)
        core.step()
        val state = core.step()
        assertEquals(GbaEmulatorCoreTestBase.ROM_BASE + 4, state.pc)
        assertEquals((GbaEmulatorCoreTestBase.ROM_BASE + 4) or 1, state.registers[14])
        assertTrue(state.thumb)
    }

    private fun romOf(vararg instructions: Int): ByteArray {
        val rom = ByteArray(0xC0)
        instructions.forEachIndexed { index, instruction ->
            val offset = index * 4
            rom[offset] = instruction.toByte()
            rom[offset + 1] = (instruction ushr 8).toByte()
            rom[offset + 2] = (instruction ushr 16).toByte()
            rom[offset + 3] = (instruction ushr 24).toByte()
        }
        return rom
    }
}

private object GbaEmulatorCoreTestBase {
    const val ROM_BASE = 0x08000000
}
