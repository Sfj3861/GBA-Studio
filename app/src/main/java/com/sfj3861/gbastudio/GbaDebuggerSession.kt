package com.sfj3861.gbastudio

data class DebuggerSnapshot(
    val cpu: EmulatorSnapshot,
    val breakpoints: Set<Int>,
    val stoppedAtBreakpoint: Boolean,
    val trace: List<String>
)

/** Debugger controller layered over the experimental CPU stepper. */
class GbaDebuggerSession(private val core:GbaEmulatorCore,private val traceLimit:Int=256) {
    private val breakpoints=linkedSetOf<Int>()
    private val trace=ArrayDeque<String>()
    private var breakpointStop=false
    init { require(traceLimit in 1..4096) }

    fun snapshot()=DebuggerSnapshot(core.snapshot(),breakpoints.toSet(),breakpointStop,trace.toList())
    fun addBreakpoint(address:Int){require(address>=0);breakpoints+=address}
    fun removeBreakpoint(address:Int){breakpoints-=address}
    fun clearBreakpoints(){breakpoints.clear()}

    /** Single-step deliberately executes one instruction even if the current PC has a breakpoint. */
    fun step():DebuggerSnapshot {
        val before=core.snapshot()
        if(!before.halted) {
            val after=core.step()
            record(before,after)
        }
        breakpointStop=false
        return snapshot()
    }

    /** Executes at most maxInstructions, stopping before the next breakpoint. */
    fun run(maxInstructions:Int=10_000):DebuggerSnapshot {
        require(maxInstructions in 1..1_000_000)
        breakpointStop=false
        repeat(maxInstructions) {
            val before=core.snapshot()
            if(before.halted)return snapshot()
            if(before.pc in breakpoints) { breakpointStop=true; return snapshot() }
            val after=core.step()
            record(before,after)
            if(after.halted)return snapshot()
        }
        return snapshot()
    }

    fun readMemoryHex(address:Int,length:Int):String {
        require(length in 1..256) { "La lectura debe ser de 1 a 256 bytes." }
        return (0 until length).joinToString(" ") { i -> "%02X".format(core.readMemory8(address+i)) }
    }

    private fun record(before:EmulatorSnapshot,after:EmulatorSnapshot) {
        trace.addLast("0x%08X %s -> 0x%08X%s".format(before.pc,if(before.thumb)"THUMB" else "ARM",after.pc,if(after.halted)" [HALT]" else ""))
        while(trace.size>traceLimit)trace.removeFirst()
    }
}
