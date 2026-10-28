package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy as D
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

class DiscoveryPolicyTest {
    private fun song(id: String, title: String = "Song $id", artist: String = "Artist $id", kind: String = "song") = Song(id, title, artist, kind = kind)
    @Test fun sameIdAndAlternateUploadDoNotReturn() {
        val played = song("old", "Pachai Nirame (Official Audio)", "Publisher")
        assertTrue(D.fresh(listOf(played, song("alt", "Pachai Nirame [Lyrics]", "Different uploader")), D.keys(played)).isEmpty())
    }
    @Test fun fullLibraryCannotCrowdOutUnheardCandidates() {
        val known = (1..80).map { song("known$it") }
        val fresh = (1..30).map { song("new$it") }
        val feed = D.fresh(known + fresh, known.flatMap { D.keys(it) }.toSet())
        assertEquals(30, feed.size)
        assertTrue(feed.all { it.id.startsWith("new") })
    }
    @Test fun exhaustedCatalogDoesNotFallBackToReplay() {
        val known = song("old")
        assertTrue(D.fresh(listOf(known), D.keys(known)).isEmpty())
        assertTrue(D.fresh(emptyList(), D.keys(known)).isEmpty())
    }
    @Test fun freshIsImmediatelyRemovedAfterPlayEvenFromCachedPool() {
        val pool = listOf(song("one"), song("two"))
        val first = D.fresh(pool, emptySet())
        assertEquals(listOf("two"), D.fresh(pool, D.keys(first.first())).map { it.id })
    }
    @Test fun repeatedRecordingsInCandidatePoolAreDeduplicated() {
        assertEquals(1, D.fresh(listOf(song("a", "Track (Audio)"), song("b", "TRACK [Official Video]")), emptySet()).size)
    }
    @Test fun tamilUnicodePreserved() {
        assertNotEquals(D.titleKey("பச்சை நிறமே"), D.titleKey("வெண்ணிலவே"))
        assertEquals("பச்சை நிறமே", D.titleKey("பச்சை நிறமே (Official Audio)"))
    }
    @Test fun musicModeRejectsSpokenAndVideos() {
        val pool = listOf(song("a", "Discourse"), song("b", kind = "video"), song("c"))
        assertEquals(listOf("c"), D.fresh(pool, emptySet()).map { it.id })
        assertEquals(3, D.fresh(pool, emptySet(), musicOnly = false).size)
    }
    @Test fun refreshChangesQueriesButNotLanguage() {
        val a = D.queries(listOf("Tamil"), listOf("Chill"), emptyList(), 0)
        val b = D.queries(listOf("Tamil"), listOf("Chill"), emptyList(), 1)
        assertNotEquals(a, b)
        assertTrue((a+b).all { it.startsWith("Tamil ") })
    }
    @Test fun neverPadsSmallPoolAndRespectsLimit() {
        assertEquals(1, D.fresh(listOf(song("a")), emptySet(), limit = 20).size)
        assertTrue(D.fresh(listOf(song("a")), emptySet(), limit = 0).isEmpty())
    }
    @Test fun fixtureNewToKnownOverlapIsZero() {
        val retained = (1..30).map { song("played$it") }
        val saved = (1..60).map { song("liked$it") }
        val pool = (retained + saved + (1..40).map { song("fresh$it") }).shuffled(java.util.Random(4))
        val known = (retained + saved).flatMap { D.keys(it) }.toSet()
        val result = D.fresh(pool, known)
        assertEquals(40, result.size)
        assertTrue(result.flatMap { D.keys(it) }.none { it in known })
    }
}

@RunWith(AndroidJUnit4::class)
class DiscoveryPersistenceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun song(i: Int) = Song("id$i", "Song $i", "Artist")
    @Test fun ledgerSurvivesThirtyItemWindowAndRecreation() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        (1..45).forEach { lib.addRecent(song(it)) }
        assertEquals(30, lib.recent.value.size)
        assertTrue(LibraryStore(ctx).playedKeys.value.containsAll(D.keys(song(1))))
        assertEquals(90, LibraryStore(ctx).playedKeys.value.size)
    }
    @Test fun migrationPreservesLikedPlaylistsAndRecent() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        lib.toggleLike(song(1)); lib.addRecent(song(2)); lib.createPlaylist("Keep"); lib.addToPlaylist("Keep", song(3))
        ctx.getSharedPreferences("vibestream_library", 0).edit().remove("played_keys_v1").commit()
        val upgraded = LibraryStore(ctx)
        assertEquals(listOf("id1"), upgraded.liked.value.map { it.id })
        assertEquals(listOf("id2"), upgraded.recent.value.map { it.id })
        assertEquals("id3", upgraded.playlists.value.single().songs.single().id)
        assertTrue(upgraded.playedKeys.value.containsAll(D.keys(song(2))))
    }
    @Test fun importedHistoryBeyondThirtyIsRemembered() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        lib.mergeBackup(emptyList(), (1..45).map { song(it) }, emptyList())
        assertEquals(30, lib.recent.value.size)
        assertTrue(LibraryStore(ctx).playedKeys.value.containsAll(D.keys(song(45))))
    }
}
