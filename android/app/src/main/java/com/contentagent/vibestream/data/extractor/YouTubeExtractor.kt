package com.contentagent.vibestream.data.extractor

import com.contentagent.vibestream.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Autonomous On-Device YouTube Extractor.
 * Runs on the Android phone using public upstream services.
 * Searches tracks and resolves direct high-fidelity audio streams with public-mirror fallback.
 */
class YouTubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Resilient public API mirrors for decentralized on-device extraction
    private val apiMirrors = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://piped-api.lunar.icu",
        "https://api.piped.projectsegfau.lt"
    )

    /**
     * Searches YouTube for music tracks matching the given query.
     */
    suspend fun searchSongs(query: String, musicOnly: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        if (musicOnly) {
            // Music mode: songs only, no podcasts, compilations or long videos.
            val songs = searchYouTubeMusic(query)
            if (songs.isNotEmpty()) return@withContext songs
            return@withContext searchViaMirrors(query)
        }
        // Podcasts & more: same results as the YouTube app first (compilations,
        // long mixes, channels), then YouTube Music songs not already listed.
        val web = searchYouTubeWeb(query)
        val songs = searchYouTubeMusic(query)
        val merged = (web + songs).distinctBy { it.id }
        if (merged.isNotEmpty()) return@withContext merged
        searchViaMirrors(query)
    }

    /**
     * Primary search: YouTube Music's own search endpoint (songs filter).
     * Public Piped mirrors are frequently down, so this does not depend on them.
     */
    private fun searchYouTubeMusic(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            val payload = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "WEB_REMIX")
                        addProperty("clientVersion", "1.20241016.01.00")
                        addProperty("hl", "en")
                        addProperty("gl", "IN")
                    })
                })
                addProperty("query", query.trim())
                addProperty("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==")
            }
            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val body = client.newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            } ?: return emptyList()
            val out = mutableListOf<Song>()
            collectSongs(JsonParser.parseString(body), out)
            out.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Plain YouTube search (what the YouTube app shows). */
    private fun searchYouTubeWeb(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            val payload = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "WEB")
                        addProperty("clientVersion", "2.20241017.01.00")
                        addProperty("hl", "en")
                        addProperty("gl", "IN")
                    })
                })
                addProperty("query", query.trim())
            }
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val body = client.newCall(request).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            } ?: return emptyList()
            val out = mutableListOf<Song>()
            collectVideos(JsonParser.parseString(body), out)
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun collectVideos(el: com.google.gson.JsonElement, out: MutableList<Song>) {
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val v = o.getAsJsonObject("videoRenderer")
            if (v != null) {
                val id = v.get("videoId")?.asString
                val title = v.getAsJsonObject("title")?.getAsJsonArray("runs")
                    ?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
                if (id != null && !title.isNullOrBlank()) {
                    val artist = v.getAsJsonObject("ownerText")?.getAsJsonArray("runs")
                        ?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "YouTube"
                    val durText = v.getAsJsonObject("lengthText")?.get("simpleText")?.asString
                    val dur = try {
                        durText?.split(":")?.fold(0L) { acc, p -> acc * 60 + p.toLong() } ?: 0L
                    } catch (e: Exception) { 0L }
                    out.add(
                        Song(
                            id = id, title = title, artist = artist,
                            durationSec = dur, durationText = formatDuration(dur),
                            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                            kind = "video"
                        )
                    )
                }
            } else {
                for (e in o.entrySet()) collectVideos(e.value, out)
            }
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) collectVideos(e, out)
        }
    }

    private fun collectSongs(el: com.google.gson.JsonElement, out: MutableList<Song>) {
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val r = o.getAsJsonObject("musicResponsiveListItemRenderer")
            if (r != null) {
                parseMusicItem(r)?.let { out.add(it) }
            } else {
                for (e in o.entrySet()) collectSongs(e.value, out)
            }
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) collectSongs(e, out)
        }
    }

    private fun parseMusicItem(r: JsonObject): Song? {
        val id = r.getAsJsonObject("playlistItemData")?.get("videoId")?.asString ?: return null
        val cols = r.getAsJsonArray("flexColumns") ?: return null
        val texts = mutableListOf<List<String>>()
        for (c in cols) {
            val runs = c.asJsonObject.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")?.getAsJsonArray("runs")
            texts.add(runs?.mapNotNull { it.asJsonObject.get("text")?.asString } ?: emptyList())
        }
        val title = texts.getOrNull(0)?.firstOrNull() ?: return null
        val sub = texts.getOrNull(1) ?: emptyList()
        val artist = sub.firstOrNull { it.isNotBlank() && it.trim() != "•" } ?: "Unknown Artist"
        val durText = sub.lastOrNull { Regex("^\\d+(:\\d{2}){1,2}$").matches(it.trim()) }?.trim()
        val dur = durText?.split(":")?.fold(0L) { acc, p -> acc * 60 + p.toLong() } ?: 0L
        return Song(
            id = id,
            title = title,
            artist = artist,
            durationSec = dur,
            durationText = formatDuration(dur),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        )
    }

    private suspend fun searchViaMirrors(query: String): List<Song> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<Song>()

        for (baseMirror in apiMirrors) {
            try {
                val url = "$baseMirror/search?q=$encodedQuery&filter=music_songs"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body?.string() ?: return@use
                    val jsonElement = JsonParser.parseString(body)

                    val items: JsonArray = when {
                        jsonElement.isJsonArray -> jsonElement.asJsonArray
                        jsonElement.isJsonObject && jsonElement.asJsonObject.has("items") -> {
                            jsonElement.asJsonObject.getAsJsonArray("items")
                        }
                        else -> JsonArray()
                    }

                    for (item in items) {
                        if (!item.isJsonObject) continue
                        val obj = item.asJsonObject

                        val rawUrl = obj.get("url")?.asString ?: ""
                        val videoId = extractVideoId(rawUrl)
                        if (videoId.isEmpty()) continue

                        val title = obj.get("title")?.asString ?: "Unknown Title"
                        val artist = obj.get("uploaderName")?.asString ?: "Unknown Artist"
                        val rawThumbnail = obj.get("thumbnail")?.asString ?: ""
                        val thumbnail = if (rawThumbnail.isNotBlank()) rawThumbnail else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        val duration = obj.get("duration")?.asLong ?: 0L

                        results.add(
                            Song(
                                id = videoId,
                                title = title,
                                artist = artist,
                                durationSec = duration,
                                durationText = formatDuration(duration),
                                thumbnailUrl = thumbnail
                            )
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                // Try next mirror
                continue
            }
        }

        // If music filter produced nothing, try general video search as fallback
        if (results.isEmpty()) {
            return@withContext searchGeneralVideos(query)
        }

        return@withContext results
    }

    /**
     * Fallback search without the music_songs filter
     */
    private suspend fun searchGeneralVideos(query: String): List<Song> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<Song>()

        for (baseMirror in apiMirrors) {
            try {
                val url = "$baseMirror/search?q=$encodedQuery&filter=all"
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val jsonElement = JsonParser.parseString(body)
                    val items = if (jsonElement.isJsonArray) jsonElement.asJsonArray else return@use

                    for (item in items) {
                        if (!item.isJsonObject) continue
                        val obj = item.asJsonObject
                        val rawUrl = obj.get("url")?.asString ?: ""
                        val videoId = extractVideoId(rawUrl)
                        if (videoId.isEmpty()) continue

                        val rawThumb = obj.get("thumbnail")?.asString ?: ""
                        val thumbnail = if (rawThumb.isNotBlank()) rawThumb else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                        results.add(
                            Song(
                                id = videoId,
                                title = obj.get("title")?.asString ?: "Unknown Title",
                                artist = obj.get("uploaderName")?.asString ?: "Unknown Artist",
                                durationSec = obj.get("duration")?.asLong ?: 0L,
                                durationText = formatDuration(obj.get("duration")?.asLong ?: 0L),
                                thumbnailUrl = thumbnail
                            )
                        )
                    }
                }
                if (results.isNotEmpty()) return@withContext results
            } catch (e: Exception) {
                continue
            }
        }
        return@withContext results
    }

    /**
     * Resolves the direct audio stream URL on-device for streaming or downloading.
     * Selects the highest quality M4A or Opus audio stream.
     */
    suspend fun resolveAudioStream(videoId: String): String? =
        resolveAudioCandidates(videoId).firstOrNull()

    /**
     * Returns playable audio URLs, best first (m4a before webm, then bitrate), so the player
     * can fall back to the next one if a URL is rejected.
     */
    suspend fun resolveAudioCandidates(videoId: String): List<String> = withContext(Dispatchers.IO) {
        lastResolveError = null
        try {
            NewPipeBootstrap.ensureInit(client)
            val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
                "https://www.youtube.com/watch?v=$videoId"
            )
            val progressive = info.audioStreams.filter {
                it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.isUrl && !it.content.isNullOrEmpty()
            }
            val sorted = progressive.sortedWith(
                compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                    it.format == org.schabi.newpipe.extractor.MediaFormat.M4A
                }.thenByDescending { it.averageBitrate }
            ).map { it.content }
            if (sorted.isEmpty()) {
                lastResolveError = "no progressive audio streams (of ${info.audioStreams.size} total)"
            }
            sorted
        } catch (e: Throwable) {
            lastResolveError = "${e.javaClass.simpleName}: ${e.message}".take(160)
            emptyList()
        }
    }

    @Volatile var lastResolveError: String? = null

    /**
     * Real-time search recommendations / autocomplete queries directly from YouTube.
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.trim().isEmpty()) return@withContext emptyList<String>()
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://suggestqueries-clients6.youtube.com/complete/search?client=firefox&ds=yt&q=$encoded"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (body != null) {
                val jsonArray = JsonParser.parseString(body).asJsonArray
                if (jsonArray.size() > 1 && jsonArray.get(1).isJsonArray) {
                    val suggestionsArray = jsonArray.get(1).asJsonArray
                    val list = mutableListOf<String>()
                    for (elem in suggestionsArray) {
                        list.add(elem.asString)
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            // Fallback or ignore
        }
        return@withContext emptyList<String>()
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch?v=") -> url.substringAfter("/watch?v=").substringBefore("&")
            url.startsWith("/watch?v=") -> url.removePrefix("/watch?v=").substringBefore("&")
            url.startsWith("/") -> url.removePrefix("/")
            else -> url
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "Unknown"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val sec = seconds % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
        else "$m:${sec.toString().padStart(2, '0')}"
    }
}
