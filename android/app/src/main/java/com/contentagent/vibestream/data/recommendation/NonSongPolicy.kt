package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.util.Locale

/** Trailers, compilations and other non-song uploads that should never be suggested as a single song. */
object NonSongPolicy {
    private val words = Regex(
        "\\b(trailer|teaser|promo|glimpse|first look|motion poster|making of|behind the scenes|interview|" +
            "non[- ]?stop|jukebox|playlist|compilation|full album|\\d+ ?(hours?|hrs))\\b"
    )

    fun isNonSong(song: Song): Boolean {
        val t = song.title.lowercase(Locale.ROOT)
        if (song.durationSec >= 900L) return true
        return words.containsMatchIn(t)
    }
}
