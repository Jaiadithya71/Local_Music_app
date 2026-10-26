package com.contentagent.vibestream

import com.contentagent.vibestream.data.chapters.SongChapter
import com.contentagent.vibestream.data.chapters.SongChapters
import org.junit.Assert.*
import org.junit.Test

class SongChaptersTest {
    private val tracks = listOf(SongChapter("First", 0), SongChapter("Second", 268000), SongChapter("Third", 543000))
    @Test fun timestampBeforeTitle() {
        assertEquals(tracks, SongChapters.fromDescription("00:00 First\n04:28 Second\n09:03 Third", 2700000))
    }
    @Test fun timestampAfterTitleAndHourFormat() {
        assertEquals(listOf(0L, 3599000L, 3600000L), SongChapters.fromDescription("First - 00:00\nSecond - 59:59\nThird - 1:00:00").map { it.startMs })
    }
    @Test fun filtersOutOfDurationAndDuplicateNativeChapters() {
        assertEquals(tracks.take(2), SongChapters.validated(tracks + tracks[0] + SongChapter("Negative", -2), 543000))
    }
    @Test fun ignoresSingleTimestampAndMalformedOrScrambledLists() {
        assertTrue(SongChapters.fromDescription("My favorite part 04:28").isEmpty())
        assertTrue(SongChapters.fromDescription("09:03 Third\n00:00 First").isEmpty())
        assertTrue(SongChapters.fromDescription("00:00\n04:28").isEmpty())
        assertTrue(SongChapters.fromDescription("0:60 Bad\n1:99 Bad").isEmpty())
        assertTrue(SongChapters.fromDescription("00:00 First 04:28 Second").isEmpty())
    }
    @Test fun nextUsesActualUnequalBoundariesAndExitsAtLast() {
        assertEquals(268000L, SongChapters.next(tracks, 0))
        assertEquals(268000L, SongChapters.next(tracks, 100000))
        assertEquals(543000L, SongChapters.next(tracks, 268000))
        assertEquals(543000L, SongChapters.next(tracks, 267900))
        assertNull(SongChapters.next(tracks, 544000))
        assertNull(SongChapters.next(emptyList(), 0))
    }
    @Test fun previousRestartsCurrentThenGoesBack() {
        assertEquals(268000L, SongChapters.previous(tracks, 280000))
        assertEquals(0L, SongChapters.previous(tracks, 269000))
        assertEquals(0L, SongChapters.previous(tracks, 0))
        assertNull(SongChapters.previous(emptyList(), 0))
    }
    @Test fun missingInitialZeroDoesNotInventATitledSong() {
        assertEquals(10000L, SongChapters.next(listOf(SongChapter("A", 10000), SongChapter("B", 20000)), 0))
    }
}
