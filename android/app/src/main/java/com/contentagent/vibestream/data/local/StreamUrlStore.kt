package com.contentagent.vibestream.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Persists resolved YouTube audio stream URLs with their real expiry, so a song
 * that was resolved once (by playback, by the next-track prefetch, or by the
 * visible-songs warmer) does not pay the extraction round trip again while the
 * URL is still valid. YouTube's signed URLs expire (~6h), so entries are keyed
 * by video id and carry the expiry parsed from the URL itself.
 */
class StreamUrlStore(context: Context) {

    private val prefs = context.getSharedPreferences("vibestream_streams", Context.MODE_PRIVATE)
    private val gson = Gson()

    data class Resolved(
        val url: String,
        val alternates: List<String>,
        val expiresAtMs: Long
    )

    /** The fresh URL and fallbacks for this video, or null when missing/stale. */
    @Synchronized
    fun get(videoId: String, nowMs: Long = System.currentTimeMillis()): Resolved? {
        val json = prefs.getString(videoId, null) ?: return null
        val o = try { JsonParser.parseString(json).asJsonObject } catch (e: Exception) { return null }
        val url = o.get("u")?.asString ?: return null
        val exp = o.get("e")?.asLong ?: return null
        if (!isFreshExpiry(exp, nowMs)) return null
        val alts = o.getAsJsonArray("a")?.mapNotNull { it?.asString } ?: emptyList()
        return Resolved(url, alts, exp)
    }

    /** Stores the best URL first, up to 4 alternates, with the earliest expiry seen. */
    @Synchronized
    fun put(videoId: String, candidates: List<String>, nowMs: Long = System.currentTimeMillis()) {
        if (candidates.isEmpty()) return
        val entry = JsonObject().apply {
            addProperty("u", candidates.first())
            add("a", com.google.gson.JsonArray().apply {
                candidates.drop(1).take(4).forEach { add(it) }
            })
            addProperty("e", expiryMsOf(candidates.first(), nowMs))
            addProperty("t", nowMs)
        }
        val edit = prefs.edit().putString(videoId, entry.toString())
        // Bound the store: evict the oldest resolutions beyond the cap.
        val keys = prefs.all.keys.toList()
        if (keys.size > MAX_ENTRIES) {
            val oldest = keys.mapNotNull { k ->
                val t = try {
                    JsonParser.parseString(prefs.getString(k, null)).asJsonObject.get("t")?.asLong
                } catch (e: Exception) { null }
                if (t != null) k to t else null
            }.sortedBy { it.second }.take(keys.size - MAX_ENTRIES)
            oldest.forEach { edit.remove(it.first) }
        }
        edit.apply()
    }

    @Synchronized
    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val MAX_ENTRIES = 200
        // Refresh this far ahead of the real expiry: a song started just before
        // the deadline must not die mid-play, and a paused-then-resumed stream
        // needs slack too.
        const val SAFETY_MARGIN_MS = 10 * 60_000L
        // Used only when a URL carries no expire parameter (non-YouTube sources).
        const val FALLBACK_TTL_MS = 5 * 60 * 60_000L + 30 * 60_000L

        private val EXPIRE_RE = Regex("[?&]expire=(\\d+)")

        /**
         * Absolute expiry (ms) for a stream URL. googlevideo URLs carry
         * `expire=<epoch seconds>`; anything else gets a conservative TTL from now.
         */
        fun expiryMsOf(url: String, nowMs: Long): Long {
            val epochSec = EXPIRE_RE.find(url)?.groupValues?.get(1)?.toLongOrNull()
            return if (epochSec != null) epochSec * 1000L else nowMs + FALLBACK_TTL_MS
        }

        fun isFreshExpiry(expiresAtMs: Long, nowMs: Long): Boolean =
            nowMs < expiresAtMs - SAFETY_MARGIN_MS

        /**
         * Whether an already-held URL is still safe to play. A URL with no
         * parseable expiry is treated as stale: its age is unknowable.
         */
        fun isFreshUrl(url: String?, nowMs: Long): Boolean {
            if (url.isNullOrEmpty()) return false
            val epochSec = EXPIRE_RE.find(url)?.groupValues?.get(1)?.toLongOrNull()
                ?: return false
            return isFreshExpiry(epochSec * 1000L, nowMs)
        }
    }
}
