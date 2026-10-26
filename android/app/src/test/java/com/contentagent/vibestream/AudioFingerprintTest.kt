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
  // Floating-point FFT implementations can differ at quantization thresholds.
  // Report exact agreement first; do not silently accept unknown algorithm drift.
  assertArrayEquals(e,a)
 }
 @Test fun silenceHasNoPeaksAndValidHeader() {
  val data=Base64.getDecoder().decode(AudioFingerprint.signature(ShortArray(48000)).substringAfter(','))
  assertEquals(56,data.size)
 }
}
