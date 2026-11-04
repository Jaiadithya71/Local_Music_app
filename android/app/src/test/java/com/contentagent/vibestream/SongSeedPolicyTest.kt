package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.*
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import org.junit.Assert.*
import org.junit.Test

class SongSeedPolicyTest {
    private fun s(id: String, title: String = id) = Song(id, title, "Artist $id")
    @Test fun onlyExplicitValidSeedsRotateAndStayBounded() {
        val songs = (1..5).map { s("aaaaaaaaaa$it") }
        assertEquals(songs.take(3), SongSeedPolicy.seeds(songs, songs, 0))
        assertEquals(listOf(songs[4], songs[0], songs[1]), SongSeedPolicy.seeds(songs, emptyList(), 4))
        assertTrue(SongSeedPolicy.seeds(listOf(s("bad")), emptyList(), 1).isEmpty())
    }
    @Test fun filtersCompilationsButKeepsLongLiveAndEnglishTamilTitles() {
        val candidates = listOf(s("1", "Tamil Jukebox"), s("2", "Non-stop hits"), s("3", "Album review"),
            s("4", "Nenjukkul Peidhidum"), s("5", "Live at Chennai").copy(durationSec = 1800),
            s("6", "Stotram").copy(durationSec = 1600), s("7", "Lecturette"))
        assertEquals(listOf("4", "5", "6", "7"), MusicSearchPolicy.songsOnly(candidates).map { it.id })
    }
    @Test fun languageHintRanksRatherThanRejectingUnknownTitles() {
        val englishTitle = s("1", "Naa Ready")
        val other = s("2", "Song [Hindi]")
        val tamil = s("3", "தமிழ் பாடல்")
        assertEquals(listOf(tamil, englishTitle, other), SongSeedPolicy.blend(listOf(other, englishTitle, tamil), emptyList(), listOf("Tamil")))
    }
    @Test fun explorationSurvivesAndKnownTracksNeverLeak() {
        val a = (1..20).map { s("radio$it") }; val b = (1..20).map { s("search$it") }
        val blend = SongSeedPolicy.blend(a, b, emptyList())
        assertEquals(listOf(a[0], b[0], a[1], b[1]), blend.take(4))
        val known = DiscoveryPolicy.keys(a[0]) + DiscoveryPolicy.keys(b[0])
        assertFalse(DiscoveryPolicy.fresh(blend, known).any { it.id in setOf(a[0].id, b[0].id) })
        assertEquals(b, SongSeedPolicy.blend(emptyList(), b, emptyList()))
    }
    @Test fun radioParserReadsRecordingMetadataAndRejectsExplicitSpoken() {
        val body = """{"contents":[{"playlistPanelVideoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"Tamil song"}]},"longBylineText":{"runs":[{"text":"Actual artist"}]},"lengthText":{"runs":[{"text":"4:12"}]}}},{"playlistPanelVideoRenderer":{"videoId":"bbbbbbbbbbb","title":{"runs":[{"text":"Podcast"}]}}}]}"""
        val songs = YouTubeExtractor().parseRadio(body)
        assertEquals(1, songs.size); assertEquals("Actual artist", songs[0].artist); assertEquals(252L, songs[0].durationSec)
        assertTrue(YouTubeExtractor().parseRadio("{}").isEmpty())
    }
}
