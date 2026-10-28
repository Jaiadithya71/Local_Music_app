package com.contentagent.vibestream.data.local

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Playlist(val name: String, val songs: List<Song> = emptyList())

/** Liked songs, recently played and playlists, stored on the device only. */
class LibraryStore(context: Context) {
    @PublishedApi internal val prefs = context.getSharedPreferences("vibestream_library", Context.MODE_PRIVATE)
    @PublishedApi internal val gson = Gson()

    private val _liked = MutableStateFlow<List<Song>>(load<Song>("liked"))
    val liked: StateFlow<List<Song>> = _liked.asStateFlow()

    private val _recent = MutableStateFlow<List<Song>>(load<Song>("recent"))
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(load<Playlist>("playlists"))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // Local-only compact ledger survives the 30-item Recent window. Old discarded
    // history cannot be recovered; migrate only the records actually retained.
    private val _playedKeys = MutableStateFlow(
        (prefs.getStringSet("played_keys_v1", emptySet()) ?: emptySet()).toSet() +
            _recent.value.flatMap { com.contentagent.vibestream.data.recommendation.DiscoveryPolicy.keys(it) })
    val playedKeys: StateFlow<Set<String>> = _playedKeys.asStateFlow()

    private fun rememberPlayed(songs: List<Song>) {
        val keys = _playedKeys.value + (prefs.getStringSet("played_keys_v1", emptySet()) ?: emptySet()) + songs.flatMap { com.contentagent.vibestream.data.recommendation.DiscoveryPolicy.keys(it) }
        _playedKeys.value = keys
        prefs.edit().putStringSet("played_keys_v1", keys).apply()
    }

    init { rememberPlayed(_recent.value) }

    /** Also called by the service while Home is not composed. */
    fun recordPlayed(song: Song) { rememberPlayed(listOf(song)) }

    // Negative taste signal: how often an artist's song was skipped early.
    private val _artistSkips = MutableStateFlow(loadSkips())
    val artistSkips: StateFlow<Map<String, Int>> = _artistSkips.asStateFlow()

    private fun loadSkips(): Map<String, Int> {
        val json = prefs.getString("artist_skips", null) ?: return emptyMap()
        return try {
            gson.fromJson<Map<String, Int>>(json, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    /** Count an early skip (user jumped to the next track soon after it started). */
    fun recordSkip(song: Song) {
        if (song.artist.isBlank() || song.artist == "Unknown Artist") return
        val map = _artistSkips.value + (song.artist to ((_artistSkips.value[song.artist] ?: 0) + 1))
        _artistSkips.value = map
        prefs.edit().putString("artist_skips", gson.toJson(map)).apply()
    }

    private inline fun <reified T> load(key: String): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            gson.fromJson<List<T>>(json, object : TypeToken<List<T>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun save(key: String, value: Any) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    private fun clean(s: Song) = s.copy(streamUrl = null, isDownloading = false, downloadProgress = 0f)

    fun isLiked(id: String) = _liked.value.any { it.id == id }

    fun toggleLike(song: Song) {
        val list = if (isLiked(song.id)) _liked.value.filter { it.id != song.id } else listOf(clean(song)) + _liked.value
        _liked.value = list
        save("liked", list)
    }

    fun addRecent(song: Song) {
        rememberPlayed(listOf(song))
        val list = (listOf(clean(song)) + _recent.value.filter { it.id != song.id }).take(30)
        _recent.value = list
        save("recent", list)
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || _playlists.value.any { it.name.equals(n, true) }) return
        val list = _playlists.value + Playlist(n)
        _playlists.value = list
        save("playlists", list)
    }

    fun addToPlaylist(name: String, song: Song) {
        val list = _playlists.value.map {
            if (it.name == name && it.songs.none { s -> s.id == song.id }) it.copy(songs = it.songs + clean(song)) else it
        }
        _playlists.value = list
        save("playlists", list)
    }

    fun removeFromPlaylist(name: String, songId: String) {
        val list = _playlists.value.map {
            if (it.name == name) it.copy(songs = it.songs.filter { s -> s.id != songId }) else it
        }
        _playlists.value = list
        save("playlists", list)
    }

    fun deletePlaylist(name: String) {
        val list = _playlists.value.filter { it.name != name }
        _playlists.value = list
        save("playlists", list)
    }

    /** Merge a backup file into the library: liked/recent dedupe by song id,
     *  playlists merge by name (songs deduped inside each). */
    fun mergeBackup(likedSongs: List<Song>, recentSongs: List<Song>, backupPlaylists: List<Playlist>) {
        if (likedSongs.isNotEmpty()) {
            val merged = _liked.value.toMutableList()
            for (s in likedSongs) if (merged.none { it.id == s.id }) merged.add(clean(s))
            _liked.value = merged
            save("liked", merged)
        }
        if (recentSongs.isNotEmpty()) {
            rememberPlayed(recentSongs)
            val merged = _recent.value.toMutableList()
            for (s in recentSongs) if (merged.none { it.id == s.id }) merged.add(clean(s))
            val trimmed = merged.take(30)
            _recent.value = trimmed
            save("recent", trimmed)
        }
        if (backupPlaylists.isNotEmpty()) {
            val merged = _playlists.value.toMutableList()
            for (pl in backupPlaylists) {
                val i = merged.indexOfFirst { it.name.equals(pl.name, true) }
                if (i >= 0) {
                    val existing = merged[i]
                    val songs = existing.songs.toMutableList()
                    for (s in pl.songs) if (songs.none { it.id == s.id }) songs.add(clean(s))
                    merged[i] = existing.copy(songs = songs)
                } else {
                    merged.add(Playlist(pl.name, pl.songs.map { clean(it) }))
                }
            }
            _playlists.value = merged
            save("playlists", merged)
        }
    }
}
