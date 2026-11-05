package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.LibraryBackup
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.data.recommendation.SongSeedPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Restored backup playlists seed song radio without being marked played:
 * imports drive discovery of NEW songs and are not replayed by it.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportSeedsTest {
    @Test fun restoredPlaylistSeedsRadioWithoutPoisoningHistory() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val library = LibraryStore(ctx)
        val songs = (1..5).map { Song("abcdefghij$it".take(11).padEnd(11, '0'), "Song $it", "Artist $it") }
        library.mergeBackup(emptyList(), emptyList(), listOf(Playlist("Imported mix", songs)))
        val restored = library.playlists.value.first { it.name == "Imported mix" }.songs
        assertEquals(5, restored.size)
        assertTrue(library.playedKeys.value.isEmpty())
        val seeds = SongSeedPolicy.seeds(library.liked.value, restored, 0)
        assertTrue(seeds.isNotEmpty())
        assertTrue(seeds.all { it.id in songs.map { s -> s.id } })
        // Discovery still treats imported songs as known: they seed, not replay.
        val known = restored.flatMap { DiscoveryPolicy.keys(it) }.toSet()
        assertTrue(DiscoveryPolicy.fresh(restored, known).isEmpty())
    }

    @Test fun duplicateBackupSongsCollapseById() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val library = LibraryStore(ctx)
        val dup = Song("abcdefghijk", "Same song", "Artist")
        library.mergeBackup(emptyList(), emptyList(), listOf(Playlist("Imported mix", listOf(dup, dup.copy()))))
        assertEquals(1, library.playlists.value.first().songs.size)
    }
}
