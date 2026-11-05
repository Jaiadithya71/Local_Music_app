package com.contentagent.vibestream

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.local.TasteProfile
import com.contentagent.vibestream.data.local.TasteStore
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.screens.HomeShelves
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Reproduction: on a cold start (no cached shelves, e.g. first launch after an
 * update) with liked songs present, a stalled song-radio request must not stop
 * Made for You from rendering keyword-seeded picks. The radio path has its own
 * 8s network ceiling in the extractor, but a stalled socket must never gate
 * the whole section.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlowRadioHomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 120000)
    fun stalledRadioDoesNotBlankMadeForYou() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (p in listOf("vibestream_shelves", "vibestream_radio_v1"))
            ctx.getSharedPreferences(p, 0).edit().clear().commit()
        com.contentagent.vibestream.ui.screens.clearShelfMemory()
        TasteStore(ctx).save(TasteProfile(listOf("Tamil"), emptyList(), emptyList()))
        val liked = Song("abcdefghijk", "Saved favourite", "Singer")
        val fresh = (1..12).map { Song("new$it", "Fresh song $it", "Artist $it") }
        var moodQueue = emptyList<Song>()
        val never = CompletableDeferred<List<Song>>()
        compose.setContent {
            VibeStreamTheme {
                HomeShelves(
                    YouTubeExtractor(),
                    search = { _, _ -> fresh },
                    related = { never.await() }, // stalled radio
                    currentSongId = null, onPlay = { _, _ -> }, onSeeAll = {},
                    liked = listOf(liked),
                    onPlayMood = { moodQueue = it })
            }
        }
        compose.waitUntil(45_000) {
            runCatching { compose.onNodeWithText("Your mood").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Your mood").performClick()
        compose.runOnIdle {
            assertTrue(moodQueue.isNotEmpty())
            assertTrue(moodQueue.none { it.id == liked.id })
        }
        // The section must settle: "Finding..." is a loading state, not a final
        // state. While radio is stalled the refresh button must come back.
        compose.waitUntil(45_000) {
            runCatching { compose.onNodeWithText("New picks").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Discovery excludes your known songs").assertExists()
        never.cancel()
    }
}
