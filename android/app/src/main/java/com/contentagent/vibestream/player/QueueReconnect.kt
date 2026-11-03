package com.contentagent.vibestream.player

import com.contentagent.vibestream.data.model.Song

/** Reconcile the persisted logical playlist with the service's actual current ID. */
object QueueReconnect {
    data class Result(val songs: List<Song>, val index: Int)
    fun reconcile(saved: List<Song>, savedIndex: Int, liveId: String?): Result {
        if (liveId != null) {
            val index = saved.indexOfFirst { it.id == liveId }
            // An unrelated playlist must never silently become the next songs.
            return if (index >= 0) Result(saved, index) else Result(emptyList(), -1)
        }
        return if (savedIndex in saved.indices) Result(saved, savedIndex) else Result(emptyList(), -1)
    }
}
