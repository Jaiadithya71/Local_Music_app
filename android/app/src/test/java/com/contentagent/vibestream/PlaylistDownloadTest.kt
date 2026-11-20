package com.contentagent.vibestream

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.*
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.*
import com.contentagent.vibestream.ui.screens.*
import com.contentagent.vibestream.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp")
class PlaylistDownloadTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val songs=listOf(
  Song("a","Bhoomiyil Maanida","M.K. Thyagaraja Bhagavathar"),
  Song("b","Ullathil Nalla Ullam","Dr. Seerkazhi S. Govindarajan"),
  Song("c","Paattum Naane","T.M. Soundararajan"),
  Song("d","Isai Thamizh Nee Seida","T.R. Mahalingam"),
  Song("e","Vaseegara","Harris Jayaraj"))
 private fun capture(name:String) { compose.runOnIdle {
  val v=compose.activity.window.decorView
  val bmp=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
  v.draw(android.graphics.Canvas(bmp));val out=File(System.getProperty("user.home"),"renders/$name.png");out.parentFile.mkdirs()
  out.outputStream().use {bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 } }
 @Test fun musicFirstPlaylistHasSecondaryOfflineAndPerSongProgress() {
  var requested=emptyList<Song>();var played=false;var action:SongActionRequest?=null
  compose.setContent { VibeStreamTheme { Surface(color=DarkBackground) {
   CompositionLocalProvider(LocalSongActions provides {action=it}) {
    PlaylistDetail(Playlist("Good Songs",songs),null,setOf("b"),mapOf("c" to .54f,"d" to 0f),emptyMap(),
     {requested=it},{_,_->played=true},{},{},{},
     mapOf("c" to DownloadTransfer(songs[2],"Downloading",54,100,42),"d" to DownloadTransfer(songs[3])))
   }
  } } }
  compose.onNodeWithText("Save offline").performClick()
  compose.runOnIdle {assertEquals(listOf("a","e"),requested.map{it.id});assertFalse(played)}
  compose.onNodeWithContentDescription("Song actions: Bhoomiyil Maanida").performClick()
  compose.runOnIdle {assertEquals("a",action?.song?.id)}
  compose.onNodeWithContentDescription("Download: Bhoomiyil Maanida").assertDoesNotExist()
  compose.onNodeWithText("54% · About 42s left").assertExists()
  compose.onNodeWithText("Queued").assertExists()
  capture("playlist-music-first")
  compose.onNodeWithText("Play",substring=false).performClick();compose.runOnIdle{assertTrue(played)}
 }
 @Test fun emptyPlaylistDoesNotPlayOrDownload() {
  compose.setContent {VibeStreamTheme {PlaylistDetail(Playlist("Empty"),null,emptySet(),emptyMap(),emptyMap(),{},{_,_->},{},{},{})}}
  compose.onNodeWithText("Play").assertIsNotEnabled();compose.onNodeWithText("Save offline").assertIsNotEnabled()
 }
 @Test fun downloadsShowPendingWithNoSavedFilesAndTransitionWithoutDuplicates() {
  val song=Song("long","60's Romantic Hits - Vol. 1","Saregama Carvaan Tamil",durationText="57:45")
  var transfers by mutableStateOf(listOf(DownloadTransfer(song)))
  var saved by mutableStateOf(emptyList<Song>())
  var retry=false
  compose.setContent {VibeStreamTheme {Surface(color=DarkBackground) {
   DownloadsPane(saved,transfers,null,false,{},{retry=true},{})
  }}}
  compose.onNodeWithText(song.title).assertIsDisplayed();compose.onNodeWithText("Queued").assertIsDisplayed()
  compose.runOnIdle {transfers=listOf(DownloadTransfer(song,"Downloading",64,100,82))}
  compose.onNodeWithText("64% · About 1m 22s left").assertIsDisplayed()
  capture("downloads-progress-eta")
  compose.runOnIdle {transfers=listOf(DownloadTransfer(song,"Download failed. Tap to retry."))}
  compose.onNodeWithText("Retry").performClick();compose.runOnIdle{assertTrue(retry)}
  compose.runOnIdle {saved=listOf(song.copy(isDownloaded=true))}
  compose.onAllNodesWithText(song.title).assertCountEquals(1)
  compose.onNodeWithText("Downloads in progress (1)").assertDoesNotExist()
 }
}
