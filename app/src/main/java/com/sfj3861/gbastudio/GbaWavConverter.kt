package com.sfj3861.gbastudio

import java.io.ByteArrayOutputStream

data class GbaPcmSample(val sampleRate:Int,val samplesUnsigned8:ByteArray,val sourceChannels:Int,val sourceBitsPerSample:Int,val durationMs:Long)

/** Bounded RIFF/WAVE PCM importer: mono unsigned 8-bit output for GBA audio pipelines. */
object GbaWavConverter {
    const val MAX_INPUT_BYTES=16*1024*1024
    const val MAX_OUTPUT_SAMPLES=8*1024*1024

    fun convert(wav:ByteArray,targetRate:Int=16_384):GbaPcmSample {
        require(wav.size in 44..MAX_INPUT_BYTES) { "WAV vacío o superior a 16 MiB." }
        require(targetRate in 4_000..32_768) { "Frecuencia de destino fuera de 4000–32768 Hz." }
        fun u16(p:Int)=(wav[p].toInt() and 255) or ((wav[p+1].toInt() and 255) shl 8)
        fun u32(p:Int)=u16(p).toLong() or (u16(p+2).toLong() shl 16)
        fun tag(p:Int)=String(wav,p,4,Charsets.US_ASCII)
        require(tag(0)=="RIFF" && tag(8)=="WAVE") { "No es un archivo RIFF/WAVE." }
        val end=(u32(4)+8).coerceAtMost(wav.size.toLong()).toInt()
        var format:Int?=null; var channels:Int?=null; var rate:Int?=null; var bits:Int?=null; var align:Int?=null
        var data=-1; var dataSize=0; var p=12
        while(p+8<=end) {
            val size=u32(p+4); val start=p+8
            require(size<=Int.MAX_VALUE && start.toLong()+size<=end.toLong()) { "Chunk WAV truncado." }
            when(tag(p)) {
                "fmt " -> { require(size>=16) { "Chunk fmt demasiado corto." }; format=u16(start); channels=u16(start+2); rate=u32(start+4).toInt(); align=u16(start+12); bits=u16(start+14) }
                "data" -> if(data<0) { data=start; dataSize=size.toInt() }
            }
            p=start+size.toInt()+(size.toInt() and 1)
        }
        require(format==1) { "Solo se admite PCM lineal sin compresión." }
        val ch=channels?:error("Falta fmt."); val hz=rate?:error("Falta frecuencia."); val depth=bits?:error("Falta profundidad."); val block=align?:error("Falta blockAlign.")
        require(ch in 1..2 && depth in setOf(8,16) && hz in 4_000..192_000) { "Se admite PCM mono/estéreo de 8/16 bits y 4–192 kHz." }
        val bps=depth/8
        require(block==ch*bps && data>=0 && dataSize>0 && dataSize%block==0) { "Bloque PCM incompleto o blockAlign incorrecto." }
        val count=dataSize/block
        val outCount=((count.toLong()*targetRate+hz/2)/hz).coerceAtLeast(1)
        require(outCount<=MAX_OUTPUT_SAMPLES) { "El audio supera 8 millones de muestras." }
        fun sample(frame:Int,channel:Int):Int {
            val at=data+frame*block+channel*bps
            return if(depth==8) ((wav[at].toInt() and 255)-128) shl 8
            else ((wav[at].toInt() and 255) or (wav[at+1].toInt() shl 8)).toShort().toInt()
        }
        val output=ByteArray(outCount.toInt())
        for(i in output.indices) {
            val src=((i.toLong()*hz)/targetRate).toInt().coerceAtMost(count-1)
            var mixed=0; for(c in 0 until ch) mixed+=sample(src,c); mixed/=ch
            output[i]=((mixed+32768) ushr 8).coerceIn(0,255).toByte()
        }
        return GbaPcmSample(targetRate,output,ch,depth,output.size*1000L/targetRate)
    }

    fun cSource(sample:GbaPcmSample,symbol:String="gba_audio"):String {
        require(symbol.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && sample.samplesUnsigned8.isNotEmpty()) { "Nombre C o muestras no válidas." }
        return buildString {
            append("#include <stdint.h>\n\nconst uint8_t \${symbol}_samples[\${sample.samplesUnsigned8.size}] = {\n")
            sample.samplesUnsigned8.forEachIndexed{i,b->if(i%16==0)append("    ");append("0x%02X".format(b.toInt() and 255));append(if(i==sample.samplesUnsigned8.lastIndex)"\n" else ", ");if(i%16==15)append("\n")}
            append("};\nconst uint32_t \${symbol}_sample_rate = \${sample.sampleRate}u;\nconst uint32_t \${symbol}_sample_count = \${sample.samplesUnsigned8.size}u;\n")
        }
    }

    fun wavBytes(sample:GbaPcmSample):ByteArray {
        val pcm=sample.samplesUnsigned8; val out=ByteArrayOutputStream(44+pcm.size)
        fun u16(v:Int){out.write(v and 255);out.write((v ushr 8) and 255)}
        fun u32(v:Int){u16(v);u16(v ushr 16)}
        fun tag(v:String){out.write(v.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");u32(36+pcm.size);tag("WAVE");tag("fmt ");u32(16);u16(1);u16(1);u32(sample.sampleRate);u32(sample.sampleRate);u16(1);u16(8);tag("data");u32(pcm.size);out.write(pcm)
        return out.toByteArray()
    }
}
