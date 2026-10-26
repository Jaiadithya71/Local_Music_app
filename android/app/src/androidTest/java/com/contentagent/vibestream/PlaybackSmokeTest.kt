package com.contentagent.vibestream

import android.content.Intent
import androidx.activity.compose.setContent
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
        android.util.Log.e("PlaybackBenchmark", "Timeout: $label; session=${PlaybackService.audioSessionId}; format=${PlaybackService.audioFormatSummary}")
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
    @Test fun compilationAppSessionAndHeadsetNextStayInsideRecording() {
        context.getSharedPreferences("vibestream_taste", 0).edit().putBoolean("done", true).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var app: MusicPlayerController
        lateinit var remote: androidx.media3.session.MediaController
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        val first = tone("fixture-compilation", 440.0).copy(chapters = listOf(
            com.contentagent.vibestream.data.chapters.SongChapter("First", 0),
            com.contentagent.vibestream.data.chapters.SongChapter("Second", 2000),
            com.contentagent.vibestream.data.chapters.SongChapter("Third", 4000),
            com.contentagent.vibestream.data.chapters.SongChapter("Fourth", 6000)))
        val next = tone("fixture-after-compilation", 660.0)
        try {
            onMain {
                app = MusicPlayerController(context, YouTubeExtractor())
                future = androidx.media3.session.MediaController.Builder(context,
                    androidx.media3.session.SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            remote = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
            onMain { app.playSong(first, listOf(first, next)) }
            await("compilation starts") { app.isPlaying.value && app.currentSong.value?.id == first.id }
            onMain { remote.pause(); remote.seekTo(0) }
            await("prefetch exists before skip, so boundary must take precedence") {
                var result = false; onMain { result = remote.hasNextMediaItem() }; result
            }
            fun assertAt(label: String, target: Long) {
                await(label) { var ok = false; onMain {
                    ok = remote.currentMediaItem?.mediaId == first.id && kotlin.math.abs(remote.currentPosition - target) < 300
                }; ok }
            }
            onMain { app.next() }; assertAt("in-app Next uses second boundary", 2000)
            onMain { remote.seekToNext() }; assertAt("session Next uses third boundary", 4000)
            onMain { assertTrue("Session advertises chapter Previous", remote.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) }
            onMain { remote.seekToPreviousMediaItem() }; assertAt("session Previous returns second boundary", 2000)
            // Android's media-button route, used by wired/Bluetooth Next, not a direct callback.
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
            audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
            assertAt("hardware media-key Next uses third boundary", 4000)
            onMain { remote.seekToNextMediaItem() }; assertAt("media-item Next uses fourth boundary", 6000)
            scenario.onActivity { activity ->
                activity.setContent {
                    com.contentagent.vibestream.ui.theme.VibeStreamTheme {
                        com.contentagent.vibestream.ui.components.FullPlayerSheet(
                            song = first.copy(durationSec = 600), isPlaying = false, isBuffering = false,
                            currentPosition = 6000, duration = 8000, isDownloaded = true,
                            isDownloading = false, downloadProgress = 0f,
                            onTogglePlayPause = {}, onSeekTo = {}, onSkipForward = {}, onSkipBackward = {},
                            onDownloadClick = {}, onDismiss = {})
                    }
                }
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1000) // Allow sheet entrance animation before visual evidence, not a readiness gate.
            File(context.getExternalFilesDir(null), "compilation-chapters.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.uiAutomation.executeShellCommand("cp /sdcard/Android/data/com.contentagent.vibestream/files/compilation-chapters.png /data/local/tmp/compilation-chapters.png").close()
            val secondChip = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("2. Second").firstOrNull()
            assertNotNull("Named selector is accessible", secondChip)
            assertTrue(secondChip!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            assertAt("Named selector seeks second song", 2000)
            onMain { remote.seekTo(6000) }
            fun findSwitch(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isCheckable) return node
                for (i in 0 until node.childCount) findSwitch(node.getChild(i))?.let { return it }
                return null
            }
            val toggle = findSwitch(instrumentation.uiAutomation.rootInActiveWindow)
            assertNotNull("Opt-in switch is accessible", toggle)
            assertFalse("Automatic recognition defaults off", toggle!!.isChecked)
            assertTrue(toggle.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await("Fingerprint disclosure dialog appears") {
                instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Enable automatic song naming?").isNotEmpty()
            }
            File(context.getExternalFilesDir(null), "recognition-consent.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.uiAutomation.executeShellCommand("cp /sdcard/Android/data/com.contentagent.vibestream/files/recognition-consent.png /data/local/tmp/recognition-consent.png").close()
            val cancel = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Cancel").first()
            assertTrue(cancel.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            assertFalse(com.contentagent.vibestream.data.analysis.CompilationCache(context).enabled)
            onMain { remote.seekToNext() }
            await("last boundary falls through to prefetched queue item") { app.currentSong.value?.id == next.id }
            assertNull(app.errorMessage.value)
            android.util.Log.i("PlaybackBenchmark", "Compilation: app/session/headset boundaries and queue fallback passed")
        } finally {
            onMain { app.release(); androidx.media3.session.MediaController.releaseFuture(future) }
            scenario.close(); context.stopService(Intent(context, PlaybackService::class.java))
            File(first.localFilePath!!).delete(); File(next.localFilePath!!).delete()
        }
    }

    @Test fun boundedDecoderHandlesAacOpusAndPcmWithoutMicrophone() {
        for (filename in listOf("generated-aac.m4a", "generated-opus.webm")) {
            val file = File(context.filesDir, "decode-$filename")
            instrumentation.context.assets.open(filename).use { input -> file.outputStream().use { input.copyTo(it) } }
            val window = com.contentagent.vibestream.data.analysis.AudioWindowDecoder().decode(file.absolutePath, 1000, 3)
            assertEquals(1000L, window.startMs)
            assertTrue("$filename produced bounded mono PCM: ${window.pcm16k.size}", window.pcm16k.size in 46000..48000)
            assertTrue(window.pcm16k.any { kotlin.math.abs(it.toInt()) > 100 })
            file.delete()
        }
        val fixture = tone("decode-pcm", 440.0)
        val window = com.contentagent.vibestream.data.analysis.AudioWindowDecoder().decode(fixture.localFilePath!!, 2000, 3)
        assertEquals(48000, window.pcm16k.size)
        assertTrue(window.pcm16k.any { kotlin.math.abs(it.toInt()) > 100 })
        File(fixture.localFilePath!!).delete()
        android.util.Log.i("PlaybackBenchmark", "Bounded AAC/Opus/PCM decoder generated 16k mono windows without microphone")
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
            android.util.Log.i("PlaybackBenchmark", "Final state: playing=${controller.isPlaying.value}, song=${controller.currentSong.value?.id}, error=${controller.errorMessage.value}")
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
            await("first fixture reaches playing; generated file ${File(first.localFilePath!!).length()} bytes") { controller.isPlaying.value && controller.currentSong.value?.id == first.id }
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
            android.util.Log.i("PlaybackBenchmark", "Final state: playing=${controller.isPlaying.value}, song=${controller.currentSong.value?.id}, error=${controller.errorMessage.value}")
            onMain { controller.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            File(first.localFilePath!!).delete()
            File(second.localFilePath!!).delete()
        }
    }
}
