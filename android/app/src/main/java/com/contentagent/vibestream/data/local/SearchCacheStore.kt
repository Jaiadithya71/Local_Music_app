package com.contentagent.vibestream.data.local

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Locale

/**
 * Persistent search-result cache. A repeated or revisited search renders from
 * the last known results instantly instead of waiting on the network again.
 * Entries expire after [TTL_MS]; at most [MAX_ENTRIES] are kept (oldest evicted).
 */
class SearchCacheStore(context: Context) {

    private val prefs = context.getSharedPreferences("vibestream_search_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun get(query: String, musicOnly: Boolean, now: Long = System.currentTimeMillis()): List<Song>? {
        val raw = prefs.getString(key(query, musicOnly), null) ?: return null
        return try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val at = obj.get("t").asLong
            if (now - at > TTL_MS) return null
            val arr = obj.getAsJsonArray("songs")
            arr.map { gson.fromJson(it, Song::class.java) }
        } catch (e: Exception) {
            null
        }
    }

    /** Disposable catalog results only; never touches the user's library or history. */
    fun clear() { prefs.edit().clear().commit() }

    fun put(query: String, musicOnly: Boolean, songs: List<Song>) {
        if (songs.isEmpty()) return
        try {
            // Never persist resolved stream URLs: they expire and the player
            // re-resolves or reuses the stream URL store instead.
            val clean = songs.take(MAX_SONGS).map { it.copy(streamUrl = null, isDownloading = false, downloadProgress = 0f) }
            val obj = JsonObject()
            obj.addProperty("t", System.currentTimeMillis())
            obj.add("songs", gson.toJsonTree(clean))
            val editor = prefs.edit().putString(key(query, musicOnly), obj.toString())
            evictIfNeeded(editor)
            editor.apply()
        } catch (e: Exception) {
            // Best effort: caching must never break a search.
        }
    }

    private fun evictIfNeeded(editor: android.content.SharedPreferences.Editor) {
        val all = prefs.all
        if (all.size < MAX_ENTRIES) return
        val aged = all.mapNotNull { (k, v) ->
            val t = try {
                JsonParser.parseString(v as String).asJsonObject.get("t").asLong
            } catch (e: Exception) {
                0L
            }
            k to t
        }.sortedBy { it.second }
        for ((k, _) in aged.take(all.size - MAX_ENTRIES + 1)) editor.remove(k)
    }

    private fun key(query: String, musicOnly: Boolean): String {
        val norm = query.trim().lowercase(Locale.ROOT) + "|" + (if (musicOnly) "M" else "A")
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
        return "s|" + digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val TTL_MS = 24L * 60 * 60 * 1000
        private const val MAX_ENTRIES = 100
        private const val MAX_SONGS = 40
    }
}
