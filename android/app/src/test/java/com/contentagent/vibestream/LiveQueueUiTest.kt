package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.screens.QueueDialog
import com.contentagent.vibestream.ui.theme.VibeStreamTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w390dp-h844dp")
class LiveQueueUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun queueShowsLiveStatusWithoutSelectingAnotherSong() {
  var picks=0
  val queue=listOf(Song("previous","Previous discovery","Singer A"),Song("playing","Playing, unchanged","Singer B"),Song("related","Related discovery","Liked singer"),Song("new","Fresh discovery","Singer C"))
  compose.setContent { VibeStreamTheme {QueueDialog(queue,1,{picks++},{},"Upcoming discoveries updated")} }
  compose.onNodeWithText("Upcoming discoveries updated").assertExists()
  compose.onNodeWithText("Playing, unchanged").assertExists()
  compose.runOnIdle {
   assertEquals(0,picks)
   val global=Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
   val field=global.javaClass.getDeclaredField("mViews").apply{isAccessible=true}
   val view=(field.get(global) as List<android.view.View>).last()
   val bmp=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bmp))
   val file=File(System.getProperty("user.home"),"renders/live-discovery-queue.png");file.parentFile?.mkdirs()
   file.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
 }
}
