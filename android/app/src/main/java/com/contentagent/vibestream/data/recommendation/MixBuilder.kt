package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.util.Locale

/** Local, deterministic mix policy. No claim of inferred mood or audio quality. */
object MixBuilder {
    private fun artist(s: Song) = s.artist.trim().lowercase(Locale.ROOT)
    private fun recording(s: Song) = artist(s) + "|" + s.title.trim().lowercase(Locale.ROOT)

    /** Interleave sources before limiting: a large liked list must not erase discovery. */
    fun build(
        liked: List<Song>, recent: List<Song>, playlistSongs: List<Song>, discovery: List<Song>,
        skippedArtists: Set<String> = emptySet(), limit: Int = 50
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val blocked = skippedArtists.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        val recentlyPlayed = recent.map { it.id }.toSet()
        val freshFirst = discovery.sortedBy { if (it.id in recentlyPlayed) 1 else 0 }
        val sources = listOf(liked, freshFirst, recent, playlistSongs).map { source ->
            source.filter { it.kind == "song" }
        }
        val candidates = mutableListOf<Song>()
        val ids = mutableSetOf<String>()
        val recordings = mutableSetOf<String>()
        for (i in 0 until (sources.maxOfOrNull { it.size } ?: 0)) {
            for (source in sources) {
                val s = source.getOrNull(i) ?: continue
                if (s.id !in ids && recording(s) !in recordings) {
                    ids.add(s.id)
                    recordings.add(recording(s))
                    candidates.add(s)
                }
            }
        }
        // Early skips are weak signals, not a permanent artist ban. Explicit likes win.
        val likedArtists = liked.map(::artist).toSet()
        val ordered = candidates.sortedBy { if (artist(it) in blocked && artist(it) !in likedArtists) 1 else 0 }
        candidates.clear()
        candidates.addAll(ordered)
        // Spread adjacent artists without pretending uploader metadata is perfect.
        val result = mutableListOf<Song>()
        while (candidates.isNotEmpty() && result.size < limit) {
            val last = result.lastOrNull()?.let(::artist)
            fun preferred(s: Song) = artist(s) !in blocked || artist(s) in likedArtists
            val next = candidates.indexOfFirst { preferred(it) && artist(it) != last }.takeIf { it >= 0 }
                ?: candidates.indexOfFirst { preferred(it) }.takeIf { it >= 0 }
                ?: candidates.indexOfFirst { artist(it) != last }.takeIf { it >= 0 } ?: 0
            result.add(candidates.removeAt(next))
        }
        return result
    }
}
