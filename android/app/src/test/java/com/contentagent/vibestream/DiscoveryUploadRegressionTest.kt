package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy as D
import org.junit.Assert.*
import org.junit.Test

class DiscoveryUploadRegressionTest {
 @Test fun singleTrackUploadsSurviveDiscoveryWithLargeHistory() {
  val history = (1..5000).map { Song("old$it", "Old recording $it", "Singer") }
  val uploads = (1..12).map { Song("new$it", "Fresh track $it", "Singer", durationSec = 240, kind = "video") }
  val known = history.flatMap { D.keys(it) }.toSet()
  assertEquals(12, D.fresh(uploads, known).size)
 }
 @Test fun unsafeUploadsAndKnownRecordingsRemainExcluded() {
  val saved = Song("saved", "Saved recording", "Singer", durationSec = 240, kind = "video")
  val pool = listOf(saved, saved.copy(id="alternate"),
   Song("talk", "An interview", "Speaker", durationSec=240, kind="video"),
   Song("mix", "Non stop jukebox", "DJ", durationSec=300, kind="video"),
   Song("long", "Concert", "Singer", durationSec=3600, kind="video"),
   Song("short", "Clip", "Singer", durationSec=15, kind="video"))
  assertTrue(D.fresh(pool,D.keys(saved)).isEmpty())
 }
}
