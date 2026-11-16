package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.screens.PlaylistDetail
import com.contentagent.vibestream.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistDownloadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val songs = listOf(
        Song("a", "Munbe Vaa", "A. R. Rahman"),
        Song("b", "Nenjukkul Peidhidum", "Harris Jayaraj"),
        Song("c", "Pachai Nirame", "A. R. Rahman"),
        Song("d", "New York Nagaram", "A. R. Rahman"),
        Song("e", "Vaseegara", "Harris Jayaraj")
    )
    @Test fun playlistAndSongButtonsDoNotPlayOrRemove() {
        var requested = emptyList<Song>()
        var played = false
        var removed = false
        compose.setContent {
            VibeStreamTheme { Surface(color = DarkBackground) {
                PlaylistDetail(Playlist("Good Songs", songs), null,
                    downloadedIds = setOf("b"), downloadProgress = mapOf("c" to .54f, "d" to 0f),
                    downloadErrors = mapOf("e" to "Download failed. Tap to retry."),
                    onDownload = { requested = it }, onPlay = { _, _ -> played = true },
                    onBack = {}, onDelete = {}, onRemove = { removed = true })
            } }
        }
        compose.onNodeWithText("Download 2 songs").performClick()
        compose.runOnIdle { assertEquals(listOf("a", "e"), requested.map { it.id }); assertFalse(played); assertFalse(removed) }
        compose.onNodeWithContentDescription("Download: Munbe Vaa").performClick()
        compose.runOnIdle { assertEquals(listOf(songs[0]), requested); assertFalse(played) }
        compose.onNodeWithContentDescription("Downloaded: Nenjukkul Peidhidum").assertIsNotEnabled()
        compose.onNodeWithText("Queued").assertExists()
        compose.onNodeWithText("Downloading 54%").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(4)
        compose.onNodeWithText("Download failed. Tap to retry.").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            val out = File(System.getProperty("user.home"), "renders/playlist-downloads.png")
            out.parentFile.mkdirs()
            out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun emptyPlaylistHasNoDownloadAction() {
        compose.setContent { VibeStreamTheme {
            PlaylistDetail(Playlist("Empty"), null, emptySet(), emptyMap(), emptyMap(), {}, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("No songs to download").assertIsNotEnabled()
    }
}
