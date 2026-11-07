package com.sfj3861.gbastudio

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaSpriteStudioTest {
    @Test fun encodesObjShapeSizeAndPaletteFields() {
        val square=GbaSpriteStudio.oamAttributes(12,20,16,16,3,2,4)
        assertEquals(20,square.attr0 and 255)
        assertEquals(1,square.attr0 ushr 14)
        assertEquals(12,square.attr1 and 511)
        assertEquals(1,square.attr1 ushr 14)
        assertEquals(3,square.attr2 and 1023)
        assertEquals(2,(square.attr2 ushr 10) and 3)
        assertEquals(4,(square.attr2 ushr 12) and 15)
        val wide=GbaSpriteStudio.oamAttributes(0,0,32,16)
        assertEquals(1,wide.attr0 ushr 14)
        assertEquals(2,wide.attr1 ushr 14)
    }

    @Test fun slicesSheetIntoFramesWithSharedPalette() {
        val bitmap=Bitmap.createBitmap(16,8,Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0,0,Color.RED);bitmap.setPixel(8,0,Color.BLUE)
        val result=GbaSpriteStudio.build(bitmap,8,8,80)
        assertEquals(2,result.frames.size)
        assertEquals(32,result.frames[0].tileData4bpp.size)
        assertEquals(80,result.frames[1].durationMs)
        assertTrue(result.paletteBgr555.size<=16)
        assertTrue(GbaSpriteStudio.animationCSource(result).contains("gba_sprite_oam"))
    }

    @Test(expected=IllegalArgumentException::class)
    fun rejectsUnsupportedObjDimensions(){GbaSpriteStudio.oamAttributes(0,0,24,24)}
}
