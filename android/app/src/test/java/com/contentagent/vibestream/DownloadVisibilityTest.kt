package com.contentagent.vibestream
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.*
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadVisibilityTest {
 @Test fun directDownloadIsVisibleWhileResolverWaitsAndFailureRemainsRetryable() = runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val storage=OfflineStorage(context)
  val gate=CompletableDeferred<String?>();val started=CompletableDeferred<Unit>()
  val manager=DownloadManager(context,YouTubeExtractor(),storage) {started.complete(Unit);gate.await()}
  val song=Song("progress-test","60's Romantic Hits - Vol. 1","Saregama Carvaan Tamil",durationSec=3465,kind="video")
  val job=launch {manager.downloadTrack(song)}
  withTimeout(3000) {started.await()}
  assertTrue(manager.isDownloading(song.id))
  assertEquals(song,manager.downloadTransfers.value[song.id]!!.song)
  assertEquals("Resolving audio...",manager.downloadTransfers.value[song.id]!!.status)
  assertFalse(storage.isDownloaded(song.id))
  gate.complete(null);job.join()
  assertFalse(manager.isDownloading(song.id))
  assertTrue(manager.downloadTransfers.value[song.id]!!.status.contains("retry"))
 }
}
