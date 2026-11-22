package com.sfj3861.gbastudio

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaWavConverterTest {
    private fun wav8(rate:Int,samples:ByteArray):ByteArray {
        val o=ByteArrayOutputStream()
        fun u16(v:Int){o.write(v and 255);o.write(v ushr 8)}
        fun u32(v:Int){u16(v);u16(v ushr 16)}
        fun tag(s:String){o.write(s.toByteArray(Charsets.US_ASCII))}
        val padding=samples.size and 1
        tag("RIFF");u32(36+samples.size+padding);tag("WAVE");tag("fmt ");u32(16);u16(1);u16(1);u32(rate);u32(rate);u16(1);u16(8);tag("data");u32(samples.size);o.write(samples)
        if(padding!=0)o.write(0)
        return o.toByteArray()
    }
    private fun putU32(bytes:ByteArray,offset:Int,value:Int) {
        repeat(4) { index -> bytes[offset+index]=(value ushr (index*8)).toByte() }
    }
    private fun u32(bytes:ByteArray,offset:Int):Long =
        (bytes[offset].toLong() and 255) or
            ((bytes[offset+1].toLong() and 255) shl 8) or
            ((bytes[offset+2].toLong() and 255) shl 16) or
            ((bytes[offset+3].toLong() and 255) shl 24)

    @Test fun convertsAndResamplesPcm(){val r=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,64,128.toByte(),192.toByte())),16000);assertEquals(8,r.samplesUnsigned8.size);assertEquals(16000,r.sampleRate);assertEquals(128,r.samplesUnsigned8[4].toInt() and 255)}
    @Test fun linearlyInterpolatesWhenUpsampling(){val r=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,255.toByte())),16000);assertEquals(4,r.samplesUnsigned8.size);assertTrue((r.samplesUnsigned8[1].toInt() and 255) in 126..129);assertTrue((r.samplesUnsigned8[2].toInt() and 255) in 253..255)}
    @Test fun exportsWaveAndC(){val r=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,128.toByte(),255.toByte())),8000);assertEquals("RIFF",String(GbaWavConverter.wavBytes(r),0,4,Charsets.US_ASCII));assertTrue(GbaWavConverter.cSource(r).contains("gba_audio_samples[3]"));assertTrue(GbaWavConverter.cSource(r).contains("-128, 0, 127"))}
    @Test fun padsOddLengthPcmDataToRiffWordBoundary() {
        val sample=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,128.toByte(),255.toByte())),8000)
        val wave=GbaWavConverter.wavBytes(sample)

        assertEquals(48,wave.size)
        assertEquals(40L,u32(wave,4))
        assertEquals(3L,u32(wave,40))
        assertEquals(0,wave.last().toInt() and 255)
    }
    @Test fun doesNotAddPaddingWhenPcmDataLengthIsEven() {
        val sample=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,128.toByte())),8000)
        val wave=GbaWavConverter.wavBytes(sample)

        assertEquals(46,wave.size)
        assertEquals(38L,u32(wave,4))
        assertEquals(2L,u32(wave,40))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsCompressedWave(){val w=wav8(8000,byteArrayOf(128.toByte()));w[20]=3;GbaWavConverter.convert(w)}
    @Test(expected=IllegalArgumentException::class) fun rejectsRiffSizeLargerThanAvailableFile() {
        val wave=wav8(8000,byteArrayOf(0,128.toByte()))
        putU32(wave,4,wave.size-7)
        GbaWavConverter.convert(wave)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOddChunkWithoutRequiredPadding() {
        val valid=wav8(8000,byteArrayOf(0,128.toByte(),255.toByte()))
        val truncated=valid.copyOf(valid.size-1)
        putU32(truncated,4,truncated.size-8)
        GbaWavConverter.convert(truncated)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsTrailingPartialChunkHeader() {
        val valid=wav8(8000,byteArrayOf(0,128.toByte()))
        val malformed=valid+byteArrayOf(1,2,3,4)
        putU32(malformed,4,(u32(valid,4)+4).toInt())
        GbaWavConverter.convert(malformed)
    }
}
