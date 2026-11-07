package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaDebuggerSessionTest {
    private fun rom(vararg words:Int):ByteArray {
        val data=ByteArray(0xC0+words.size*4)
        words.forEachIndexed{i,v->for(b in 0..3)data[0xC0+i*4+b]=(v ushr (b*8)).toByte()}
        return data
    }
    @Test fun stopsBeforeBreakpointAndCanSingleStepThroughIt() {
        val core=GbaEmulatorCore(rom(0xE3A00001.toInt(),0xE3A01002.toInt()))
        val debugger=GbaDebuggerSession(core)
        debugger.addBreakpoint(0x08000004)
        val stopped=debugger.run(10)
        assertTrue(stopped.stoppedAtBreakpoint)
        assertEquals(0x08000004,stopped.cpu.pc)
        val stepped=debugger.step()
        assertEquals(2,stepped.cpu.steps)
        assertEquals(2,stepped.cpu.registers[1])
    }
    @Test fun recordsTraceAndReadsMappedMemory() {
        val core=GbaEmulatorCore(rom(0xE3A00001.toInt()))
        core.writeMemory32(0x02000000,0x12345678)
        val debugger=GbaDebuggerSession(core,8)
        assertEquals("78 56 34 12",debugger.readMemoryHex(0x02000000,4))
        assertEquals(1,debugger.step().trace.size)
    }
}
