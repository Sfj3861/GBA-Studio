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
        val core = GbaEmulatorCore(romOf(0xEFFFFFFF.toInt()))
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
        rom[0] = 0x41
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
