package com.contentagent.vibestream.data.model

/**
 * One browsable YouTube Music entry in sectioned search results:
 * an album, artist or playlist (songs and videos stay [Song]).
 * [id] is the browseId for albums/artists/playlists, the videoId otherwise.
 */
data class SearchEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val kind: String // "album" | "artist" | "playlist"
)

/** Search results grouped the way YouTube Music presents them. */
data class SearchSections(
    val topResult: Song?,
    val songs: List<Song>,
    val videos: List<Song>,
    val albums: List<SearchEntry>,
    val artists: List<SearchEntry>,
    val playlists: List<SearchEntry>
) {
    val isEmpty: Boolean
        get() = topResult == null && songs.isEmpty() && videos.isEmpty() &&
            albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    /** Flat playback context: every playable row in display order. */
    val playable: List<Song>
        get() = (listOfNotNull(topResult) + songs + videos).distinctBy { it.id }
}

/** An opened artist page. */
data class ArtistPage(
    val name: String,
    val thumbnailUrl: String,
    val topSongs: List<Song>,
    val albums: List<SearchEntry>
)
