package com.contentagent.vibestream

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.local.TasteProfile
import com.contentagent.vibestream.data.local.TasteStore
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.ui.screens.HomeShelves
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoveryHomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun actualHomeExcludesKnownAndUpdatesQueueAfterPlay() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_shelves", 0).edit().clear().commit()
        TasteStore(ctx).save(TasteProfile(listOf("Tamil"), listOf("Chill")))
        val old = Song("old", "Already played", "Fixture artist")
        val liked = Song("liked", "Saved favourite", "Fixture artist")
        val fresh = (1..16).map { Song("new$it", "Discovery song $it", "Fixture artist $it") }
        val known = mutableStateOf(DiscoveryPolicy.keys(old))
        var queue = emptyList<Song>()
        val queries = mutableListOf<String>()
        compose.setContent {
            VibeStreamTheme {
                androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
                HomeShelves(YouTubeExtractor(), search = { q, _ -> queries.add(q); listOf(old, liked) + fresh },
                    currentSongId = null, onPlay = { _, q -> queue = q }, onSeeAll = {},
                    recent = listOf(old), liked = listOf(liked), playedKeys = known.value,
                    onPlayMood = { queue = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").assertExists().performClick()
        compose.runOnIdle {
            assertEquals(16, queue.size)
            assertFalse(queue.any { it.id == "old" || it.id == "liked" })
            assertTrue(queries.all { it.startsWith("Tamil ") })
            known.value = known.value + DiscoveryPolicy.keys(fresh.first())
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").performClick()
        compose.runOnIdle {
            assertEquals(15, queue.size)
            assertFalse(queue.any { it.id == "new1" })
        }
        compose.onNodeWithText("Refresh").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(queries.distinct().size >= 4) }
        val out = File(System.getProperty("user.home"), "renders/discovery-home.png")
        out.parentFile.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun exhaustedHomeShowsNoReplayMix() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_shelves", 0).edit().clear().commit()
        TasteStore(ctx).save(TasteProfile(listOf("Japanese")))
        val old = Song("known", "Known fixture", "Fixture artist")
        var queue = emptyList<Song>()
        compose.setContent {
            VibeStreamTheme {
                androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
                    HomeShelves(YouTubeExtractor(), search = { _, _ -> listOf(old) },
                        currentSongId = null, onPlay = { _, q -> queue = q }, onSeeAll = {},
                        recent = listOf(old), playedKeys = DiscoveryPolicy.keys(old), onPlayMood = { queue = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").assertDoesNotExist()
        compose.onNodeWithText("No new-to-you picks loaded. Retry or edit your taste.").assertExists()
        compose.runOnIdle { assertTrue(queue.isEmpty()) }
    }

}
