package com.contentagent.vibestream

import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.player.DeviceVolume
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

/**
 * Functional check that the player volume slider and the phone's media volume
 * are one and the same control, in both directions, against the real playback
 * service on a device/emulator:
 *  - slider fraction -> Android media stream volume changes to the same step
 *  - Android media stream volume change (what hardware buttons do) -> the
 *    slider's state flow follows
 * No network, credentials or account data; generated PCM only.
 */
@RunWith(AndroidJUnit4::class)
class DeviceVolumeFunctionalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private fun streamVolume() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private fun streamMax() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, timeoutMs: Long = 15000, predicate: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (predicate()) return
            Thread.sleep(100)
        }
        assertTrue(label, predicate())
    }

    private fun tone(name: String): Song {
        val rate = 44100
        val frames = rate * 8
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 4)
        data.putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { i ->
            val sample = (sin(2 * PI * 440.0 * i / rate) * 4096).toInt().toShort()
            data.putShort(sample).putShort(sample)
        }
        val file = File(context.filesDir, "$name.wav")
        file.writeBytes(data.array())
        return Song(id = name, title = "Generated $name", artist = "Test oscillator", durationSec = 8,
            localFilePath = file.absolutePath, isDownloaded = true)
    }

    @Test fun sliderAndPhoneMediaVolumeAreTheSameControlBothDirections() {
        context.getSharedPreferences("vibestream_taste", 0).edit().putBoolean("done", true).commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var controller: MusicPlayerController
        val savedVolume = streamVolume()
        val max = streamMax()
        try {
            onMain { controller = MusicPlayerController(context, YouTubeExtractor()) }
            onMain { controller.playSong(tone("volume-sync-tone")) }
            await("playback running so the media stream is active") { controller.isPlaying.value }
            await("controller reports stream geometry") { controller.maxDeviceVolume.value == max }
            android.util.Log.i("PlaybackBenchmark", "device volume support: ${controller.debugDeviceVolumeSupport()}")

            // App -> phone: moving the slider moves the Android media volume.
            for (fraction in listOf(0.2f, 0.8f)) {
                val expected = DeviceVolume.volumeForFraction(fraction, max)
                onMain { controller.setDeviceVolumeFraction(fraction) }
                await("slider $fraction sets stream volume $expected") { streamVolume() == expected }
                await("slider state reflects $expected") { controller.deviceVolume.value == expected }
                android.util.Log.i("PlaybackBenchmark", "slider $fraction -> stream volume ${streamVolume()}/$max")
            }

            // Phone -> app: what hardware buttons do (stream volume change from
            // outside the app) must move the slider's state.
            val outside = (max / 3).coerceIn(0, max)
            onMain { audio.setStreamVolume(AudioManager.STREAM_MUSIC, outside, 0) }
            await("outside stream change $outside reaches slider state") { controller.deviceVolume.value == outside }
            android.util.Log.i("PlaybackBenchmark", "outside set $outside -> slider state ${controller.deviceVolume.value}")

            // Stepper buttons move the same stream volume one step at a time.
            onMain { controller.volumeUp() }
            await("volumeUp raises stream volume") { streamVolume() == (outside + 1).coerceAtMost(max) }
            onMain { controller.volumeDown() }
            await("volumeDown lowers stream volume") { streamVolume() == outside }
            android.util.Log.i("PlaybackBenchmark", "steppers -> stream volume ${streamVolume()}/$max")
        } finally {
            onMain { audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0) }
            onMain { controller.release() }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
