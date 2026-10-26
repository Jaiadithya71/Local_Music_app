package com.contentagent.vibestream.data.analysis

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** Bounded internal prototype: decode a source window, never record the microphone. */
class AudioWindowDecoder {
    data class Window(val startMs: Long, val pcm16k: ShortArray)

    fun decode(source: String, startMs: Long, seconds: Int = 12,
               headers: Map<String, String> = emptyMap(), cancelled: () -> Boolean = { false }): Window {
        require(startMs >= 0 && seconds in 3..12)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.removePrefix("file://"), headers)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Missing audio format")
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var inputDone = false
            var outputDone = false
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val output = ArrayList<Short>(seconds * 16000)
            val info = MediaCodec.BufferInfo()
            val endUs = (startMs + seconds * 1000) * 1000
            val deadline = System.nanoTime() + 30_000_000_000L
            var nextOutputUs = startMs * 1000.0
            while (!outputDone && output.size < seconds * 16000) {
                check(!cancelled()) { "Analysis cancelled" }
                check(System.nanoTime() < deadline) { "Audio window decode timed out" }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index) ?: error("Missing input buffer")
                        val time = extractor.sampleTime
                        val size = if (time >= 0 && time < endUs + 1_000_000) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else AudioFormat.ENCODING_PCM_16BIT
                        require(rate > 0 && channels in 1..8)
                        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    }
                    else -> if (index >= 0) {
                        val buffer = decoder.getOutputBuffer(index)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.nativeOrder())
                            val frameBytes = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frames = buffer.remaining() / frameBytes
                            repeat(frames) { frame ->
                                var mono = 0.0
                                repeat(channels) {
                                    mono += if (encoding == AudioFormat.ENCODING_PCM_FLOAT)
                                        buffer.float.toDouble().coerceIn(-1.0, 1.0) * 32767 else buffer.short.toDouble()
                                }
                                val timeUs = info.presentationTimeUs + frame * 1_000_000.0 / rate
                                // Nearest-neighbor feasibility output, not a production
                                // fingerprint resampler. Handles low source rates too.
                                while (timeUs >= nextOutputUs && nextOutputUs < endUs && output.size < seconds * 16000) {
                                    output.add((mono / channels).toInt().coerceIn(-32768, 32767).toShort())
                                    nextOutputUs += 1_000_000.0 / 16000
                                }
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs >= endUs) outputDone = true
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            return Window(startMs, output.toShortArray())
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            codec?.release(); extractor.release()
        }
    }
}
