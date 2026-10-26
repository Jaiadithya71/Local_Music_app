package com.contentagent.vibestream.data.local

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the play queue, position and playback modes so a listening session
 * survives the app being killed. Stream URLs are deliberately dropped: they
 * expire, so they are re-resolved on play.
 */
class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("vibestream_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    data class SavedQueue(
        val songs: List<Song> = emptyList(),
        val index: Int = -1,
        val positionMs: Long = 0L,
        val shuffle: Boolean = false,
        val repeat: Int = 0
    )

    fun save(songs: List<Song>, index: Int, positionMs: Long, shuffle: Boolean, repeat: Int) {
        val clean = songs.map { it.copy(chapters = if(it.automaticChapters) null else it.chapters, automaticChapters = false, streamUrl = null, isDownloading = false, downloadProgress = 0f) }
        prefs.edit()
            .putString("songs", gson.toJson(clean))
            .putInt("index", index)
            .putLong("position_ms", positionMs)
            .putBoolean("shuffle", shuffle)
            .putInt("repeat", repeat)
            .apply()
    }

    fun savePosition(positionMs: Long) {
        prefs.edit().putLong("position_ms", positionMs).apply()
    }

    fun load(): SavedQueue? {
        val json = prefs.getString("songs", null) ?: return null
        val songs: List<Song> = try {
            gson.fromJson(json, object : TypeToken<List<Song>>() {}.type) ?: return null
        } catch (e: Exception) { return null }
        if (songs.isEmpty()) return null
        return SavedQueue(
            songs = songs,
            index = prefs.getInt("index", -1).coerceIn(-1, songs.size - 1),
            positionMs = prefs.getLong("position_ms", 0L),
            shuffle = prefs.getBoolean("shuffle", false),
            repeat = prefs.getInt("repeat", 0)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
