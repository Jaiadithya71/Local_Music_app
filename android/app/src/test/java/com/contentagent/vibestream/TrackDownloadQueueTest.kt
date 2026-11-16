package com.contentagent.vibestream

import com.contentagent.vibestream.data.local.TrackDownloadQueue
import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TrackDownloadQueueTest {
    @Test fun skipsSavedDeduplicatesAndRunsSequentially() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val transferred = mutableListOf<String>()
        val queued = mutableListOf<String>()
        val saved = mutableSetOf("saved")
        val queue = TrackDownloadQueue(scope, { it in saved }, { queued.add(it) }) { song ->
            transferred.add(song.id)
            if (song.id == "a") gate.await()
            saved.add(song.id)
            if (song.id == "b") done.complete(Unit)
        }
        try {
            queue.enqueue(listOf("saved", "a", "a", "b").map { Song(it, it, "Artist") })
            queue.enqueue(listOf(Song("a", "a", "Artist")))
            assertEquals(listOf("a", "b"), queued)
            assertEquals(listOf("a"), transferred)
            gate.complete(Unit)
            withTimeout(2000) { done.await() }
            queue.enqueue(listOf(Song("b", "b", "Artist")))
            assertEquals(listOf("a", "b"), transferred)
        } finally { scope.cancel() }
    }

    @Test fun failedSongCanBeRetried() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        val queue = TrackDownloadQueue(scope, { false }, {}) { attempts++ }
        try {
            queue.enqueue(listOf(Song("a", "Track", "Artist")))
            yield()
            queue.enqueue(listOf(Song("a", "Track", "Artist")))
            yield()
            assertEquals(2, attempts)
        } finally { scope.cancel() }
    }
}
