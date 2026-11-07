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
        tag("RIFF");u32(36+samples.size);tag("WAVE");tag("fmt ");u32(16);u16(1);u16(1);u32(rate);u32(rate);u16(1);u16(8);tag("data");u32(samples.size);o.write(samples)
        return o.toByteArray()
    }
    @Test fun convertsAndResamplesPcm(){val r=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,64,128,192)),16000);assertEquals(8,r.samplesUnsigned8.size);assertEquals(16000,r.sampleRate);assertEquals(128,r.samplesUnsigned8[4].toInt() and 255)}
    @Test fun exportsWaveAndC(){val r=GbaWavConverter.convert(wav8(8000,byteArrayOf(0,128,255)),8000);assertEquals("RIFF",String(GbaWavConverter.wavBytes(r),0,4,Charsets.US_ASCII));assertTrue(GbaWavConverter.cSource(r).contains("gba_audio_samples[3]"))}
    @Test(expected=IllegalArgumentException::class) fun rejectsCompressedWave(){val w=wav8(8000,byteArrayOf(128));w[20]=3;GbaWavConverter.convert(w)}
}
