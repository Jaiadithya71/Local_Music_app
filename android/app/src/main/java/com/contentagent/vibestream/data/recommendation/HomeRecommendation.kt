package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

object HomeRecommendation {
    data class Seed(val label: String, val query: String)
    fun seed(liked: List<Song>, recent: List<Song>, languages: List<String>, artists: List<String>): Seed {
        // A video uploader is not a reliable artist identity. Use the played title,
        // and tell the listener which signal explains this shelf.
        val anchor = (liked + recent).firstOrNull { it.title.isNotBlank() }
        if (anchor != null) {
            val title = anchor.title.substringBefore('|').trim().take(90)
            return Seed("Because you played ${title.take(32)}", "$title songs")
        }
        val artist = artists.firstOrNull { it.isNotBlank() }
        if (artist != null) return Seed("From your chosen artists", "$artist songs")
        val language = languages.firstOrNull { it.isNotBlank() }
        if (language != null) return Seed("From your chosen languages", "$language hit songs")
        return Seed("Explore music", "trending songs")
    }
}
