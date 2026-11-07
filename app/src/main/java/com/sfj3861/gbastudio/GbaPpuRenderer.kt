package com.sfj3861.gbastudio

data class GbaVideoMemorySnapshot(val io:ByteArray,val paletteRam:ByteArray,val vram:ByteArray,val oam:ByteArray)
data class GbaRenderedFrame(val width:Int,val height:Int,val argbPixels:IntArray,val warnings:List<String>)

/** Software renderer for GBA display mode 0 text backgrounds (BG0–BG3). OBJ and bitmap modes are not rendered. */
object GbaPpuRenderer {
    const val SCREEN_WIDTH=240
    const val SCREEN_HEIGHT=160

    fun renderMode0(memory:GbaVideoMemorySnapshot):GbaRenderedFrame {
        require(memory.io.size>=0x10 && memory.paletteRam.size>=0x200 && memory.vram.size>=0x18000) {
            "Snapshot de memoria de vídeo incompleto."
        }
        val dispcnt=u16(memory.io,0)
        val mode=dispcnt and 7
        if(mode!=0)return GbaRenderedFrame(SCREEN_WIDTH,SCREEN_HEIGHT,IntArray(SCREEN_WIDTH*SCREEN_HEIGHT),listOf("Modo de vídeo $mode no implementado; solo se renderiza modo 0."))
        if(dispcnt and (1 shl 7)!=0)return GbaRenderedFrame(SCREEN_WIDTH,SCREEN_HEIGHT,IntArray(SCREEN_WIDTH*SCREEN_HEIGHT){0xFFFFFFFF.toInt()},emptyList())
        val pixels=IntArray(SCREEN_WIDTH*SCREEN_HEIGHT){0xFF000000.toInt()}
        val enabled=(0..3).filter{dispcnt and (1 shl (8+it))!=0}
        val backgrounds=enabled.map{index->
            val control=u16(memory.io,0x08+index*2)
            index to (control and 3)
        }.sortedWith(compareByDescending<Pair<Int,Int>>{it.second}.thenByDescending{it.first})
        backgrounds.forEach{(index,_)->drawTextBackground(memory,index,pixels)}
        val warnings=mutableListOf<String>()
        if(dispcnt and (1 shl 12)!=0)warnings+="Sprites OBJ habilitados, pero el renderizador OBJ aún no está implementado."
        if(dispcnt and (1 shl 5)!=0)warnings+="Ventanas GBA todavía no implementadas."
        return GbaRenderedFrame(SCREEN_WIDTH,SCREEN_HEIGHT,pixels,warnings)
    }

    private fun drawTextBackground(memory:GbaVideoMemorySnapshot,bg:Int,target:IntArray) {
        val control=u16(memory.io,0x08+bg*2)
        val charBase=((control ushr 2) and 3)*0x4000
        val color256=control and (1 shl 7)!=0
        val screenBase=((control ushr 8) and 31)*0x800
        val sizeCode=(control ushr 14) and 3
        val mapWidth=if(sizeCode==1 || sizeCode==3)64 else 32
        val mapHeight=if(sizeCode==2 || sizeCode==3)64 else 32
        val scrollX=u16(memory.io,0x10+bg*4) and 0x1FF
        val scrollY=u16(memory.io,0x12+bg*4) and 0x1FF
        for(screenY in 0 until SCREEN_HEIGHT)for(screenX in 0 until SCREEN_WIDTH) {
            val x=(screenX+scrollX)%(mapWidth*8)
            val y=(screenY+scrollY)%(mapHeight*8)
            val tileX=x/8;val tileY=y/8
            val blockColumns=mapWidth/32
            val block=(tileY/32)*blockColumns+(tileX/32)
            val local=(tileY%32)*32+(tileX%32)
            val mapOffset=screenBase+block*0x800+local*2
            if(mapOffset<0 || mapOffset+1>=memory.vram.size)continue
            val entry=u16(memory.vram,mapOffset)
            val tile=entry and 0x3FF
            val px=x and 7;val py=y and 7
            val sx=if(entry and (1 shl 10)!=0)7-px else px
            val sy=if(entry and (1 shl 11)!=0)7-py else py
            val tileOffset=charBase+tile*if(color256)64 else 32
            val colorIndex=if(color256) {
                memory.vram.getOrNull(tileOffset+sy*8+sx)?.toInt()?.and(255)?:0
            } else {
                val packed=memory.vram.getOrNull(tileOffset+sy*4+sx/2)?.toInt()?.and(255)?:0
                val nibble=if(sx and 1==0)packed and 15 else packed ushr 4
                val bank=(entry ushr 12) and 15
                bank*16+nibble
            }
            if(colorIndex==0)continue
            val paletteOffset=colorIndex*2
            if(paletteOffset+1>=memory.paletteRam.size)continue
            val color=u16(memory.paletteRam,paletteOffset)
            target[screenY*SCREEN_WIDTH+screenX]=0xFF000000.toInt() or
                (expand5(color and 31) shl 16) or
                (expand5((color ushr 5) and 31) shl 8) or
                expand5((color ushr 10) and 31)
        }
    }

    private fun expand5(value:Int)=(value shl 3) or (value ushr 2)
    private fun u16(data:ByteArray,offset:Int)=(data[offset].toInt() and 255) or ((data[offset+1].toInt() and 255) shl 8)
}
