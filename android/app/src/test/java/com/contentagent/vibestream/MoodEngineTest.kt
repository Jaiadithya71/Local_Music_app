package com.contentagent.vibestream

import com.contentagent.vibestream.data.local.PlayEventTracker
import com.contentagent.vibestream.data.local.PlaySnapshot
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.*
import org.junit.Assert.*
import org.junit.Test

class MoodEngineTest {
    private val now = 1_800_000_000_000L
    private val night = MoodState("late_night", "wired", "still")
    private fun song(id: String, title: String, artist: String = "Artist $id") =
        Song(id = id, title = title, artist = artist, durationSec = 200)
    private fun ev(i: Int, title: String, artist: String, outcome: String, hour: Int = 23, route: String = "wired", motion: String = "still", liked: Boolean = false) =
        PlayEvent(songId = "h$i", artist = artist, title = title, startMs = now - (60L + i) * 3_600_000L,
            listenedSec = 150, durationSec = 200, outcome = outcome, hour = hour, route = route, motion = motion, liked = liked)

    /** 40 plays: slow melody songs finished late at night on headphones, workout songs skipped early. */
    private fun nightLog(): List<PlayEvent> = (0 until 40).map { i ->
        if (i % 2 == 0) ev(i, "slow melody song $i", "Singer ${i % 7}", MoodEngine.FINISHED)
        else ev(i, "workout dance mass $i", "Beat ${i % 5}", MoodEngine.SKIP_EARLY)
    }

    @Test fun tagsComeFromTitleAndArtist() {
        assertEquals(setOf("tamil", "slow", "melody"), MoodEngine.tagsOf("Tamil Slow Melody", "X"))
        assertTrue(MoodEngine.tagsOf("Hello", "World").isEmpty())
    }

    @Test fun hourBuckets() {
        assertEquals("morning", MoodEngine.bucket(7)); assertEquals("afternoon", MoodEngine.bucket(14))
        assertEquals("evening", MoodEngine.bucket(19)); assertEquals("late_night", MoodEngine.bucket(2))
    }

    @Test fun belowThirtyPlaysNothingChanges() {
        val few = nightLog().take(10)
        val list = listOf(song("a", "workout dance"), song("b", "slow melody"))
        assertEquals(list, MoodRanker(few, now, night, null).rank(list))
        assertNull(MoodEngine.currentMood(few, now, night, emptyMap()))
    }

    @Test fun learnedStatePutsSlowSongsFirstAndWorkoutLast() {
        val list = listOf(song("w", "workout dance mass"), song("m", "slow melody night"))
        val out = MoodRanker(nightLog(), now, night, null).rank(list)
        assertEquals("m", out.first().id)
    }

    @Test fun sameArtistNeverTwiceInARowWhenAvoidable() {
        val list = listOf(song("a", "slow melody", "Same"), song("b", "slow melody two", "Same"),
            song("c", "calm soft", "Other"), song("d", "relax soft", "Third"))
        val out = MoodRanker(nightLog(), now, night, null).rank(list)
        for (i in 1 until out.size) if (i < 3) assertNotEquals(out[i - 1].artist, out[i].artist)
    }

    @Test fun oneSlotInFiveGoesToANovelSong() {
        val known = (0 until 10).map { song("k$it", "slow melody known $it", "Singer ${it % 7}x$it") }
        val novel = (0 until 3).map { song("n$it", "zzz fresh $it", "Newbie $it") }
        val out = MoodRanker(nightLog(), now, night, null).rank(known + novel)
        assertEquals(13, out.size)
        assertTrue(out[4].id.startsWith("n"))
    }

    @Test fun moodFollowsTagsAndCorrectionWins() {
        val devo = (0 until 40).map { ev(it, "murugan bhakti suprabhatam $it", "Sing$it", MoodEngine.FINISHED, hour = 7, route = "speaker") }
        val st = MoodState("morning", "speaker", "still")
        assertEquals(Mood.DEVOTIONAL, MoodEngine.currentMood(devo, now, st, emptyMap()))
        assertEquals(Mood.FOCUS, MoodEngine.currentMood(devo, now, st, mapOf(st.key to "FOCUS")))
    }

    @Test fun notFeelingThisAfterThreeEarlySkips() {
        val skips = (0 until 3).map { PlayEvent(songId = "s$it", artist = "A$it", title = "t", startMs = now - it * 60_000L, outcome = MoodEngine.SKIP_EARLY) }
        assertTrue(MoodEngine.notFeelingThis(skips, now))
        assertFalse(MoodEngine.notFeelingThis(skips.take(2), now))
    }

    @Test fun twoEarlySkipsDropThatLaneForNextPicks() {
        val log = nightLog() + listOf(
            PlayEvent(songId = "x1", artist = "Lane", title = "chill lofi one", startMs = now - 120_000L, outcome = MoodEngine.SKIP_EARLY, hour = 23, route = "wired"),
            PlayEvent(songId = "x2", artist = "Lane", title = "chill lofi two", startMs = now - 60_000L, outcome = MoodEngine.SKIP_EARLY, hour = 23, route = "wired"))
        val list = listOf(song("a", "slow melody", "Lane"), song("b", "soft calm", "Fresh"))
        assertEquals("b", MoodRanker(log, now, night, null).rank(list).first().id)
    }

    @Test fun explainNamesTheReasons() {
        val text = MoodRanker(nightLog(), now, night, Mood.CHILL).explain(song("m", "slow melody"))
        assertTrue(text.contains("late night") && text.contains("slow") && text.contains("score"))
    }

    @Test fun trackerClassifiesOutcomes() {
        val got = mutableListOf<PlayEvent>()
        val t = PlayEventTracker({ got.add(it) }, { PlaySnapshot(hour = 22, route = "wired") }, { it == "a" })
        val a = song("a", "A"); val b = song("b", "B"); val c = song("c", "C"); val d = song("d", "D")
        t.begin(a, 0, 0, false, "home_mix", 1000L)
        t.begin(b, 10_000L, 200_000L, false, "home_mix", 20_000L)      // a skipped early
        t.begin(c, 100_000L, 200_000L, false, "queue", 130_000L)       // b skipped late
        t.begin(d, 0, 0, true, "queue", 400_000L)                      // c finished (auto)
        assertEquals(listOf(MoodEngine.SKIP_EARLY, MoodEngine.SKIP_LATE, MoodEngine.FINISHED), got.map { it.outcome })
        assertTrue(got[0].liked); assertFalse(got[1].liked)
        assertEquals("wired", got[0].route)
    }

    @Test fun retryOfSameSongIsNotANewPlay() {
        val got = mutableListOf<PlayEvent>()
        val t = PlayEventTracker({ got.add(it) }, { PlaySnapshot() })
        val a = song("a", "A")
        t.begin(a, 0, 0, false, "queue", 1000L)
        t.begin(a, 5_000L, 200_000L, false, "queue", 6000L)
        assertTrue(got.isEmpty())
        t.begin(a, 190_000L, 200_000L, false, "queue", 200_000L)
        assertEquals(listOf(MoodEngine.REPLAYED), got.map { it.outcome })
    }
}
