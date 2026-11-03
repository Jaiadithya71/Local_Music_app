package com.contentagent.vibestream

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.player.QueueReconnect
import org.junit.Assert.*
import org.junit.Test

class QueueReconnectTest {
    private val songs = (1..50).map { Song("s$it", "Song $it", "Artist") }
    @Test fun liveSongRestoresAllFiftyAndUsesActualIndex() {
        val result = QueueReconnect.reconcile(songs, 0, "s20")
        assertEquals(50, result.songs.size)
        assertEquals(19, result.index)
        assertEquals("s21", result.songs[result.index + 1].id)
    }
    @Test fun coldStartKeepsSavedIndex() {
        assertEquals(7, QueueReconnect.reconcile(songs, 7, null).index)
    }
    @Test fun unrelatedLiveSongDoesNotAttachWrongPlaylist() {
        assertTrue(QueueReconnect.reconcile(songs, 7, "different").songs.isEmpty())
    }
    @Test fun missingOrInvalidSavedQueueIsSafe() {
        assertEquals(-1, QueueReconnect.reconcile(emptyList(), -1, "s1").index)
        assertEquals(-1, QueueReconnect.reconcile(songs, 99, null).index)
    }
}
