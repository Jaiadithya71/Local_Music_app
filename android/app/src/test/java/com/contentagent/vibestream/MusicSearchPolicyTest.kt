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
}
