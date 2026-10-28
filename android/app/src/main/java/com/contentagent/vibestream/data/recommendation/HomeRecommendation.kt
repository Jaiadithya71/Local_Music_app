package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

object HomeRecommendation {
    data class Seed(val label: String, val query: String)
    fun seed(liked: List<Song>, recent: List<Song>, languages: List<String>, artists: List<String>): Seed {
        val language = languages.firstOrNull { it.isNotBlank() }
        if (language != null) return Seed("Discover $language", "$language new releases")
        val artist = artists.firstOrNull { it.isNotBlank() }
        if (artist != null) return Seed("Discover from your chosen artists", "$artist songs")
        return Seed("Explore music", "Tamil new releases")
    }
}
