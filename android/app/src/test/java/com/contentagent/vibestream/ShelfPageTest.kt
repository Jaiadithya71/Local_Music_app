package com.contentagent.vibestream

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.local.*
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.screens.HomeShelves
import com.contentagent.vibestream.ui.screens.ShelfScreen
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(qualifiers = "w390dp-h844dp")
class ShelfPageTest {
 @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

 @Test fun shelfPageLoadsSearchesInCategoryAndShowMoreAppends() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  ctx.getSharedPreferences("vibestream_shelves",0).edit().clear().commit()
  com.contentagent.vibestream.ui.screens.clearShelfMemory()
  val queries=mutableListOf<String>()
  compose.setContent {
   VibeStreamTheme {
    androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
    ShelfScreen(title="Workout",query="workout songs",extractor=YouTubeExtractor(),search={ q, _ ->
     queries.add(q)
     (1..8).map { Song("$q-$it", "$q track $it", "Artist $it") }
    },musicOnly=true,currentSongId=null,known=emptySet(),onPlay={_,_->},onBack={})
    }
   }
  }
  compose.waitForIdle()
  compose.runOnIdle { assertTrue(queries.any { it=="workout songs" }) }
  compose.onNodeWithText("Workout").assertExists()
  compose.onNodeWithText("workout songs track 1").assertExists()
  // In-category search: "tamil" on the Workout page searches "tamil workout songs".
  compose.onNodeWithTag("shelf-search").performTextInput("tamil")
  compose.waitForIdle()
  // Recomposition started the debounced reload; now run the paused main
  // looper past the 350ms debounce so the search actually fires.
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(600, java.util.concurrent.TimeUnit.MILLISECONDS)
  compose.waitForIdle()
  compose.runOnIdle { assertTrue(queries.any { it=="tamil workout songs" }) }
  compose.onNodeWithText("tamil workout songs track 1").assertExists()
  // Show more fetches a drift query and appends new songs instead of no-op.
  compose.onNodeWithTag("shelf-show-more").performClick()
  compose.waitForIdle()
  compose.runOnIdle { assertTrue(queries.any { it=="tamil workout songs mix" }) }
  compose.onNodeWithTag("shelf-list").performScrollToNode(hasText("tamil workout songs mix track 1"))
  compose.onNodeWithText("tamil workout songs mix track 1").assertExists()
 }

 @Test fun yourMoodLeadsWithLikedAndPlaylistArtists() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  ctx.getSharedPreferences("vibestream_shelves",0).edit().clear().commit()
  com.contentagent.vibestream.ui.screens.clearShelfMemory()
  TasteStore(ctx).save(TasteProfile())
  val liked=listOf(Song("l1","A loved song","Loved Artist"))
  val playlists=listOf(com.contentagent.vibestream.data.local.Playlist("mine",listOf(Song("p1","A playlist song","Playlist Artist"))))
  var queue: List<Song> = emptyList()
  compose.setContent {
   VibeStreamTheme {
    androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
    androidx.compose.runtime.CompositionLocalProvider(com.contentagent.vibestream.ui.components.LocalSongActions provides {}) {
    HomeShelves(YouTubeExtractor(),search={ q, _ ->
     (1..6).map { Song("$q-r$it", "$q random $it", "Random $it") } +
       Song("$q-l", "$q loved-flavoured", "Loved Artist") +
       Song("$q-p", "$q playlist-flavoured", "Playlist Artist")
    },related={emptyList()},currentSongId=null,onPlay={_,_->},onSeeAll={_,_->},
     liked=liked,playlists=playlists,onPlayMood={ queue=it })
    }
    }
   }
  }
  compose.waitForIdle()
  compose.onNodeWithText("Your mood").performClick()
  compose.waitForIdle()
  compose.runOnIdle {
   assertTrue(queue.isNotEmpty())
   assertTrue("first pick is " + queue.first().artist, queue.first().artist=="Loved Artist" || queue.first().artist=="Playlist Artist")
  }
 }

 @Test fun homeShelfTitleOpensCategoryPage() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  ctx.getSharedPreferences("vibestream_shelves",0).edit().clear().commit()
  com.contentagent.vibestream.ui.screens.clearShelfMemory()
  TasteStore(ctx).save(TasteProfile())
  var opened: Pair<String,String>? = null
  compose.setContent {
   VibeStreamTheme {
    androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
    androidx.compose.runtime.CompositionLocalProvider(com.contentagent.vibestream.ui.components.LocalSongActions provides {}) {
    HomeShelves(YouTubeExtractor(),search={ q, _ ->
     (1..8).map { Song("$q-$it", "$q track $it", "Artist $it") }
    },related={emptyList()},currentSongId=null,onPlay={_,_->},onSeeAll={ t, q -> opened = t to q })
    }
    }
   }
  }
  compose.waitForIdle()
  compose.onNodeWithTag("home-shelves").performScrollToNode(hasTestTag("shelf-title-Workout"))
  compose.onNodeWithTag("shelf-title-Workout").performClick()
  compose.runOnIdle { assertEquals("Workout" to "workout songs", opened) }
 }
}
