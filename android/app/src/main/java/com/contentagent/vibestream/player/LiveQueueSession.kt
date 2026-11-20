package com.contentagent.vibestream.player

import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.data.recommendation.LiveQueuePolicy

/** Session boundary for asynchronous radio replies and deduplicated skip taps. */
internal class LiveQueueSession {
    var active = false; private set
    var epoch = 0L; private set
    val played = mutableSetOf<String>()
    val avoidedArtists = mutableSetOf<String>()
    private var lastSkippedId: String? = null

    fun begin(discovery: Boolean) {
        epoch++; active = discovery
        played.clear(); avoidedArtists.clear(); lastSkippedId = null
    }
    fun notePlaying(song: Song) {
        if (active) played.addAll(DiscoveryPolicy.keys(song))
        if (lastSkippedId != song.id) lastSkippedId = null
    }
    fun acceptEarlySkip(song: Song, positionMs: Long): Boolean {
        if (positionMs !in 1..45_000 || lastSkippedId == song.id) return false
        lastSkippedId = song.id
        if (active && song.artist.isNotBlank() && song.artist != "Unknown Artist") avoidedArtists.add(song.artist)
        return true
    }
    fun revise(
        requestEpoch: Long, queue: List<Song>, index: Int, candidates: List<Song>, feedback: LiveQueuePolicy.Feedback,
        known: Set<String>, languages: List<String>, moods: List<String>
    ): List<Song> {
        if (!active || requestEpoch != epoch) return queue
        return LiveQueuePolicy.update(queue,index,candidates,feedback,known+played,avoidedArtists,languages,moods)
    }
}
