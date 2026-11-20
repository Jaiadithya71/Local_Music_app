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
import com.contentagent.vibestream.data.model.ArtistPage
import com.contentagent.vibestream.data.model.SearchEntry
import com.contentagent.vibestream.data.model.SearchSections
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.screens.AlbumPageView
import com.contentagent.vibestream.ui.screens.ArtistPageView
import com.contentagent.vibestream.ui.screens.SectionedSearchResults
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the YouTube Music-style sectioned search screens to PNGs (real Compose layouts, no network). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SectionedRenderTest {
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

    private fun scenario(block: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { sc ->
            sc.onActivity(block)
        }
    }

    private fun song(id: String, title: String, artist: String, dur: String, kind: String = "song") =
        Song(id = id, title = title, artist = artist, durationText = dur, kind = kind)

    private fun demoSections() = SearchSections(
        topResult = song("t1", "Srivalli", "Javed Ali", "3:45"),
        songs = listOf(
            song("s1", "Srivalli", "Javed Ali", "3:45"),
            song("s2", "Srivalli (From \"Pushpa\")", "Sid Sriram", "4:02"),
            song("s3", "Srivalli - Lyrical", "Devi Sri Prasad", "3:58"),
            song("s4", "Srivalli (Karaoke)", "Various", "3:44")),
        videos = listOf(
            song("v1", "Srivalli Video Song | Pushpa", "T-Series", "4:10", kind = "video"),
            song("v2", "Srivalli Dance Cover", "Team Naach", "3:20", kind = "video")),
        albums = listOf(
            SearchEntry("a1", "Pushpa - The Rise", "Album • Devi Sri Prasad", "", "album"),
            SearchEntry("a2", "Srivalli - Single", "Single • Javed Ali", "", "album")),
        artists = listOf(
            SearchEntry("ar1", "Sid Sriram", "Artist • 8.2M subscribers", "", "artist"),
            SearchEntry("ar2", "Javed Ali", "Artist • 3.1M subscribers", "", "artist")),
        playlists = listOf(
            SearchEntry("p1", "Srivalli Vibes", "Playlist • 42 songs", "", "playlist")))

    @Test(timeout = 120000)
    fun render_sectionedSearch() {
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    SectionedSearchResults(
                        sections = demoSections(),
                        activeChip = "all",
                        onChip = {},
                        currentSongId = null,
                        isPlaying = false,
                        isDownloaded = { false },
                        isDownloading = { false },
                        downloadProgress = { 0f },
                        onPlay = { _, _ -> },
                        onDownload = {},
                        onOpenEntry = {})
                }
            }
            pump(act)
            snap(act.window.decorView, "sectioned-search", 1080, 2400)
        }
    }

    @Test(timeout = 120000)
    fun render_albumPage() {
        val entry = SearchEntry("a1", "Pushpa - The Rise", "Album • Devi Sri Prasad • 2021", "", "album")
        val tracks = listOf(
            song("s1", "Srivalli", "Javed Ali", "3:45"),
            song("s2", "Oo Antava Oo Oo Antava", "Indravathi Chauhan", "3:42"),
            song("s3", "Saami Saami", "Sunidhi Chauhan", "3:55"),
            song("s4", "Daakko Daakko Meka", "Shivam", "4:15"),
            song("s5", "Eyy Bidda Idhi Naa Adda", "Nakash Aziz", "4:02"))
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    AlbumPageView(
                        entry = entry,
                        tracks = tracks,
                        loading = false,
                        currentSongId = "s1",
                        isPlaying = true,
                        isDownloaded = { it == "s3" },
                        isDownloading = { it == "s4" },
                        downloadProgress = { 0.45f },
                        onPlay = { _, _ -> },
                        onPlayAll = {},
                        onShuffle = {},
                        onDownload = {},
                        onBack = {})
                }
            }
            pump(act)
            snap(act.window.decorView, "sectioned-album-page", 1080, 2400)
        }
    }

    @Test(timeout = 120000)
    fun render_artistPage() {
        val page = ArtistPage(
            name = "Sid Sriram",
            thumbnailUrl = "",
            topSongs = listOf(
                song("s1", "Srivalli (From \"Pushpa\")", "Sid Sriram", "4:02"),
                song("s2", "Inkem Inkem Inkem Kaavaale", "Sid Sriram", "4:26"),
                song("s3", "Samajavaragamana", "Sid Sriram", "3:42"),
                song("s4", "Adiga Adiga", "Sid Sriram", "4:10"),
                song("s5", "Undiporaadhey", "Sid Sriram", "3:58")),
            albums = listOf(
                SearchEntry("a1", "Geetha Govindam", "Album • 2018", "", "album"),
                SearchEntry("a2", "Ala Vaikunthapurramuloo", "Album • 2019", "", "album"),
                SearchEntry("a3", "Pushpa - The Rise", "Album • 2021", "", "album")))
        scenario { act ->
            act.setContent {
                VibeStreamTheme {
                    ArtistPageView(
                        page = page,
                        loading = false,
                        currentSongId = null,
                        isPlaying = false,
                        isDownloaded = { false },
                        isDownloading = { false },
                        downloadProgress = { 0f },
                        onPlay = { _, _ -> },
                        onPlayAll = {},
                        onDownload = {},
                        onOpenEntry = {},
                        onBack = {})
                }
            }
            pump(act)
            snap(act.window.decorView, "sectioned-artist-page", 1080, 2400)
        }
    }
}
