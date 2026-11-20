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
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.ui.screens.HomeShelves
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(qualifiers = "w390dp-h844dp")
class ShelfRefreshTest {
 @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
 @Test fun depletedCategoriesRefillIndividuallyAndRefreshClearsCatalog() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  ctx.getSharedPreferences("vibestream_shelves",0).edit().clear().commit()
  com.contentagent.vibestream.ui.screens.clearShelfMemory()
  TasteStore(ctx).save(TasteProfile(listOf("Tamil","Hindi")))
  val old=Song("known","Already saved","Old artist")
  val queries=mutableListOf<String>();var clears=0
  compose.setContent {
   VibeStreamTheme {
    androidx.compose.material3.Surface(color = com.contentagent.vibestream.ui.theme.DarkBackground) {
    androidx.compose.runtime.CompositionLocalProvider(com.contentagent.vibestream.ui.components.LocalSongActions provides {}) {
    HomeShelves(YouTubeExtractor(),search={q,_->
     queries.add(q)
     if(q=="Tamil hit songs" || q=="Hindi hit songs") listOf(old)
     else (1..8).map { Song("$q-$it", "${if (q.startsWith("Hindi")) "Hindi" else "Tamil"} discovery $it", "Artist $it") }
    }, related={emptyList()},refreshCatalog={clears++},currentSongId=null,onPlay={_,_->},onSeeAll={},playedKeys=DiscoveryPolicy.keys(old))
    }
    }
   }
  }
  compose.waitForIdle()
  compose.runOnIdle {
   assertTrue(queries.any {it.startsWith("Tamil hit songs ")})
   assertTrue(queries.any {it.startsWith("Hindi hit songs ")})
  }
  compose.onNodeWithTag("home-shelves").performScrollToNode(hasText("Hindi hits"))
  compose.waitForIdle()
  compose.runOnIdle {
   val view=compose.activity.window.decorView
   val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bitmap))
   val file=java.io.File(System.getProperty("user.home"),"renders/refilled-home-shelves.png");file.parentFile?.mkdirs()
   file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.onNodeWithTag("home-shelves").performScrollToNode(hasText("New picks"))
  compose.onNodeWithText("New picks").performClick();compose.waitForIdle()
  compose.runOnIdle {assertEquals(1,clears)}
 }
 @Test fun catalogClearDoesNotTouchLikesOrHistory() {
  val ctx=ApplicationProvider.getApplicationContext<Context>()
  val song=Song("cache-fixture","Saved cache fixture","Singer")
  val library=LibraryStore(ctx)
  if(!library.isLiked(song.id))library.toggleLike(song)
  library.recordPlayed(song)
  val cache=SearchCacheStore(ctx);cache.put("fixture",true,listOf(song))
  assertNotNull(cache.get("fixture",true))
  YouTubeExtractor().apply {searchCache=cache}.clearSearchCaches()
  assertNull(cache.get("fixture",true))
  assertTrue(LibraryStore(ctx).isLiked(song.id))
  assertTrue(LibraryStore(ctx).playedKeys.value.contains("id:${song.id}"))
 }
}
