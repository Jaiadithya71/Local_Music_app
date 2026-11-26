package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.data.recommendation.NonSongPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonSongPolicyTest {
    private fun song(id: String, title: String, sec: Long) = Song(id = id, title = title, artist = "A$id", durationSec = sec)

    @Test fun trailersAndCompilationsAreNotSongs() {
        assertTrue(NonSongPolicy.isNonSong(song("1", "SOORI 07 - OFFICIAL TRAILER | Soori", 228)))
        assertTrue(NonSongPolicy.isNonSong(song("2", "Viral Chill Songs Playlist 2025 Non-Stop Trending", 732)))
        assertTrue(NonSongPolicy.isNonSong(song("3", "Some long upload", 1800)))
    }

    @Test fun normalSongsPass() {
        assertFalse(NonSongPolicy.isNonSong(song("4", "Pre Workout (Official Video)", 327)))
        assertFalse(NonSongPolicy.isNonSong(song("5", "Vazhimuzhudhum Varuvaayaa - Lyric Video", 189)))
    }

    @Test fun freshDropsNonSongs() {
        val out = DiscoveryPolicy.fresh(
            listOf(song("1", "Movie Trailer", 200), song("4", "Real Song", 200)),
            emptySet(), musicOnly = false
        )
        assertEquals(listOf("4"), out.map { it.id })
    }
}
