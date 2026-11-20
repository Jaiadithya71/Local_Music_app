package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song

/** Refill each depleted category, not only the pooled Explore row. */
object ShelfRefillPolicy {
    fun needsRefill(pool: List<Song>, known: Set<String>, musicOnly: Boolean): Boolean =
        DiscoveryPolicy.fresh(pool, known, limit = 5, musicOnly = musicOnly).size < 5

    fun queries(query: String, round: Int): List<String> {
        val variants = listOf("new releases", "indie", "melody", "latest singles")
        val offset = Math.floorMod(round, variants.size)
        return (0..1).map { "$query ${variants[(offset + it) % variants.size]}" }
    }

    fun merge(pool: List<Song>, additions: List<Song>): List<Song> =
        (pool + additions).distinctBy { it.id }
}
