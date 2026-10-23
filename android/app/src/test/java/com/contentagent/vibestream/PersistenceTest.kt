package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.local.QueueStore
import com.contentagent.vibestream.data.lyrics.LyricsRepository
import com.contentagent.vibestream.data.model.Song
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun song(id: String, stream: String? = null) =
        Song(id = id, title = "Title$id", artist = "Artist$id", durationSec = 200, streamUrl = stream)

    @Test
    fun queueStore_roundtrip_stripsSecretsAndKeepsState() {
        ctx.getSharedPreferences("vibestream_queue", 0).edit().clear().commit()
        QueueStore(ctx).save(
            listOf(song("a", "https://secret-stream-a"), song("b", "https://secret-stream-b")),
            index = 1, positionMs = 12345, shuffle = true, repeat = 2
        )
        val loaded = QueueStore(ctx).load()!!
        assertEquals(listOf("a", "b"), loaded.songs.map { it.id })
        assertNull("streamUrl must not be persisted", loaded.songs[0].streamUrl)
        assertNull("streamUrl must not be persisted", loaded.songs[1].streamUrl)
        assertEquals(1, loaded.index)
        assertEquals(12345L, loaded.positionMs)
        assertTrue(loaded.shuffle)
        assertEquals(2, loaded.repeat)
    }

    @Test
    fun queueStore_empty_loadsNull_soNoAutoplayOnColdStart() {
        ctx.getSharedPreferences("vibestream_queue", 0).edit().clear().commit()
        assertNull(QueueStore(ctx).load())
    }

    @Test
    fun queueStore_corruptJson_loadsNull_noCrashNoDataLoss() {
        ctx.getSharedPreferences("vibestream_queue", 0).edit().putString("songs", "{not json").commit()
        assertNull(QueueStore(ctx).load())
    }

    @Test
    fun libraryMerge_dedupesLikedAndPreservesExisting() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        lib.mergeBackup(likedSongs = listOf(song("a"), song("b")), recentSongs = emptyList(), backupPlaylists = emptyList())
        lib.mergeBackup(likedSongs = listOf(song("b"), song("c")), recentSongs = emptyList(), backupPlaylists = emptyList())
        assertEquals(listOf("a", "b", "c"), lib.liked.value.map { it.id })
        // persisted across instances
        assertEquals(listOf("a", "b", "c"), LibraryStore(ctx).liked.value.map { it.id })
    }

    @Test
    fun libraryMerge_playlistMergesByNameDedupesSongs() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        lib.mergeBackup(emptyList(), emptyList(), listOf(Playlist("Gym", listOf(song("a")))))
        lib.mergeBackup(emptyList(), emptyList(), listOf(Playlist("gym", listOf(song("a"), song("b")))))
        val pl = lib.playlists.value.single { it.name.equals("Gym", true) }
        assertEquals(listOf("a", "b"), pl.songs.map { it.id })
    }

    @Test
    fun recentMerge_trimsToThirty() {
        ctx.getSharedPreferences("vibestream_library", 0).edit().clear().commit()
        val lib = LibraryStore(ctx)
        lib.mergeBackup(emptyList(), (1..40).map { song("s$it") }, emptyList())
        assertEquals(30, lib.recent.value.size)
    }
}

@RunWith(AndroidJUnit4::class)
class LrcParseTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun parseLrc_timestampsSortedAndShifted() {
        val lrc = "[00:10.50]second line\n[00:01.00]first line\n[01:05.00]last line"
        val lines = LyricsRepository(ctx).parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals(1000L, lines[0].t)
        assertEquals("first line", lines[0].text)
        assertEquals(10500L, lines[1].t)
        assertEquals(65000L, lines[2].t)
    }

    @Test
    fun parseLrc_ignoresMetadataAndBlanks() {
        val lrc = "[ti:Song Title]\n[ar:Artist]\n\n[00:02.00]hello"
        val lines = LyricsRepository(ctx).parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals("hello", lines[0].text)
    }
}
