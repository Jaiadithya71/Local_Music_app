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
            val raw = ArrayList<Float>()

            val info = MediaCodec.BufferInfo()
            val endUs = (startMs + seconds * 1000) * 1000
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!outputDone) {
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
                                if (timeUs >= startMs * 1000 && timeUs < endUs) {
                                    raw.add((mono / channels).toFloat())
                                }
                            }
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || info.presentationTimeUs >= endUs) outputDone = true
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            return Window(startMs, resample(raw,rate))
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            codec?.release(); extractor.release()
        }
    }
    private fun resample(input: List<Float>, sourceRate: Int): ShortArray {
        val count=(input.size.toLong()*16000/sourceRate).toInt()
        val cutoff=minOf(1.0,16000.0/sourceRate)*0.94
        val radius=24
        return ShortArray(count) { index ->
            val position=index*sourceRate/16000.0
            var sum=0.0;var weight=0.0
            val center=position.toInt()
            for (j in center-radius..center+radius) {
                if (j !in input.indices) continue
                val distance=position-j
                val x=Math.PI*distance*cutoff
                val sinc=if(kotlin.math.abs(x)<1e-10) 1.0 else kotlin.math.sin(x)/x
                val window=0.5+0.5*kotlin.math.cos(Math.PI*distance/(radius+1))
                val w=sinc*cutoff*window
                sum+=input[j]*w;weight+=w
            }
            (if(weight==0.0) 0 else (sum/weight).toInt()).coerceIn(-32768,32767).toShort()
        }
    }

}
