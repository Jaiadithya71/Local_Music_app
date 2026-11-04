package com.contentagent.vibestream.data.recommendation

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException

/** Device-only 24h candidate cache. No library, history or profile upload. */
class RelatedSongCache(context: Context) {
    private val prefs = context.getSharedPreferences("vibestream_radio_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    suspend fun get(seed: String, fetch: suspend (String) -> List<Song>, now: Long = System.currentTimeMillis()): List<Song> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cached = try { gson.fromJson<List<Song>>(prefs.getString(seed, null), object : TypeToken<List<Song>>() {}.type) ?: emptyList() } catch (_: Exception) { emptyList() }
        if (cached.isNotEmpty() && now - prefs.getLong("t:$seed", 0) in 0 until 86_400_000L) return@withContext cached
        val result = try { fetch(seed) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        if (result.isEmpty()) return@withContext cached // stale candidates remain subject to the live known-song filter
        val clean = result.map { it.copy(streamUrl = null, localFilePath = null) }.take(50)
        val edit = prefs.edit()
        val keys = prefs.all.keys.filter { !it.startsWith("t:") && it != seed }.sortedByDescending { prefs.getLong("t:$it", 0) }
        keys.drop(63).forEach { edit.remove(it).remove("t:$it") }
        edit.putString(seed, gson.toJson(clean)).putLong("t:$seed", now).apply()
        return@withContext clean
    }
}
