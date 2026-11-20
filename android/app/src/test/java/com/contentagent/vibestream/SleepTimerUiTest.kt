package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.ui.components.SleepTimerDialog
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
class SleepTimerUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun durationsEndOfSongAndCancelAreAccessible() {
  var minutes=0;var ended=false;var cancelled=0
  compose.setContent {
   var end by remember {mutableStateOf<Long?>(null)}
   var endSong by remember {mutableStateOf(false)}
   VibeStreamTheme {SleepTimerDialog(end,endSong,{minutes=it;end=System.currentTimeMillis()+it*60000L;endSong=false},{ended=true;endSong=true;end=null},{cancelled++;end=null;endSong=false},{})}
  }
  for(m in listOf(15,30,45,60)) {
   compose.onNodeWithText("$m minutes").performScrollTo().performClick()
   compose.runOnIdle {assertEquals(m,minutes)}
  }
  compose.onNodeWithText("End of current song").performScrollTo().performClick()
  compose.runOnIdle {assertTrue(ended)}
  compose.onNodeWithText("Will stop at the end of this song").performScrollTo()
  compose.waitForIdle()
  compose.runOnIdle {
   val global=Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
   val field=global.javaClass.getDeclaredField("mViews").apply{isAccessible=true}
   val view=(field.get(global) as List<android.view.View>).last()
   val bmp=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(bmp))
   val file=File(System.getProperty("user.home"),"renders/sleep-timer-new.png");file.parentFile?.mkdirs()
   file.outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.onNodeWithText("Turn off").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(1,cancelled)}
 }
}
