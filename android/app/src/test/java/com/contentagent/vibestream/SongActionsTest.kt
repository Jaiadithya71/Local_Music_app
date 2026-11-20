package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.*
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongActionsTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun listOptionsLikeAndSaveDoNotPlayTheSong() {
  val song=Song("fixture-song","Unplayed discovery song","Fixture singer")
  var plays=0;var likes=0;var saves=0;var downloads=0
  compose.setContent {
   var request by remember {mutableStateOf<SongActionRequest?>(null)}
   VibeStreamTheme {
    CompositionLocalProvider(LocalSongActions provides {request=it}) {
     SongCard(song,false,false,false,0f,false,onPlayClick={plays++},onDownloadClick={downloads++})
     request?.let {
      SongActionsSheet(song,false,false,false,onLike={likes++;request=null},onSave={saves++;request=null},onDownload={downloads++;request=null},onDismiss={request=null})
     }
    }
   }
  }
  compose.onNodeWithContentDescription("Song actions: ${song.title}").performClick()
  compose.onNodeWithText("Like song").performClick()
  compose.runOnIdle {assertEquals(1,likes);assertEquals(0,plays)}
  compose.onNodeWithContentDescription("Song actions: ${song.title}").performClick()
  compose.onNodeWithText("Add to playlist").performClick()
  compose.runOnIdle {assertEquals(1,saves);assertEquals(0,plays);assertEquals(0,downloads)}
  compose.onNodeWithContentDescription("Song actions: ${song.title}").performClick()
  compose.waitForIdle()
  compose.runOnIdle {
   val global=Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
   val field=global.javaClass.getDeclaredField("mViews").apply {isAccessible=true}
   val view=(field.get(global) as List<android.view.View>).last()
   val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bitmap))
   val file=File(System.getProperty("user.home"),"renders/song-list-actions.png");file.parentFile?.mkdirs()
   file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.onNodeWithText("Download song").performClick()
  compose.runOnIdle {assertEquals(1,downloads);assertEquals(0,plays)}
 }
}
