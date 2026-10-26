package com.contentagent.vibestream

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Bounded public metadata read only. No music downloaded and no account used. */
@RunWith(AndroidJUnit4::class)
class LiveChapterTest {
    @Test fun bhoomiPublisherTracklistResolves() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("CHECK_PUBLIC_CHAPTERS") == "1")
        val extractor = YouTubeExtractor()
        val points = extractor.resolveChapters("ges_8ILnAo4")
        assertNotNull("Public timestamp metadata must resolve: ${extractor.lastChapterError}", points)
        assertTrue("Expected publisher tracklist, not fabricated boundaries: $points", points!!.size >= 5)
        assertEquals(listOf(0L, 268000L, 543000L, 800000L, 1040000L), points.take(5).map { it.startMs })
        println("PUBLIC_CHAPTER_CHECK count=${points.size} starts=${points.map { it.startMs }}")
    }
}
