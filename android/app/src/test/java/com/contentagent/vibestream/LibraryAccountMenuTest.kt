package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.*
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
class LibraryAccountMenuTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun compactAccountMenuAndPlaylistActionsDoNotPlay() {
  var signsOut=0;var plays=0;var requests=0
  val song=Song("fixture","Unplayed Tamil discovery","Fixture singer")
  compose.setContent {
   VibeStreamTheme {
    CompositionLocalProvider(LocalSongActions provides {requests++}) {
     Surface(color=DarkBackground) {
      Column {
       Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("VibeStream",color=TextPrimary,modifier=Modifier.weight(1f))
        YouTubeAccountMenu {signsOut++}
       }
       PlaylistDetail(Playlist("Saved songs",listOf(song)),null,emptySet(),emptyMap(),emptyMap(),{}, {_,_->plays++},{},{},{})
      }
     }
    }
   }
  }
  compose.onNodeWithText("YouTube Music signed in").assertDoesNotExist()
  compose.onNodeWithContentDescription("Song actions: ${song.title}").performClick()
  compose.runOnIdle {assertEquals(1,requests);assertEquals(0,plays)}
  compose.runOnIdle {
   val view=compose.activity.window.decorView
   val bmp=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bmp))
   val f=File(System.getProperty("user.home"),"renders/library-compact-account.png");f.parentFile?.mkdirs()
   f.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.onNodeWithContentDescription("YouTube Music account").performClick()
  compose.onNodeWithText("Sign out of YouTube Music").performClick()
  compose.runOnIdle {assertEquals(1,signsOut);assertEquals(0,plays)}
 }
}
