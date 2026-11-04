package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

/** No inferred full plays: only explicit likes/saves seed radio in this first step. */
object SongSeedPolicy {
    fun seeds(liked: List<Song>, saved: List<Song>, round: Int): List<Song> {
        val all = MusicSearchPolicy.songsOnly(liked + saved).filter { Regex("[A-Za-z0-9_-]{11}").matches(it.id) }.distinctBy { it.id }
        if (all.isEmpty()) return emptyList()
        val start = Math.floorMod(round, all.size)
        return (all.drop(start) + all.take(start)).take(3)
    }

    // Unknown/English-only titles stay eligible. Explicit language labels are a
    // ranking hint, never proof of the language actually sung.
    fun languageRank(song: Song, languages: List<String>): Int {
        if (languages.isEmpty()) return 1
        val t = song.title.lowercase()
        val labels = listOf("Tamil", "Hindi", "Telugu", "Malayalam", "Kannada", "English", "Japanese", "Korean")
        val matches = labels.filter { Regex("\\b${it.lowercase()}\\b").containsMatchIn(t) }
        if (matches.any { m -> languages.any { it.equals(m, true) } }) return 0
        if (languages.any { it.equals("Tamil", true) } && t.any { it in '\u0B80'..'\u0BFF' }) return 0
        return if (matches.isEmpty()) 1 else 2
    }

    /** Interleave radio and keyword candidates: a large radio pool cannot erase exploration. */
    fun blend(related: List<Song>, search: List<Song>, languages: List<String>): List<Song> {
        val a = related.sortedBy { languageRank(it, languages) }
        val b = search.sortedBy { languageRank(it, languages) }
        return buildList {
            for (i in 0 until maxOf(a.size, b.size)) {
                a.getOrNull(i)?.let { add(it) }
                b.getOrNull(i)?.let { add(it) }
            }
        }.distinctBy { it.id }
    }
}
