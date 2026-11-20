package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.util.Locale

/** Edits only the unplayed tail of a discovery queue. Never relabels language. */
object LiveQueuePolicy {
    enum class Signal { LIKE, SKIP, PLAY }
    data class Feedback(val song: Song, val signal: Signal)

    fun update(
        queue: List<Song>, index: Int, candidates: List<Song>, feedback: Feedback,
        known: Set<String>, avoidedArtists: Set<String>, languages: List<String>, moods: List<String>,
        limit: Int = 40
    ): List<Song> {
        if (index !in queue.indices) return queue
        val fixed = queue.take(index + 1)
        val blocked = known + fixed.flatMap { DiscoveryPolicy.keys(it) }
        val upcoming = DiscoveryPolicy.fresh(queue.drop(index + 1), blocked, limit = limit)
        val additions = DiscoveryPolicy.fresh(candidates, blocked + upcoming.flatMap { DiscoveryPolicy.keys(it) }, limit = 12)
        val artist = feedback.song.artist.trim().lowercase(Locale.ROOT)
        val avoided = avoidedArtists.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val all = (upcoming + additions).filter {
            // An explicit skip removes that artist from the upcoming discovery
            // tail this session. Saved playlists never pass through this policy.
            feedback.signal != Signal.SKIP || artist.isBlank() || artist == "unknown artist" || it.artist.trim().lowercase(Locale.ROOT) != artist
        }
        val ranked = all.sortedWith(compareBy<Song>(
            { if (it.artist.trim().lowercase(Locale.ROOT) in avoided) 1 else 0 },
            { if (feedback.signal == Signal.LIKE && it.artist.trim().lowercase(Locale.ROOT) == artist) 0 else 1 },
            { if (feedback.signal == Signal.LIKE && additions.any { a -> a.id == it.id }) 0 else 1 },
            { SongSeedPolicy.languageRank(it, languages) },
            { song -> if (moods.any { mood -> (song.title + " " + song.artist).contains(mood, ignoreCase = true) }) 0 else 1 }
        ))
        return fixed + ranked.take(limit)
    }
}
