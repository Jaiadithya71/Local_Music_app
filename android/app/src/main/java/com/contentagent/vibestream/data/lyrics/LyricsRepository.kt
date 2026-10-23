package com.contentagent.vibestream.data.lyrics

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Synced and plain lyrics from LRCLIB (https://lrclib.net) - free, no account,
 * no API key. Results (including "not found") are cached on the device so a
 * song only costs one network call ever.
 */
class LyricsRepository(context: Context) {

    data class LyricLine(val t: Long = 0L, val text: String = "")
    data class Lyrics(val synced: List<LyricLine>? = null, val plain: String? = null)

    private val prefs = context.getSharedPreferences("vibestream_lyrics", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun get(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        prefs.getString(song.id, null)?.let { cached ->
            return@withContext if (cached == "NONE") null
            else try { gson.fromJson(cached, Lyrics::class.java) } catch (e: Exception) { null }
        }
        val result = fetch(song)
        prefs.edit().putString(song.id, if (result == null) "NONE" else gson.toJson(result)).apply()
        result
    }

    private fun fetch(song: Song): Lyrics? {
        return try {
            val url = buildString {
                append("https://lrclib.net/api/get?track_name=")
                append(URLEncoder.encode(song.title.trim(), "UTF-8"))
                append("&artist_name=")
                append(URLEncoder.encode(song.artist.trim(), "UTF-8"))
                if (song.durationSec > 0) append("&duration=").append(song.durationSec)
            }
            val req = Request.Builder().url(url)
                .header("User-Agent", "VibeStream v1.0.1 (https://github.com/Jaiadithya71/Local_Music_app)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = gson.fromJson(body, JsonObject::class.java)
                val synced = if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull)
                    parseLrc(obj.get("syncedLyrics").asString) else emptyList()
                val plain = if (obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull)
                    obj.get("plainLyrics").asString else null
                if (synced.isEmpty() && plain.isNullOrBlank()) null
                else Lyrics(
                    synced = synced.takeIf { it.isNotEmpty() },
                    plain = plain?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Parses LRC lines like [03:12.50]text into timed entries. */
    internal fun parseLrc(lrc: String): List<LyricLine> {
        val rx = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lines()) {
            val m = rx.find(raw.trim()) ?: continue
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toDoubleOrNull() ?: continue
            out.add(LyricLine(((min * 60 + sec) * 1000).toLong(), m.groupValues[3].trim()))
        }
        return out.sortedBy { it.t }
    }
}
