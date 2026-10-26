package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.MixBuilder
import org.junit.Assert.*
import org.junit.Test

class MixBuilderTest {
    private fun song(id: String, artist: String = "Artist $id", title: String = "Song $id", kind: String = "song") =
        Song(id = id, title = title, artist = artist, kind = kind)

    @Test fun largeLibraryDoesNotCrowdOutDiscovery() {
        val mix = MixBuilder.build((1..80).map { song("liked$it") }, emptyList(), emptyList(),
            (1..20).map { song("fresh$it") })
        assertEquals(50, mix.size)
        assertTrue(mix.count { it.id.startsWith("fresh") } >= 15)
    }
    @Test fun duplicateRecordingAndIdAreNotRepeated() {
        val mix = MixBuilder.build(listOf(song("a", "Artist", "Track")),
            listOf(song("b", " artist ", "TRACK ")), emptyList(), listOf(song("a")))
        assertEquals(1, mix.size)
    }
    @Test fun consecutiveArtistsSpreadWhenPossible() {
        val mix = MixBuilder.build(listOf(song("a", "A"), song("b", "A"), song("c", "B")),
            emptyList(), emptyList(), emptyList())
        assertEquals(listOf("A", "B", "A"), mix.map { it.artist })
    }
    @Test fun skippedArtistsDeprioritizedNotPermanentlyBanned() {
        val mix = MixBuilder.build(emptyList(), listOf(song("a", "Artist")), emptyList(),
            listOf(song("b", "Other")), setOf(" artist "))
        assertEquals(listOf("b", "a"), mix.map { it.id })
    }
    @Test fun explicitLikesOutrankOldEarlySkips() {
        val mix = MixBuilder.build(listOf(song("a", "Artist")), emptyList(), emptyList(),
            listOf(song("b", "Other")), setOf("Artist"))
        assertEquals("a", mix.first().id)
    }
    @Test fun videoResultsNeverEnterMusicMix() {
        val mix = MixBuilder.build(emptyList(), emptyList(), emptyList(),
            listOf(song("a", kind = "video"), song("b")))
        assertEquals(listOf("b"), mix.map { it.id })
    }
    @Test fun emptySourcesAndZeroLimitAreSafe() {
        assertTrue(MixBuilder.build(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty())
        assertTrue(MixBuilder.build(listOf(song("a")), emptyList(), emptyList(), emptyList(), limit = 0).isEmpty())
    }
    @Test fun everyAvailableSourceContributesBeforeTruncation() {
        val mix = MixBuilder.build(listOf(song("liked")), listOf(song("recent")), listOf(song("playlist")),
            listOf(song("discovery")), limit = 4)
        assertEquals(setOf("liked", "recent", "playlist", "discovery"), mix.map { it.id }.toSet())
    }
    @Test fun namedMixDoesNotImportUnrelatedLibrary() {
        val mix = MixBuilder.build(emptyList(), emptyList(), emptyList(), listOf(song("seed")))
        assertEquals(listOf("seed"), mix.map { it.id })
    }
}
