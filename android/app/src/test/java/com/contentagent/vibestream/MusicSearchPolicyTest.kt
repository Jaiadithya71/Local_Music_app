package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.MusicSearchPolicy
import org.junit.Assert.*
import org.junit.Test

class MusicSearchPolicyTest {
    private fun track(id: String, title: String, duration: Long = 240, kind: String = "song") =
        Song(id = id, title = title, artist = "Fixture", durationSec = duration, kind = kind)
    @Test fun songsModeRejectsExplicitSpokenRecordings() {
        val result = MusicSearchPolicy.songsOnly(listOf(track("a", "Tamil Discourse", 2700),
            track("b", "Podcast Episode"), track("c", "An Audiobook"), track("d", "Studio song")))
        assertEquals(listOf("d"), result.map { it.id })
    }
    @Test fun doesNotBanLongMusicDevotionalMusicOrOtherLanguages() {
        val items = listOf(track("a", "Long instrumental", 1800), track("b", "Stotram", 1300),
            track("c", "日本語の歌"), track("d", "Live song"))
        assertEquals(items, MusicSearchPolicy.songsOnly(items))
    }
    @Test fun videoItemsAndSubstringFalsePositivesAreHandled() {
        val result = MusicSearchPolicy.songsOnly(listOf(track("a", "Video", kind = "video"), track("b", "Lecturette")))
        assertEquals(listOf("b"), result.map { it.id })
    }
    @Test fun coverageMissesRegionalVersionNotInCatalog() {
        // The Hindi Srivalli is in the catalog but does not cover "srivalli tamil".
        val hindi = Song(id = "h", title = "Srivalli", artist = "Javed Ali")
        assertFalse(MusicSearchPolicy.coversQuery(listOf(hindi), "srivalli tamil"))
        assertTrue(MusicSearchPolicy.coversQuery(listOf(hindi), "srivalli"))
        val tamil = Song(id = "t", title = "Srivalli (Tamil) Pushpa - The Rise", artist = "Sid Sriram", kind = "video")
        assertTrue(MusicSearchPolicy.coversQuery(listOf(hindi, tamil), "srivalli tamil"))
    }
    @Test fun coverageIgnoresUploadDescriptorWords() {
        val song = Song(id = "a", title = "Kannana Kanne", artist = "Sid Sriram")
        assertTrue(MusicSearchPolicy.coversQuery(listOf(song), "kannana kanne full video song lyrics"))
        assertTrue(MusicSearchPolicy.coversQuery(emptyList(), "song"))
    }
    @Test fun musicVideosKeepSingleTracksAndDropSpokenOrLong() {
        val items = listOf(
            track("song", "Srivalli (Tamil) Pushpa", 230, kind = "video"),
            track("talk", "Movie review", 240, kind = "video"),
            track("mix", "Hits jukebox", 300, kind = "video"),
            track("long", "Concert", 3600, kind = "video"),
            track("ytmSong", "Studio track", 200, kind = "song"))
        assertEquals(listOf("song"), MusicSearchPolicy.musicVideosOnly(items).map { it.id })
    }
}
