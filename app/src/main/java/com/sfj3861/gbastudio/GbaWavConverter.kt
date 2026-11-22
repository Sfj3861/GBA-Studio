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
        val declaredEnd=u32(4)+8L
        require(declaredEnd in 12L..wav.size.toLong()) { "Tamaño RIFF inválido o archivo truncado." }
        val end=declaredEnd.toInt()
        var format:Int?=null; var channels:Int?=null; var rate:Int?=null; var bits:Int?=null; var align:Int?=null
        var data=-1; var dataSize=0; var p=12
        while(p+8<=end) {
            val size=u32(p+4); val start=p+8
            val paddedEnd=start.toLong()+size+(size and 1L)
            require(size<=Int.MAX_VALUE && paddedEnd<=end.toLong()) { "Chunk WAV truncado o sin relleno RIFF." }
            when(tag(p)) {
                "fmt " -> { require(size>=16) { "Chunk fmt demasiado corto." }; format=u16(start); channels=u16(start+2); rate=u32(start+4).toInt(); align=u16(start+12); bits=u16(start+14) }
                "data" -> if(data<0) { data=start; dataSize=size.toInt() }
            }
            p=start+size.toInt()+(size.toInt() and 1)
        }
        require(p==end) { "Cabecera de chunk WAV truncada." }
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
            val position=i.toLong()*hz
            val left=(position/targetRate).toInt().coerceAtMost(count-1)
            val right=(left+1).coerceAtMost(count-1)
            val fraction=(position%targetRate).toInt()
            var leftMixed=0; var rightMixed=0
            for(c in 0 until ch) {
                leftMixed+=sample(left,c)
                rightMixed+=sample(right,c)
            }
            leftMixed/=ch
            rightMixed/=ch
            val mixed=(leftMixed.toLong()*(targetRate-fraction)+rightMixed.toLong()*fraction)/targetRate
            output[i]=((mixed.toInt()+32768) ushr 8).coerceIn(0,255).toByte()
        }
        return GbaPcmSample(targetRate,output,ch,depth,output.size*1000L/targetRate)
    }

    fun cSource(sample:GbaPcmSample,symbol:String="gba_audio"):String {
        require(symbol.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && sample.samplesUnsigned8.isNotEmpty()) { "Nombre C o muestras no válidas." }
        return buildString {
            append("#include <stdint.h>\n\nconst int8_t ${symbol}_samples[${sample.samplesUnsigned8.size}] = {\n")
            sample.samplesUnsigned8.forEachIndexed{i,b->if(i%16==0)append("    ");append(((b.toInt() and 255)-128).toString());append(if(i==sample.samplesUnsigned8.lastIndex)"\n" else ", ");if(i%16==15)append("\n")}
            append("};\nconst uint32_t ${symbol}_sample_rate = ${sample.sampleRate}u;\nconst uint32_t ${symbol}_sample_count = ${sample.samplesUnsigned8.size}u;\n")
        }
    }

    fun wavBytes(sample:GbaPcmSample):ByteArray {
        val pcm=sample.samplesUnsigned8
        // RIFF chunks are word-aligned. The data chunk length excludes its optional pad byte,
        // while the enclosing RIFF size includes that byte.
        val padding=pcm.size and 1
        val out=ByteArrayOutputStream(44+pcm.size+padding)
        fun u16(v:Int){out.write(v and 255);out.write((v ushr 8) and 255)}
        fun u32(v:Int){u16(v);u16(v ushr 16)}
        fun tag(v:String){out.write(v.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");u32(36+pcm.size+padding);tag("WAVE");tag("fmt ");u32(16);u16(1);u16(1);u32(sample.sampleRate);u32(sample.sampleRate);u16(1);u16(8);tag("data");u32(pcm.size);out.write(pcm)
        if(padding!=0) out.write(0)
        return out.toByteArray()
    }
}
