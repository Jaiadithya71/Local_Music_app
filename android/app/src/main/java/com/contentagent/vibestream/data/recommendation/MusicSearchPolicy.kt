package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.util.Locale

/** Conservative recording-type checks. Duration, English titles and live music are not bans. */
object MusicSearchPolicy {
    private val spoken = Regex("\\b(discourse|audiobook|audio book|podcast|lecture|spoken word|interview|reaction|review|tutorial)\\b", RegexOption.IGNORE_CASE)
    private val compilation = Regex("\\b(jukebox|non[ -]?stop|compilation|full album|all songs|song collection)\\b", RegexOption.IGNORE_CASE)
    // Query words that describe the upload, not the music itself; they never decide coverage.
    private val queryStopwords = setOf("song", "songs", "full", "video", "lyrics", "lyrical", "audio", "official", "the", "from", "status")

    fun songsOnly(items: List<Song>): List<Song> = items.filter {
        it.kind != "video" && !spoken.containsMatchIn(it.title) && !compilation.containsMatchIn(it.title)
    }

    /** Discovery uses the same vetted single-track upload fallback as music search.
     * A YouTube source is not itself evidence that a recording is non-music.
     */
    fun discoveryTracks(items: List<Song>): List<Song> {
        return items.filter { songsOnly(listOf(it)).isNotEmpty() || musicVideosOnly(listOf(it)).isNotEmpty() }
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ")

    private fun queryTokens(query: String): List<String> =
        normalize(query).split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in queryStopwords }

    /**
     * True when at least one result covers every meaningful query word in its title or artist.
     * YouTube Music's anonymous catalog skips some regional tracks; a false here tells the
     * search path to add matching YouTube uploads so the song is still reachable.
     */
    fun coversQuery(items: List<Song>, query: String): Boolean {
        val tokens = queryTokens(query)
        if (tokens.isEmpty()) return true
        return items.any { song ->
            val hay = normalize(song.title + " " + song.artist)
            tokens.all { hay.contains(it) }
        }
    }

    /**
     * Keeps YouTube uploads that behave like a single music track: not spoken word or a
     * compilation, and between one minute and fifteen minutes long. Duration 0 (unknown)
     * is kept, since relevance ranking already put the upload near the top.
     */
    fun musicVideosOnly(items: List<Song>): List<Song> = items.filter {
        it.kind == "video" &&
            !spoken.containsMatchIn(it.title) && !compilation.containsMatchIn(it.title) &&
            (it.durationSec == 0L || it.durationSec in 60..900)
    }
}
