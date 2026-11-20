package com.contentagent.vibestream.data.local

import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One transfer at a time; repeated taps never add a second copy. */
class TrackDownloadQueue(
    scope: CoroutineScope,
    private val isSaved: (String) -> Boolean,
    private val onQueued: (Song) -> Unit,
    private val transfer: suspend (Song) -> Unit
) {
    private val pending = mutableSetOf<String>()
    private val channel = Channel<Song>(Channel.UNLIMITED)
    init {
        scope.launch {
            for (song in channel) {
                try { transfer(song) }
                finally { synchronized(pending) { pending.remove(song.id) } }
            }
        }
    }
    fun enqueue(songs: List<Song>) = synchronized(pending) {
        songs.forEach { song ->
            if (!isSaved(song.id) && pending.add(song.id)) {
                onQueued(song)
                channel.trySend(song)
            }
        }
    }
}
