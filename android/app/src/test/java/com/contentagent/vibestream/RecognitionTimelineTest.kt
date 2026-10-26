package com.contentagent.vibestream

import com.contentagent.vibestream.data.analysis.RecognitionTimeline as Timeline
import org.junit.Assert.*
import org.junit.Test

class RecognitionTimelineTest {
    private fun at(ms: Long, id: String?) = Timeline.Observation(ms, id, id?.let { "Title $it" })
    @Test fun windowsCoverStartAndEndAndStayBounded() {
        val points = Timeline.windowStarts(2700000)
        assertEquals(0L, points.first()); assertEquals(2688000L, points.last())
        assertTrue(points.zipWithNext().all { (a,b) -> b - a in 1..30000 })
        assertEquals(listOf(0L), Timeline.windowStarts(5000))
    }
    @Test fun repeatedSongsAreNotGloballyDeduped() {
        val segments = Timeline.assemble(listOf(at(0,"A"),at(30000,"A"),at(60000,"B"),at(90000,"B"),at(120000,"A"),at(150000,"A")),180000)
        assertEquals(listOf("A","B","A"),segments.map { it.recordingId })
        assertEquals(0L,segments.first().startMs);assertEquals(180000L,segments.last().endMs)
        assertTrue(segments.zipWithNext().all { (a,b)->a.endMs==b.startMs })
    }
    @Test fun isolatedMatchIsUnknownAndCannotInventSongName() {
        val result = Timeline.assemble(listOf(at(0,null),at(30000,"A"),at(60000,null)),90000)
        assertEquals(1,result.size);assertNull(result.single().recordingId);assertEquals("Unknown",result.single().title)
    }
    @Test fun unmatchedGapRemainsUnknown() {
        val result = Timeline.assemble(listOf(at(0,"A"),at(30000,"A"),at(60000,null),at(90000,"B"),at(120000,"B")),150000)
        assertEquals(listOf("A",null,"B"),result.map { it.recordingId })
        assertTrue(result.all { it.boundaryUncertaintyMs >= 30000 })
    }
    @Test fun emptyResultsAreExplicitlyUnknown() {
        assertEquals("Unknown",Timeline.assemble(emptyList(),2700000).single().title)
    }
}
