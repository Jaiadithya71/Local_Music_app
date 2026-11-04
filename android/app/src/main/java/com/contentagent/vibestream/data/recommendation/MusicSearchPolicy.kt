package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

/** Conservative recording-type checks. Duration, English titles and live music are not bans. */
object MusicSearchPolicy {
    private val spoken = Regex("\\b(discourse|audiobook|audio book|podcast|lecture|spoken word|interview|reaction|review|tutorial)\\b", RegexOption.IGNORE_CASE)
    private val compilation = Regex("\\b(jukebox|non[ -]?stop|compilation|full album|all songs|song collection)\\b", RegexOption.IGNORE_CASE)
    fun songsOnly(items: List<Song>): List<Song> = items.filter {
        it.kind != "video" && !spoken.containsMatchIn(it.title) && !compilation.containsMatchIn(it.title)
    }
}
