package com.contentagent.vibestream

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.player.MusicPlayerController
import com.contentagent.vibestream.player.PlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Playback against generated PCM only. No network music, credentials or account data. */
@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, timeoutMs: Long = 15000, predicate: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (predicate()) return
            Thread.sleep(100)
        }
        assertTrue(label, predicate())
    }
    private fun tone(name: String, frequency: Double): Song {
        val rate = 44100
        val frames = rate * 8
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 4)
        data.putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { i ->
            val sample = (sin(2 * PI * frequency * i / rate) * 4096).toInt().toShort()
            data.putShort(sample).putShort(sample)
        }
        val file = File(context.filesDir, "$name.wav")
        file.writeBytes(data.array())
        return Song(id = name, title = "Generated $name", artist = "Test oscillator", durationSec = 8,
            localFilePath = file.absolutePath, isDownloaded = true)
    }
    @Test fun aacAndOpusDecodeThroughRealPlaybackService() {
        context.getSharedPreferences("vibestream_taste", 0).edit().putBoolean("done", true).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var controller: MusicPlayerController
        try {
            onMain { controller = MusicPlayerController(context, YouTubeExtractor()) }
            for ((filename, mime, rate) in listOf(
                Triple("generated-aac.m4a", "audio/mp4a-latm", "44100 Hz"),
                Triple("generated-opus.webm", "audio/opus", "48000 Hz")
            )) {
                val file = File(context.filesDir, filename)
                instrumentation.context.assets.open(filename).use { input -> file.outputStream().use { input.copyTo(it) } }
                val song = Song(id = filename, title = "Generated codec fixture", artist = "Test oscillator",
                    localFilePath = file.absolutePath, isDownloaded = true)
                onMain { controller.playSong(song) }
                await("$filename reaches actual playback") { controller.isPlaying.value && controller.currentSong.value?.id == filename }
                await("$filename decoded format reported") { PlaybackService.audioFormatSummary.contains(mime) && PlaybackService.audioFormatSummary.contains(rate) }
                await("$filename clock advances") { controller.currentPosition.value >= 500 }
                assertNull(controller.errorMessage.value)
                android.util.Log.i("PlaybackBenchmark", "$filename decoded: ${PlaybackService.audioFormatSummary}")
            }
        } finally {
            onMain { controller.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
    @Test fun localPcmPlaybackPauseSeekNextAndTimer() {
        // Foreground activity permits starting the actual media playback service.
        context.getSharedPreferences("vibestream_taste", 0).edit().putBoolean("done", true).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var controller: MusicPlayerController
        val first = tone("fixture-a", 440.0)
        val second = tone("fixture-b", 660.0)
        try {
            onMain { controller = MusicPlayerController(context, YouTubeExtractor()) }
            onMain { controller.playSong(first, listOf(first, second)) }
            await("first fixture reaches playing") { controller.isPlaying.value && controller.currentSong.value?.id == first.id }
            await("playback clock advances") { controller.currentPosition.value > 500 }
            await("actual audio session assigned") { PlaybackService.audioSessionId > 0 }
            await("decoded PCM format reported") { PlaybackService.audioFormatSummary.contains("44100 Hz") }
            assertTrue(PlaybackService.audioFormatSummary.contains("2 channels"))
            android.util.Log.i("PlaybackBenchmark", "Decoded fixture: ${PlaybackService.audioFormatSummary}")
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "playback-smoke.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            onMain { controller.togglePlayPause() }
            await("pause takes effect") { !controller.isPlaying.value }
            onMain { controller.seekTo(2000) }
            onMain { controller.togglePlayPause() }
            await("resume after seek advances from target") { controller.isPlaying.value && controller.currentPosition.value >= 2000 }
            onMain { controller.next() }
            await("next fixture selected") { controller.currentSong.value?.id == second.id && controller.isPlaying.value }
            onMain { controller.setSleepTimerMinutes(0) }
            await("timer pauses actual playback") { !controller.isPlaying.value && controller.sleepEndAtMs.value == null }
            assertNull(controller.errorMessage.value)
        } finally {
            onMain { controller.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            File(first.localFilePath!!).delete()
            File(second.localFilePath!!).delete()
        }
    }
}
