package com.contentagent.vibestream

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.lyrics.LyricsRepository
import com.contentagent.vibestream.data.model.LrcLine
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.player.SleepTimerDialog
import com.contentagent.vibestream.ui.player.LyricsPanel
import com.contentagent.vibestream.ui.player.PlayerScreen
import com.contentagent.vibestream.ui.settings.EqualizerDialog
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import com.contentagent.vibestream.viewmodel.PlayerViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
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

    private fun snap(v: View, name: String, w: Int = 1080, h: Int = 2400) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(0x0B, 0x0F, 0x14))
        v.draw(c)
        val f = File(outDir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("RENDER ${f.absolutePath} ${f.length()}")
    }

    private fun scenario(block: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { sc ->
            sc.onActivity(block)
        }
    }

    private fun demoVm(act: ComponentActivity): PlayerViewModel {
        val vm = PlayerViewModel(act.application)
        vm.currentSong.value = Song("s1", "Neon Skyline", "Night Drive", 214)
        vm.isPlaying.value = true
        vm.positionMs.value = 62000
        vm.durationMs.value = 214000
        vm.sleepTimerEndsAtMs.value = null
        vm.recentPlays.value = (1..12).map { PlayerViewModel.RecentPlay("s$it", System.currentTimeMillis() - it * 3600_000L) }
        return vm
    }

    @Test
    fun render_lyricsSynced() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    LyricsPanel(
                        lines = listOf(
                            LrcLine(0, "City lights in the rain"),
                            LrcLine(30_000, "We were chasing neon skies"),
                            LrcLine(60_000, "And the night was ours to take"),
                            LrcLine(90_000, "Every heartbeat synchronized"),
                            LrcLine(120_000, "Till the morning wakes")
                        ),
                        activeIndex = 2,
                        loading = false,
                        onSeek = {}
                    )
                }
            }
            pump(act)
            snap(act.window.decorView, "lyrics-synced")
        }
    }

    @Test
    fun render_lyricsLoading() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme { LyricsPanel(lines = emptyList(), activeIndex = -1, loading = true, onSeek = {}) }
            }
            pump(act)
            snap(act.window.decorView, "lyrics-loading")
        }
    }

    @Test
    fun render_sleepTimerDialog() {
        scenario { act ->
            val vm = demoVm(act)
            var show by androidx.compose.runtime.mutableStateOf(true)
            act.setContent {
                VibeStreamTheme {
                    PlayerScreen(vm = vm, store = LibraryStore(act), onClose = {}, onOpenTimer = { show = true })
                    if (show) SleepTimerDialog(
                        onPick = { _, _ -> show = false },
                        onPickCustom = { _, _ -> show = false },
                        onCancelTimer = { show = false },
                        onDismiss = { show = false },
                        activeEndsAtMs = null
                    )
                }
            }
            pump(act)
            val dlg = ShadowDialog.getLatestDialog() ?: ShadowDialog.getShownDialogs().lastOrNull()
            snap((dlg?.window?.decorView ?: act.window.decorView), "sleep-timer-dialog")
        }
    }

    @Test
    fun render_equalizerDialog() {
        scenario { act ->
            val vm = demoVm(act)
            var show by androidx.compose.runtime.mutableStateOf(true)
            act.setContent {
                VibeStreamTheme {
                    PlayerScreen(vm = vm, store = LibraryStore(act), onClose = {}, onOpenTimer = { show = true })
                    if (show) EqualizerDialog(vm = vm, onDismiss = { show = false })
                }
            }
            pump(act)
            val dlg = ShadowDialog.getLatestDialog() ?: ShadowDialog.getShownDialogs().lastOrNull()
            snap((dlg?.window?.decorView ?: act.window.decorView), "equalizer-dialog")
        }
    }

    @Test
    fun render_fullPlayer_lyricsExpanded() {
        scenario { act ->
            val vm = demoVm(act)
            val lyrics = LyricsRepository(act)
            act.setContent {
                VibeStreamTheme {
                    PlayerScreen(vm = vm, store = LibraryStore(act), onClose = {}, onOpenTimer = {})
                    LaunchedEffect(Unit) {
                        lyrics.cached.value = LyricsRepository.Cached(
                            key = "x", synced = true,
                            plain = "City lights in the rain\nWe were chasing neon skies",
                            lines = listOf(
                                LrcLine(30_000, "City lights in the rain"),
                                LrcLine(62_000, "We were chasing neon skies"),
                                LrcLine(90_000, "And the night was ours to take")
                            ))
                        vm.lyricsExpanded.value = true
                    }
                }
            }
            pump(act)
            snap(act.window.decorView, "full-player-lyrics")
        }
    }
}
