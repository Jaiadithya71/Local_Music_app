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
}
