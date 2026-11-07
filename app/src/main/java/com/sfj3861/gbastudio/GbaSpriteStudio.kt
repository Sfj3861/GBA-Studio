package com.sfj3861.gbastudio

import android.graphics.Bitmap

data class GbaOamAttributes(val attr0:Int,val attr1:Int,val attr2:Int,val x:Int,val y:Int,val width:Int,val height:Int)
data class GbaSpriteFrame(val index:Int,val tileData4bpp:ByteArray,val tileCount:Int,val durationMs:Int,val oam:GbaOamAttributes)
data class GbaSpriteSheetResult(val frameWidth:Int,val frameHeight:Int,val paletteBgr555:List<Int>,val frames:List<GbaSpriteFrame>,val warnings:List<String>)

/** Sprite-sheet slicing, GBA OBJ shape/size and OAM attribute generation. */
object GbaSpriteStudio {
    private val legalSizes=setOf(8 to 8,16 to 16,32 to 32,64 to 64,16 to 8,32 to 8,32 to 16,64 to 32,8 to 16,8 to 32,16 to 32,32 to 64)

    fun build(bitmap:Bitmap,frameWidth:Int,frameHeight:Int,durationMs:Int=100,x:Int=0,y:Int=0,priority:Int=0,paletteBank:Int=0):GbaSpriteSheetResult {
        require(frameWidth to frameHeight in legalSizes) { "Tamaño OBJ no válido. Usa dimensiones GBA admitidas." }
        require(bitmap.width%frameWidth==0 && bitmap.height%frameHeight==0) { "El spritesheet debe dividirse exactamente en celdas." }
        require(durationMs in 1..60_000) { "Duración de frame fuera de rango." }
        require(x in 0..511 && y in 0..255 && priority in 0..3 && paletteBank in 0..15) { "Coordenadas, prioridad o paleta fuera de rango." }
        val converted=AssetStudio.convertTo4Bpp(bitmap)
        val sheetTilesWide=converted.width/8
        val tilesWide=frameWidth/8
        val tilesHigh=frameHeight/8
        val across=converted.width/frameWidth
        val down=converted.height/frameHeight
        val frames=ArrayList<GbaSpriteFrame>(across*down)
        for (fy in 0 until down) for (fx in 0 until across) {
            val bytes=ByteArray(tilesWide*tilesHigh*32)
            var dst=0
            for (ty in 0 until tilesHigh) for (tx in 0 until tilesWide) {
                val source=((fy*tilesHigh+ty)*sheetTilesWide+fx*tilesWide+tx)*32
                converted.tiles4bpp.copyInto(bytes,dst,source,source+32); dst+=32
            }
            val index=frames.size
            frames+=GbaSpriteFrame(index,bytes,tilesWide*tilesHigh,durationMs,
                oamAttributes(x,y,frameWidth,frameHeight,0,priority,paletteBank))
        }
        val warnings=converted.warnings.toMutableList()
        if(frames.size>1) warnings+="Los frames comparten la misma región OBJ; actualiza sus tiles en VRAM al cambiar de frame."
        return GbaSpriteSheetResult(frameWidth,frameHeight,converted.colors,frames,warnings)
    }

    fun oamAttributes(x:Int,y:Int,width:Int,height:Int,tileIndex:Int=0,priority:Int=0,paletteBank:Int=0,horizontalFlip:Boolean=false,verticalFlip:Boolean=false):GbaOamAttributes {
        require(width to height in legalSizes) { "Dimensiones OBJ no válidas." }
        require(x in 0..511 && y in 0..255 && tileIndex in 0..1023 && priority in 0..3 && paletteBank in 0..15) { "Atributos OAM fuera de rango." }
        val (shape,size)=when {
            width==height -> 0 to when(width){8->0;16->1;32->2;else->3}
            width>height -> 1 to when(width to height){16 to 8->0;32 to 8->1;32 to 16->2;else->3}
            else -> 2 to when(width to height){8 to 16->0;8 to 32->1;16 to 32->2;else->3}
        }
        val attr0=(y and 255) or (shape shl 14)
        val attr1=(x and 511) or (if(horizontalFlip) 1 shl 12 else 0) or (if(verticalFlip) 1 shl 13 else 0) or (size shl 14)
        val attr2=(tileIndex and 1023) or (priority shl 10) or (paletteBank shl 12)
        return GbaOamAttributes(attr0,attr1,attr2,x,y,width,height)
    }

    fun animationCSource(sheet:GbaSpriteSheetResult,symbol:String="gba_sprite"):String {
        require(symbol.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Nombre C no válido." }
        require(sheet.frames.isNotEmpty()) { "Animación vacía." }
        return buildString {
            append("#include <stdint.h>\n\n")
            append("const uint16_t \${symbol}_palette[\${sheet.paletteBgr555.size}] = {\n")
            append(sheet.paletteBgr555.joinToString(", "){"0x%04X".format(it)}).append("\n};\n\n")
            sheet.frames.forEach { frame ->
                append("const uint8_t \${symbol}_frame_\${frame.index}[\${frame.tileData4bpp.size}] = {\n")
                frame.tileData4bpp.forEachIndexed { i,b -> if(i%16==0) append("    "); append("0x%02X".format(b.toInt() and 255)); append(if(i==frame.tileData4bpp.lastIndex) "\n" else ", "); if(i%16==15) append("\n") }
                append("};\n")
            }
            append("\nconst uint16_t \${symbol}_frame_duration_ms[\${sheet.frames.size}] = { ")
            append(sheet.frames.joinToString(", "){it.durationMs.toString()}).append(" };\n")
            append("const uint16_t \${symbol}_oam[\${sheet.frames.size}][3] = {\n")
            sheet.frames.forEach { f -> append("    { 0x%04X, 0x%04X, 0x%04X },\n".format(f.oam.attr0,f.oam.attr1,f.oam.attr2)) }
            append("};\n")
        }
    }
}
