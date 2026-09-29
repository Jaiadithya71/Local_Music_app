package com.contentagent.vibestream.data.local

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Manages locally saved/downloaded songs on the Android device.
 * Persists offline music library without any remote database or server.
 */
class OfflineStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("vibestream_offline_db", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    init {
        loadDownloadedSongs()
    }

    fun loadDownloadedSongs(): List<Song> {
        val json = prefs.getString("saved_songs", null) ?: return emptyList()
        val type = object : TypeToken<List<Song>>() {}.type
        val songs: List<Song> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Verify the file still exists on disk
        val validSongs = songs.filter { song ->
            song.localFilePath?.let { File(it).exists() } == true
        }

        _downloadedSongs.value = validSongs
        return validSongs
    }

    fun saveSong(song: Song) {
        val current = _downloadedSongs.value.toMutableList()
        val index = current.indexOfFirst { it.id == song.id }
        val updatedSong = song.copy(isDownloaded = true, isDownloading = false, downloadProgress = 1f)

        if (index >= 0) {
            current[index] = updatedSong
        } else {
            current.add(0, updatedSong)
        }

        persistList(current)
    }

    fun removeSong(videoId: String): Boolean {
        val current = _downloadedSongs.value.toMutableList()
        val song = current.find { it.id == videoId }
        var deletedFile = false

        if (song?.localFilePath != null) {
            val file = File(song.localFilePath!!)
            if (file.exists()) {
                deletedFile = file.delete()
            }
        }

        current.removeAll { it.id == videoId }
        persistList(current)
        return deletedFile
    }

    fun isDownloaded(videoId: String): Boolean {
        return _downloadedSongs.value.any { it.id == videoId && it.localFilePath?.let { p -> File(p).exists() } == true }
    }

    fun getLocalSong(videoId: String): Song? {
        return _downloadedSongs.value.find { it.id == videoId }
    }

    private fun persistList(list: List<Song>) {
        _downloadedSongs.value = list
        val json = gson.toJson(list)
        prefs.edit().putString("saved_songs", json).apply()
    }
}
