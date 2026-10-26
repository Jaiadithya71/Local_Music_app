package com.contentagent.vibestream
import com.contentagent.vibestream.data.analysis.CompilationIndexer
import com.contentagent.vibestream.data.analysis.RecognitionMatch
import org.junit.Assert.*
import org.junit.Test

class CompilationIndexerTest {
 @Test fun contentIdentityInvalidatesSamePathSizeAndTimestampReplacement() {
  val f=java.io.File.createTempFile("source-version",".pcm")
  try {
   f.writeBytes(byteArrayOf(1,2,3));val time=f.lastModified()
   val first=com.contentagent.vibestream.data.analysis.CompilationContentVersion.of(f.path)
   f.writeBytes(byteArrayOf(3,2,1));f.setLastModified(time)
   assertNotEquals(first,com.contentagent.vibestream.data.analysis.CompilationContentVersion.of(f.path))
   assertNull(com.contentagent.vibestream.data.analysis.CompilationContentVersion.of("https://example.invalid/audio"))
  } finally { f.delete() }
 }
 @Test fun automaticNewFileUsesObservedNamesAndRefinesUnequalBoundaries() {
  var position=0L;var calls=0
  val indexer=CompilationIndexer(decode={_,start,_->position=start;ShortArray(48000)},recognize={
   calls++
   val id=if(position<173000) "A" else if(position<391000) "B" else "A"
   RecognitionMatch.Match(id,"Title $id","Test",0)
  },throttle={})
  val result=indexer.index("fixture",600000)
  assertEquals(listOf("Title A (approx.)","Title B (approx.)","Title A (approx.)"),result.chapters.map{it.title})
  assertTrue(kotlin.math.abs(result.chapters[1].startMs-173000)<=4000)
  assertTrue(kotlin.math.abs(result.chapters[2].startMs-391000)<=4000)
  assertTrue(calls<40)
 }
 @Test fun missesAreUnknownWithoutInventedNames() {
  val result=CompilationIndexer(decode={_,_,_->ShortArray(48000)},recognize={null},throttle={}).index("fixture",600000)
  assertEquals("Unknown",result.chapters.single().title)
 }
 @Test fun cancellationDuringDecodeStopsBeforeDisclosure() {
  var cancelled=false;var called=false
  try {
   CompilationIndexer(decode={_,_,_->cancelled=true;ShortArray(48000)},recognize={called=true;null},throttle={}).index("fixture",600000,cancelled={cancelled})
   fail("Must cancel after decode")
  } catch (_:IllegalStateException) { assertFalse(called) }
 }
 @Test fun cancellationStopsBeforeDisclosure() {
  var called=false
  try {
   CompilationIndexer(decode={_,_,_->ShortArray(48000)},recognize={called=true;null},throttle={}).index("fixture",600000,cancelled={true})
   fail("Must cancel")
  } catch (_:IllegalStateException) { assertFalse(called) }
 }
}
