package com.contentagent.vibestream

import com.contentagent.vibestream.data.local.DownloadTransferStore
import com.contentagent.vibestream.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferTest {
 private val song=Song("long", "60's Romantic Hits - Vol. 1", "Saregama Carvaan Tamil", durationSec=3465, kind="video")
 @Test fun queuedVisibleBeforeResolutionAndEtaUsesBytesNotFakeStages() {
  val store=DownloadTransferStore()
  store.queued(song)
  assertEquals(song,store.transfers.value["long"]!!.song)
  assertEquals("Queued",store.transfers.value["long"]!!.status)
  store.phase(song,"Resolving audio...")
  assertNull(store.transfers.value["long"]!!.percent)
  store.progress(song,25,100,10000)
  assertEquals(25,store.transfers.value["long"]!!.percent)
  assertEquals(30L,store.transfers.value["long"]!!.etaSeconds)
  assertEquals("25% · About 30s left",store.transfers.value["long"]!!.status)
 }
 @Test fun unknownSizeDoesNotInventPercentageOrEtaAndErrorCanRetry() {
  val store=DownloadTransferStore();store.queued(song)
  store.progress(song,1000,-1,10000)
  assertNull(store.transfers.value["long"]!!.percent)
  assertNull(store.transfers.value["long"]!!.etaSeconds)
  store.phase(song,"Download failed. Tap to retry.")
  assertTrue(store.transfers.value["long"]!!.status.contains("retry"))
  store.queued(song);assertEquals("Queued",store.transfers.value["long"]!!.phase)
  store.finished(song.id);assertTrue(store.transfers.value.isEmpty())
 }
 @Test fun pendingTracksKeepTheirOwnProgressAndDeduplicate() {
  val store=DownloadTransferStore();store.queued(song);store.queued(song)
  val second=song.copy(id="next",title="Next song");store.queued(second)
  store.progress(song,50,100,2000)
  assertEquals(2,store.transfers.value.size)
  assertEquals("Queued",store.transfers.value["next"]!!.status)
  store.phase(song,"Verifying");assertEquals("100% · Verifying audio...",store.transfers.value["long"]!!.status)
 }
}
