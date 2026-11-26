package com.contentagent.vibestream

import com.contentagent.vibestream.data.recommendation.Chapter
import com.contentagent.vibestream.data.recommendation.ChapterPolicy
import org.junit.Assert.*
import org.junit.Test

class ChapterPolicyTest {
    private val desc = """
        Best of 90s Tamil melodies
        00:00 Kadhal Rojave
        4:35 - Chinna Chinna Aasai
        1. 09:10 Vennilave
        1:02:03 Uyire Uyire
        Subscribe for more
    """.trimIndent()

    @Test fun parsesTimestampLinesInOrder() {
        val c = ChapterPolicy.fromDescription(desc, 4000)
        assertEquals(listOf("Kadhal Rojave", "Chinna Chinna Aasai", "Vennilave", "Uyire Uyire"), c.map { it.title })
        assertEquals(listOf(0L, 275L, 550L, 3723L), c.map { it.startSec })
    }

    @Test fun parsesTrailingTimestamps() {
        val c = ChapterPolicy.fromDescription("Song A - 0:00\nSong B - 3:10\nSong C - 7:45", 600)
        assertEquals(listOf("Song A", "Song B", "Song C"), c.map { it.title })
    }

    @Test fun tooFewLinesMeansNoChapters() {
        assertTrue(ChapterPolicy.fromDescription("0:00 A\n3:00 B", 600).isEmpty())
    }

    @Test fun nextAndPreviousJumpBetweenSongs() {
        val c = listOf(Chapter("A", 0), Chapter("B", 200), Chapter("C", 400))
        assertTrue(ChapterPolicy.nextStartMs(c, 10_000L) == 200_000L)
        assertNull(ChapterPolicy.nextStartMs(c, 401_000L))
        assertTrue(ChapterPolicy.prevStartMs(c, 250_000L) == 200_000L)
        assertTrue(ChapterPolicy.prevStartMs(c, 201_000L) == 0L)
        assertNull(ChapterPolicy.prevStartMs(c, 1_000L))
    }
}
