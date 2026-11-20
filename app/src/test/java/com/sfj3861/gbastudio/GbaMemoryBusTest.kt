package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Test

class GbaMemoryBusTest {
    private fun core()=GbaEmulatorCore(ByteArray(0xC0))

    @Test fun mapsPaletteVramOamAndIoStorage() {
        val cpu=core()
        cpu.writeMemory16(0x04000000,0x0100)
        cpu.writeMemory16(0x05000002,0x001F)
        cpu.writeMemory32(0x06000000,0x11111111)
        cpu.writeMemory16(0x07000000,0x1234)
        assertEquals(0x0100,cpu.readMemory16(0x04000000))
        assertEquals(0x001F,cpu.readMemory16(0x05000002))
        assertEquals(0x11111111,cpu.readMemory32(0x06000000))
        assertEquals(0x1234,cpu.readMemory16(0x07000000))
    }

    @Test fun mirrorsUpperVramWindowToLastThirtyTwoKilobytes() {
        val cpu=core()
        cpu.writeMemory16(0x06018000,0xBEEF)
        assertEquals(0xBEEF,cpu.readMemory16(0x06010000))
    }

    @Test fun videoSnapshotCopiesMutableMemory() {
        val cpu=core()
        cpu.writeMemory16(0x05000000,0x1234)
        val snapshot=cpu.videoMemorySnapshot()
        cpu.writeMemory16(0x05000000,0x5678)
        assertEquals(0x1234,(snapshot.paletteRam[0].toInt() and 255) or ((snapshot.paletteRam[1].toInt() and 255) shl 8))
    }
}
