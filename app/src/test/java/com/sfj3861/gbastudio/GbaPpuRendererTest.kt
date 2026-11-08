package com.sfj3861.gbastudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaPpuRendererTest {
    @Test fun rendersModeZeroFourBppTextBackground() {
        val io=ByteArray(0x400)
        val palette=ByteArray(0x400)
        val vram=ByteArray(0x18000)
        io[0]=0x00;io[1]=0x01 // Enable BG0, mode 0.
        io[8]=0x00;io[9]=0x01 // BG0 screen base block 1.
        palette[2]=0x1F;palette[3]=0x00 // BGR555 red.
        vram.fill(0x11,0,32) // Tile 0: color index 1 for all pixels.
        val frame=GbaPpuRenderer.renderMode0(GbaVideoMemorySnapshot(io,palette,vram,ByteArray(0x400)))
        assertEquals(240*160,frame.argbPixels.size)
        assertEquals(0xFFFF0000.toInt(),frame.argbPixels[0])
        assertTrue(frame.warnings.isEmpty())
    }

    @Test fun transparentBackgroundPixelsUseBackdropPaletteColor() {
        val io=ByteArray(0x400);io[1]=1;io[9]=1
        val palette=ByteArray(0x400);palette[0]=0xE0.toByte();palette[1]=0x03
        val frame=GbaPpuRenderer.renderMode0(GbaVideoMemorySnapshot(io,palette,ByteArray(0x18000),ByteArray(0x400)))
        assertEquals(0xFF00FF00.toInt(),frame.argbPixels[0])
    }

    @Test fun reportsUnsupportedBitmapMode() {
        val io=ByteArray(0x400);io[0]=3
        val frame=GbaPpuRenderer.renderMode0(GbaVideoMemorySnapshot(io,ByteArray(0x400),ByteArray(0x18000),ByteArray(0x400)))
        assertTrue(frame.warnings.single().lowercase().contains("modo de vídeo 3"))
    }
}
