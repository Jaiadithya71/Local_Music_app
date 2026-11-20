package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.text.Normalizer
import java.util.Locale

/** Discovery is separate from replay. Never pad an exhausted feed with known songs. */
object DiscoveryPolicy {
    fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).substringBefore('|')
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("\\b(official|music video|lyric video|lyrics|lyrical|audio|video|hd|4k|remastered)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun keys(song: Song): Set<String> = buildSet {
        if (song.id.isNotBlank()) add("id:${song.id}")
        val title = titleKey(song.title)
        if (title.isNotBlank()) add("title:$title")
    }

    fun fresh(candidates: List<Song>, known: Set<String>, skippedArtists: Set<String> = emptySet(), limit: Int = 50, musicOnly: Boolean = true): List<Song> {
        val seen = known.toMutableSet()
        val unique = candidates.filter { song ->
            (!musicOnly || MusicSearchPolicy.discoveryTracks(listOf(song)).isNotEmpty()) &&
                keys(song).none { it in seen } && keys(song).also { seen.addAll(it) }.isNotEmpty()
        }.sortedBy { if (it.artist.trim().lowercase(Locale.ROOT) in skippedArtists.map { a -> a.trim().lowercase(Locale.ROOT) }) 1 else 0 }
        val pending = unique.toMutableList()
        val out = mutableListOf<Song>()
        while (pending.isNotEmpty() && out.size < limit.coerceAtLeast(0)) {
            val last = out.lastOrNull()?.artist
            val i = pending.indexOfFirst { it.artist != last }.takeIf { it >= 0 } ?: 0
            out.add(pending.removeAt(i))
        }
        return out
    }

    /** Counts only, suitable for a screenshot without exposing titles, IDs or tokens. */
    fun diagnostic(candidates: List<Song>, known: Set<String>, musicOnly: Boolean): String {
        val qualified = if (musicOnly) MusicSearchPolicy.discoveryTracks(candidates) else candidates
        val hidden = qualified.count { song -> keys(song).any { it in known } }
        val visible = fresh(candidates, known, limit = candidates.size, musicOnly = musicOnly).size
        return "Fetched ${candidates.size} | catalog ${candidates.count { it.kind != "video" }} | uploads ${candidates.count { it.kind == "video" }} | filtered ${candidates.size - qualified.size} | known $hidden | duplicates ${qualified.size - hidden - visible} | visible $visible"
    }

    fun queries(languages: List<String>, moods: List<String>, artists: List<String>, round: Int): List<String> {
        val langs = languages.filter { it.isNotBlank() }.ifEmpty { listOf("Tamil", "Hindi") }
        val variant = listOf("new releases", "indie songs", "melody songs", "hidden gems songs")[Math.floorMod(round, 4)]
        return (langs.map { "$it $variant" } +
            moods.filter { it.isNotBlank() }.take(2).flatMap { m -> langs.map { "$it $m songs" } } +
            artists.filter { it.isNotBlank() }.take(3).map { "$it songs" }).distinct()
    }
}
