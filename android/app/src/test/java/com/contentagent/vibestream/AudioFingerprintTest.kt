package com.contentagent.vibestream
import com.contentagent.vibestream.data.analysis.AudioFingerprint
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
class AudioFingerprintTest {
 @Test fun generatedProbeMatchesIndependentSignaturePeaks() {
  val data=javaClass.getResourceAsStream("/fingerprints/probe.pcm")!!.readBytes()
  val shorts=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
  val pcm=ShortArray(shorts.remaining());shorts.get(pcm)
  val actual=AudioFingerprint.signature(pcm)
  val expected=javaClass.getResourceAsStream("/fingerprints/probe.uri")!!.bufferedReader().readText().trim()
  val a=Base64.getDecoder().decode(actual.substringAfter(','))
  val e=Base64.getDecoder().decode(expected.substringAfter(','))
  fun peaks(bytes:ByteArray):List<List<Int>> {
   val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);b.position(56)
   val result=mutableListOf<List<Int>>()
   while(b.remaining()>0){
    val band=b.int;val size=b.int;val end=b.position()+size;var pass=0
    while(b.position()<end){
     var delta=b.get().toInt() and 255
     if(delta==255){pass=b.int;delta=b.get().toInt() and 255}
     pass+=delta;val magnitude=b.short.toInt() and 65535;val frequency=b.short.toInt() and 65535
     result.add(listOf(band,pass,magnitude,frequency))
    }
    b.position(end+(4-size%4)%4)
   }
   return result
  }
  val actualPeaks=peaks(a);val expectedPeaks=peaks(e)
  val agreement=expectedPeaks.count { point -> actualPeaks.any { other ->
   point[0]==other[0] && point[1]==other[1] && kotlin.math.abs(point[2]-other[2])<=2 && kotlin.math.abs(point[3]-other[3])<=2
  }}.toDouble()/expectedPeaks.size
  assertTrue("Independent direct-PCM FFT peak agreement $agreement",agreement>=0.98)
  assertTrue(kotlin.math.abs(actualPeaks.size-expectedPeaks.size)<=2)

 }
 @Test fun silenceHasNoPeaksAndValidHeader() {
  val data=Base64.getDecoder().decode(AudioFingerprint.signature(ShortArray(48000)).substringAfter(','))
  assertEquals(56,data.size)
 }
}
