package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

/** Upstream songs filters occasionally return explicitly labelled spoken recordings. */
object MusicSearchPolicy {
    private val spoken = Regex("\\b(discourse|audiobook|audio book|podcast|lecture|spoken word)\\b", RegexOption.IGNORE_CASE)
    fun songsOnly(items: List<Song>): List<Song> = items.filter {
        it.kind != "video" && !spoken.containsMatchIn(it.title)
    }
}
