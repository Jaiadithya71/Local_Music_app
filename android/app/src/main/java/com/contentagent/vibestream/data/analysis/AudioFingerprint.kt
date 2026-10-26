package com.contentagent.vibestream.data.analysis

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.*

/** Local spectral signature. Input is bounded mono 16 kHz PCM, never microphone data.
 * Protocol reference: shazamio-core (MIT, dotX12), pinned in THIRD_PARTY_NOTICES.
 */
object AudioFingerprint {
    private data class Peak(val pass: Int, val magnitude: Int, val frequency: Int)
    fun signature(pcm: ShortArray): String {
        require(pcm.size in 48000..192000)
        val ring = ShortArray(2048)
        val fftHistory = Array(256) { FloatArray(1025) }
        val spread = Array(256) { FloatArray(1025) }
        val peaks = Array(4) { mutableListOf<Peak>() }
        var sampleIndex = 0
        var fftIndex = 0
        var spreadIndex = 0
        var passes = 0
        for (chunk in 0 until pcm.size / 128) {
            System.arraycopy(pcm, chunk * 128, ring, sampleIndex, 128)
            sampleIndex = (sampleIndex + 128) and 2047
            val real = DoubleArray(2048) { i ->
                ring[(i + sampleIndex) and 2047].toDouble() *
                    (0.5 - 0.5 * cos(2.0 * PI * (i + 1) / 2049))
            }
            val imaginary = DoubleArray(2048)
            fft(real, imaginary)
            val output = fftHistory[fftIndex]
            for (i in 0..1024) output[i] = ((real[i] * real[i] + imaginary[i] * imaginary[i]) / 131072).toFloat().coerceAtLeast(1e-10f)
            fftIndex = (fftIndex + 1) and 255
            val current = spread[spreadIndex]
            System.arraycopy(output, 0, current, 0, 1025)
            for (i in 0..1022) current[i] = maxOf(current[i], current[i + 1], current[i + 2])
            for (previous in intArrayOf(1, 3, 6)) {
                val past = spread[(spreadIndex - previous) and 255]
                for (i in 0..1024) past[i] = maxOf(past[i], current[i])
            }
            spreadIndex = (spreadIndex + 1) and 255
            passes++
            if (passes < 46) continue
            val candidate = fftHistory[(fftIndex - 46) and 255]
            val surrounding = spread[(spreadIndex - 49) and 255]
            for (bin in 10..1014) {
                val value = candidate[bin]
                if (value < 1f / 64 || value < surrounding[bin - 1]) continue
                var neighbor = 0f
                for (delta in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) neighbor = maxOf(neighbor, surrounding[bin + delta])
                if (value <= neighbor) continue
                for (delta in intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249))
                    neighbor = maxOf(neighbor, spread[(spreadIndex + delta) and 255][bin - 1])
                if (value <= neighbor) continue
                fun magnitude(v: Float) = ln(v).coerceAtLeast(1f / 64) * 1477.3f + 6144f
                val middle = magnitude(value)
                val before = magnitude(candidate[bin - 1])
                val after = magnitude(candidate[bin + 1])
                val curvature = middle * 2 - before - after
                if (curvature <= 0) continue
                val frequency = bin * 64 + ((after - before) * 32 / curvature).toInt()
                val hz = frequency * (16000f / 2 / 1024 / 64)
                val band = when (hz.toInt()) { in 250..519 -> 0; in 520..1449 -> 1; in 1450..3499 -> 2; in 3500..5500 -> 3; else -> -1 }
                if (band >= 0) peaks[band].add(Peak(passes - 46, middle.toInt(), frequency))
            }
        }
        fun ByteArrayOutputStream.u32(value: Int) { write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()) }
        fun ByteArrayOutputStream.u16(value: Int) { write(value and 255); write((value ushr 8) and 255) }
        val bytes = ByteArrayOutputStream()
        listOf(0xcafe2580.toInt(),0,0,0x94119c00.toInt(),0,0,0,3 shl 27,0,0,pcm.size + 3840,(15 shl 19) + 0x40000,0x40000000,0).forEach { bytes.u32(it) }
        for (band in 0..3) {
            if (peaks[band].isEmpty()) continue
            val body = ByteArrayOutputStream()
            var pass = 0
            for (peak in peaks[band]) {
                if (peak.pass - pass >= 255) { body.write(255); body.u32(peak.pass); pass = peak.pass }
                body.write(peak.pass - pass); body.u16(peak.magnitude); body.u16(peak.frequency); pass = peak.pass
            }
            bytes.u32(0x60030040 + band);bytes.u32(body.size());bytes.write(body.toByteArray())
            repeat((4 - body.size() % 4) % 4) { bytes.write(0) }
        }
        val packet = bytes.toByteArray()
        val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(8,packet.size - 48);header.putInt(52,packet.size - 48)
        val crc = CRC32();crc.update(packet,8,packet.size - 8);header.putInt(4,crc.value.toInt())
        return "data:audio/vnd.shazam.sig;base64," + Base64.getEncoder().encodeToString(packet)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val r = real[i]; real[i] = real[j]; real[j] = r }
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2 * PI / length
            val wr = cos(angle); val wi = sin(angle)
            for (start in real.indices step length) {
                var ur = 1.0; var ui = 0.0
                for (offset in 0 until length / 2) {
                    val a = start + offset;val b = a + length / 2
                    val vr = real[b] * ur - imaginary[b] * ui;val vi = real[b] * ui + imaginary[b] * ur
                    real[b] = real[a] - vr;imaginary[b] = imaginary[a] - vi
                    real[a] += vr;imaginary[a] += vi
                    val next = ur * wr - ui * wi;ui = ur * wi + ui * wr;ur = next
                }
            }
            length *= 2
        }
    }
}
