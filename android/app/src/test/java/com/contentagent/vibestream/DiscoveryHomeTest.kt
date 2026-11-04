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
        com.contentagent.vibestream.ui.screens.clearShelfMemory()
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
                HomeShelves(YouTubeExtractor(), related = { emptyList() }, search = { q, _ -> queries.add(q); listOf(old, liked) + fresh },
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
        compose.onNodeWithText("New picks").performClick()
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
        com.contentagent.vibestream.ui.screens.clearShelfMemory()
        TasteStore(ctx).save(TasteProfile(listOf("Japanese")))
        val old = Song("known", "Known fixture", "Fixture artist")
        var queue = emptyList<Song>()
        compose.setContent {
            VibeStreamTheme {
                androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
                    HomeShelves(YouTubeExtractor(), related = { emptyList() }, search = { _, _ -> listOf(old) },
                        currentSongId = null, onPlay = { _, q -> queue = q }, onSeeAll = {},
                        recent = listOf(old), playedKeys = DiscoveryPolicy.keys(old), onPlayMood = { queue = it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").assertDoesNotExist()
        compose.onNodeWithText("No new picks yet. Retry or edit your taste.").assertExists()
        compose.runOnIdle { assertTrue(queue.isEmpty()) }
    }

    @Test fun radioChangesHomeQueueAndKnownFilterStillWins() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_shelves", 0).edit().clear().commit()
        com.contentagent.vibestream.ui.screens.clearShelfMemory()
        ctx.getSharedPreferences("vibestream_radio_v1", 0).edit().clear().commit()
        TasteStore(ctx).save(TasteProfile(listOf("Tamil")))
        val seed = Song("abcdefghijk", "Liked seed", "Singer")
        val related = Song("lllllllllll", "English titled Tamil song", "Another singer")
        var queue = emptyList<Song>()
        compose.setContent {
            VibeStreamTheme {
                HomeShelves(YouTubeExtractor(), search = { _, _ -> listOf(Song("sssssssssss", "Search discovery", "Third singer")) },
                    related = { listOf(seed, related, Song("ppppppppppp", "Podcast", "Talk")) },
                    currentSongId = null, onPlay = { _, q -> queue = q }, onSeeAll = {},
                    liked = listOf(seed), onPlayMood = { queue = it })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").performClick()
        compose.runOnIdle {
            assertTrue(queue.any { it.id == related.id })
            assertTrue(queue.any { it.id == "sssssssssss" })
            assertFalse(queue.any { it.id == seed.id || it.id == "ppppppppppp" })
        }
    }

    @Test fun warmHomeUsesSavedPoolsAndClearPreservesLibrary() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_shelves", 0).edit().clear().commit()
        com.contentagent.vibestream.ui.screens.clearShelfMemory()
        ctx.getSharedPreferences("vibestream_radio_v1", 0).edit().clear().commit()
        TasteStore(ctx).save(TasteProfile(listOf("Tamil")))
        val liked = Song("abcdefghijk", "Saved song", "Artist")
        val library = com.contentagent.vibestream.data.local.LibraryStore(ctx)
        if (!library.isLiked(liked.id)) library.toggleLike(liked)
        library.recordPlayed(liked)
        val pool = (1..12).map { Song("cache$it", "Cached song $it", "Artist $it") }
        val key = "discovery-v4|M|Tamil new releases"
        ctx.getSharedPreferences("vibestream_shelves", 0).edit()
            .putString(key, com.google.gson.Gson().toJson(pool)).putLong("t:$key", System.currentTimeMillis()).commit()
        var cachedQueryFetched = false
        compose.setContent {
            VibeStreamTheme {
                HomeShelves(YouTubeExtractor(), search = { q, _ -> if (q == "Tamil new releases") cachedQueryFetched = true; pool },
                    related = { emptyList() }, currentSongId = null, onPlay = { _, _ -> }, onSeeAll = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your mood").assertExists()
        compose.runOnIdle { assertFalse(cachedQueryFetched) }
        compose.onNodeWithText("Loading cache").performClick()
        compose.onNodeWithText("Clear loading cache?").assertExists()
        compose.runOnIdle {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
            val view = (field.get(global) as List<android.view.View>).last()
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File(System.getProperty("user.home"), "renders/loading-cache.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Clear cache").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(com.contentagent.vibestream.data.local.LibraryStore(ctx).isLiked(liked.id))
            assertTrue(com.contentagent.vibestream.data.local.LibraryStore(ctx).playedKeys.value.contains("id:${liked.id}"))
        }
    }

}
