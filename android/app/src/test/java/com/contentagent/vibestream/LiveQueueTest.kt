package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.*
import com.contentagent.vibestream.player.LiveQueueSession
import org.junit.Assert.*
import org.junit.Test

class LiveQueueTest {
 private val past=Song("past","Past track","Old singer")
 private val current=Song("current","Playing track","Current singer",streamUrl="https://fixture.invalid/audio")
 private val unrelated=Song("unrelated","Other discovery","Other singer")
 private val similar=Song("similar","Tamil melody","Favourite singer")
 private val feedback=Song("liked","Liked song","Favourite singer")
 private fun revise(q:List<Song>,c:List<Song>,f:LiveQueuePolicy.Feedback,known:Set<String> = emptySet()) =
  LiveQueuePolicy.update(q,1,c,f,known,emptySet(),listOf("Tamil"),listOf("melody"))
 @Test fun likeAddsRelatedAndPromotesLikedArtistWithoutTouchingCurrentOrHistory() {
  val q=listOf(past,current,unrelated)
  val out=revise(q,listOf(similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE))
  assertEquals(listOf(past,current),out.take(2));assertSame(current,out[1])
  assertEquals("https://fixture.invalid/audio",current.streamUrl)
  assertEquals(similar,out[2]);assertEquals(unrelated,out[3])
 }
 @Test fun repeatedLikesDoNotDuplicateSongIdsOrRecordings() {
  val f=LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE)
  val first=revise(listOf(past,current,unrelated),listOf(similar,similar),f)
  val second=revise(first,listOf(similar,similar.copy(id="alt",title="Tamil melody (Official Audio)")),f)
  assertEquals(1,second.count{it.title.startsWith("Tamil melody")})
  assertEquals(second.map{it.id}.distinct().size,second.size)
 }
 @Test fun knownPlayedAndSavedRecordingsNeverEnterUpcoming() {
  val old=Song("old","Already played song","Singer")
  val saved=Song("saved","Saved favourite","Singer")
  val out=revise(listOf(past,current,old,saved),listOf(old.copy(id="alt"),saved,similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE),
   (DiscoveryPolicy.keys(old)+DiscoveryPolicy.keys(saved)))
  assertEquals(listOf(past,current,similar),out)
 }
 @Test fun skipDropsMatchingArtistFromUpcomingNotCurrentAndDemotesPriorSkips() {
  val skipped=current.copy(artist="Skipped singer")
  val tail=listOf(Song("same","Same artist discovery","Skipped singer"),unrelated)
  val out=LiveQueuePolicy.update(listOf(past,skipped)+tail,1,listOf(similar),LiveQueuePolicy.Feedback(skipped,LiveQueuePolicy.Signal.SKIP),emptySet(),setOf("Favourite singer"),emptyList(),emptyList())
  assertSame(skipped,out[1]);assertFalse(out.drop(2).any{it.artist==skipped.artist});assertEquals(unrelated,out[2])
 }
 @Test fun unknownArtistSkipDoesNotDeleteAllUnknownMetadata() {
  val unknown=current.copy(artist="Unknown Artist")
  val another=unrelated.copy(artist="Unknown Artist")
  val out=revise(listOf(past,unknown,another),emptyList(),LiveQueuePolicy.Feedback(unknown,LiveQueuePolicy.Signal.SKIP))
  assertTrue(another in out)
 }
 @Test fun networkFailureKeepsEligibleUpcomingAndNeverPadsWithHistory() {
  assertEquals(listOf(past,current,unrelated),revise(listOf(past,current,unrelated),emptyList(),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.PLAY)))
 }
 @Test fun delayedReplyCannotModifyAnExplicitPlaylistOrNewDiscoverySession() {
  val session=LiveQueueSession();session.begin(true);val request=session.epoch
  val q=listOf(past,current,unrelated)
  session.begin(false)
  assertSame(q,session.revise(request,q,1,listOf(similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE),emptySet(),emptyList(),emptyList()))
  session.begin(true)
  assertSame(q,session.revise(request,q,1,listOf(similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE),emptySet(),emptyList(),emptyList()))
 }
 @Test fun skipFeedbackDedupesInAppAndHeadsetAndOnlyCountsEarlyNext() {
  val session=LiveQueueSession();session.begin(true)
  assertFalse(session.acceptEarlySkip(current,0));assertFalse(session.acceptEarlySkip(current,45001))
  assertTrue(session.acceptEarlySkip(current,10000));assertFalse(session.acceptEarlySkip(current,10001))
  session.notePlaying(unrelated);assertTrue(session.acceptEarlySkip(unrelated,1000))
 }
 @Test fun playedSessionLedgerFiltersCandidatesEvenBeforeLibraryPersists() {
  val session=LiveQueueSession();session.begin(true);session.notePlaying(similar)
  val q=listOf(past,current,unrelated)
  val out=session.revise(session.epoch,q,1,listOf(similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE),emptySet(),emptyList(),emptyList())
  assertFalse(out.any{it.id==similar.id})
 }
 @Test fun invalidIndexAndEmptyQueueAreUnchangedAndUpcomingIsBounded() {
  assertTrue(LiveQueuePolicy.update(emptyList(),-1,listOf(similar),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.LIKE),emptySet(),emptySet(),emptyList(),emptyList()).isEmpty())
  val many=(1..100).map{Song("x$it","Discovery $it","Singer $it")}
  assertEquals(42,revise(listOf(past,current)+many,emptyList(),LiveQueuePolicy.Feedback(feedback,LiveQueuePolicy.Signal.PLAY)).size)
 }
}
