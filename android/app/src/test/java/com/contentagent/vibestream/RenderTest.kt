package com.contentagent.vibestream

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.lyrics.LyricsRepository
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.player.EqualizerManager
import com.contentagent.vibestream.ui.components.EqualizerDialog
import com.contentagent.vibestream.ui.components.FullPlayerSheet
import com.contentagent.vibestream.ui.components.LyricsView
import com.contentagent.vibestream.ui.components.SleepTimerDialog
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowPopupWindow
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    private val outDir = File(System.getProperty("user.home"), "renders").apply { mkdirs() }

    private fun pump(activity: Activity) {
        repeat(6) { Shadows.shadowOf(Looper.getMainLooper()).idle() }
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        activity.window.decorView.layout(0, 0, 1080, 2400)
    }

    private fun snap(v: View, name: String, w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(0x0B, 0x0F, 0x14))
        v.draw(c)
        val f = File(outDir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("RENDER ${f.absolutePath} ${f.length()}")
    }

    private fun snapActivity(activity: Activity, name: String) =
        snap(activity.window.decorView, name, 1080, 2400)

    private fun snapDialog(activity: Activity, name: String) {
        val dlg = ShadowDialog.getLatestDialog() ?: ShadowDialog.getShownDialogs().lastOrNull()
        val popup = ShadowPopupWindow.getLatestPopupWindow()?.contentView
        val v = dlg?.window?.decorView ?: popup ?: activity.window.decorView
        v.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        snap(v, name, v.measuredWidth.coerceAtLeast(600), v.measuredHeight.coerceAtLeast(400))
    }

    private fun scenario(block: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { sc ->
            sc.onActivity(block)
        }
    }

    private fun demoSong() = Song(
        id = "s1", title = "Neon Skyline", artist = "Night Drive",
        durationSec = 214, durationText = "3:34")

    private fun demoLyrics() = LyricsRepository.Lyrics(
        synced = listOf(
            LyricsRepository.LyricLine(0, "City lights in the rain"),
            LyricsRepository.LyricLine(30_000, "We were chasing neon skies"),
            LyricsRepository.LyricLine(60_000, "And the night was ours to take"),
            LyricsRepository.LyricLine(90_000, "Every heartbeat synchronized"),
            LyricsRepository.LyricLine(120_000, "Till the morning wakes")
        ))

    @Test(timeout = 120000)
    fun render_lyricsSynced() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    LyricsView(loading = false, lyrics = demoLyrics(), positionMs = 62_000)
                }
            }
            pump(act)
            snapActivity(act, "lyrics-synced")
        }
    }

    @Test(timeout = 120000)
    fun render_lyricsLoading() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme { LyricsView(loading = true, lyrics = null, positionMs = 0) }
            }
            pump(act)
            snapActivity(act, "lyrics-loading")
        }
    }

    @Test(timeout = 120000)
    fun render_lyricsPlainFallback() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    LyricsView(
                        loading = false,
                        lyrics = LyricsRepository.Lyrics(
                            plain = "City lights in the rain\nWe were chasing neon skies\nAnd the night was ours to take"),
                        positionMs = 0)
                }
            }
            pump(act)
            snapActivity(act, "lyrics-plain")
        }
    }

    @Test(timeout = 120000)
    fun render_sleepTimerDialog_picker() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    SleepTimerDialog(
                        endAtMs = null, endOfTrack = false,
                        onPickMinutes = {}, onPickEndOfTrack = {},
                        onCancelTimer = {}, onDismiss = {})
                }
            }
            pump(act)
            snapDialog(act, "sleep-timer-dialog")
        }
    }

    @Test(timeout = 120000)
    fun render_sleepTimerDialog_countdown() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    SleepTimerDialog(
                        endAtMs = System.currentTimeMillis() + 15 * 60_000, endOfTrack = false,
                        onPickMinutes = {}, onPickEndOfTrack = {},
                        onCancelTimer = {}, onDismiss = {})
                }
            }
            pump(act)
            snapDialog(act, "sleep-timer-countdown")
        }
    }

    @Test(timeout = 120000)
    fun render_equalizerDialog() {
        scenario { act ->
            val eq = EqualizerManager(act)
            eq.testRangeOverride = (-1500).toShort() to (1500).toShort()
            eq.testPresetsOverride = listOf("Normal", "Rock", "Bass Boost", "Vocal")
            eq.testPresetIndexOverride = 1
            eq.testBandFreqsHz = listOf(60, 230, 910, 3600, 14000)
            eq.setStateForTest(true, listOf(400, 150, 0, -200, 300).map { it.toShort() })
            act.setContent {
                VibeStreamTheme { EqualizerDialog(eq = eq, onDismiss = {}) }
            }
            pump(act)
            snapDialog(act, "equalizer-dialog")
        }
    }

    @Test(timeout = 120000)
    fun render_fullPlayer_lyricsExpanded() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    FullPlayerSheet(
                        song = demoSong(),
                        isPlaying = true,
                        isBuffering = false,
                        currentPosition = 62_000,
                        duration = 214_000,
                        isDownloaded = false,
                        isDownloading = false,
                        downloadProgress = 0f,
                        onTogglePlayPause = {}, onSeekTo = {},
                        onSkipForward = {}, onSkipBackward = {},
                        onDownloadClick = {}, onDismiss = {},
                        showLyrics = true,
                        sleepTimerActive = true,
                        lyricsContent = {
                            LyricsView(loading = false, lyrics = demoLyrics(), positionMs = 62_000)
                        }
                    )
                }
            }
            pump(act)
            snapDialog(act, "full-player-lyrics")
        }
    }
}
