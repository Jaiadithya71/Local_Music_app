package com.contentagent.vibestream.data.extractor

import com.contentagent.vibestream.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Autonomous On-Device YouTube Extractor.
 * Runs on the Android phone using public upstream services.
 * Searches tracks and resolves available audio streams. Source quality is not guaranteed.
 */
class YouTubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ---- Signed-in YouTube Music session (optional, OAuth device flow) ----

    /** Supplies a fresh OAuth access token, or null when signed out. Wired from MainActivity to YtMusicAuth. */
    @Volatile var authTokenProvider: (() -> String?)? = null

    /** Called when the server rejects the token, so the cached token can be dropped and refreshed. */
    @Volatile var onAuthRejected: (() -> Unit)? = null

    /** True after the server rejects our session (expired/revoked token); UI can prompt sign-in again. */
    @Volatile var authRejected: Boolean = false

    /** Attaches signed-in headers when a session exists. Returns true when auth was attached. */
    private fun attachAuth(builder: Request.Builder): Boolean {
        val token = authTokenProvider?.invoke()?.takeIf { it.isNotBlank() } ?: return false
        builder.header("Authorization", "Bearer $token")
        builder.header("X-Goog-AuthUser", "0")
        builder.header("X-Origin", "https://music.youtube.com")
        builder.header("Referer", "https://music.youtube.com/")
        return true
    }

    /**
     * Executes an InnerTube POST. On 401/403 with auth attached, flags the session
     * as rejected and retries once anonymously so basic search keeps working.
     */
    private fun execOrNull(request: Request, authed: Boolean): String? {
        return try {
            client.newCall(request).execute().use { r ->
                if (r.isSuccessful) {
                    r.body?.string()
                } else if ((r.code == 401 || r.code == 403) && authed) {
                    authRejected = true
                    onAuthRejected?.invoke()
                    val anon = request.newBuilder()
                        .removeHeader("Authorization")
                        .removeHeader("X-Goog-AuthUser")
                        .build()
                    client.newCall(anon).execute().use { r2 ->
                        if (r2.isSuccessful) r2.body?.string() else null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Sectioned search (YouTube Music layout) ----

    private val sectionsCache = object : LinkedHashMap<String, Pair<Long, com.contentagent.vibestream.data.model.SearchSections>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, com.contentagent.vibestream.data.model.SearchSections>>?) = size > 16
    }

    /**
     * Search results grouped like YouTube Music: top result, songs, videos,
     * albums, artists, playlists. Runs the catalog search and the typed general
     * search in parallel; 30-minute session cache per query.
     */
    suspend fun searchSectioned(query: String, musicOnly: Boolean = true): com.contentagent.vibestream.data.model.SearchSections = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext com.contentagent.vibestream.data.model.SearchSections(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val key = query.trim().lowercase(java.util.Locale.ROOT) + "|sec|" + musicOnly
        val now = System.currentTimeMillis()
        sectionsCache[key]?.let { if (now - it.first < 30 * 60 * 1000L) return@withContext it.second }
        val sections = coroutineScope {
            val generalDeferred = async { searchYouTubeMusicGeneral(query) }
            val songsDeferred = async { searchSongs(query, musicOnly) }
            val general = generalDeferred.await()
            val songs = songsDeferred.await()
            buildSections(general, songs, musicOnly)
        }
        if (!sections.isEmpty) sectionsCache[key] = now to sections
        sections
    }

    private fun buildSections(
        general: GeneralSearchParse,
        songs: List<Song>,
        musicOnly: Boolean
    ): com.contentagent.vibestream.data.model.SearchSections {
        val policy = com.contentagent.vibestream.data.recommendation.MusicSearchPolicy
        // searchSongs may include augmented YouTube uploads (kind=video) when the
        // catalog misses the query; those belong in the Videos section.
        val songItems = (if (musicOnly) songs else policy.songsOnly(general.songs).ifEmpty { songs }).filter { it.kind != "video" }
        val videos = (songs.filter { it.kind == "video" } + policy.musicVideosOnly(general.videos)).distinctBy { it.id }
        return com.contentagent.vibestream.data.model.SearchSections(
            topResult = general.topResult,
            songs = songItems,
            videos = videos,
            albums = general.albums,
            artists = general.artists,
            playlists = general.playlists
        )
    }

    private class GeneralSearchParse {
        var topResult: Song? = null
        val songs = mutableListOf<Song>()
        val videos = mutableListOf<Song>()
        val albums = mutableListOf<com.contentagent.vibestream.data.model.SearchEntry>()
        val artists = mutableListOf<com.contentagent.vibestream.data.model.SearchEntry>()
        val playlists = mutableListOf<com.contentagent.vibestream.data.model.SearchEntry>()
    }

    /** General YouTube Music search (no type filter): typed items for every section. */
    private fun searchYouTubeMusicGeneral(query: String): GeneralSearchParse {
        val out = GeneralSearchParse()
        if (query.isBlank()) return out
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
            }
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
            val authed = attachAuth(builder)
            val request = builder
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val body = execOrNull(request, authed) ?: return out
            parseGeneralSearch(JsonParser.parseString(body), out)
            out
        } catch (e: Exception) {
            out
        }
    }

    private fun runsText(o: JsonObject?, field: String): String =
        o?.getAsJsonObject(field)?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""

    private fun bestThumbnail(container: JsonObject?, vararg path: String): String {
        var node = container
        for (p in path) node = node?.getAsJsonObject(p) ?: return ""
        val arr = node?.getAsJsonArray("thumbnails") ?: return ""
        return arr.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""
    }

    private fun parseGeneralSearch(el: com.google.gson.JsonElement, out: GeneralSearchParse) {
        if (el.isJsonArray) {
            for (e in el.asJsonArray) parseGeneralSearch(e, out)
            return
        }
        if (!el.isJsonObject) return
        val o = el.asJsonObject
        val card = o.getAsJsonObject("musicCardShelfRenderer")
        if (card != null) {
            val vid = card.getAsJsonObject("onTap")?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
            val title = runsText(card, "title")
            if (vid != null && title.isNotBlank() && out.topResult == null) {
                val sub = runsText(card, "subtitle")
                val parts = sub.split("•").map { it.trim() }
                val artist = parts.getOrNull(1) ?: ""
                val durText = parts.lastOrNull { Regex("^\\d+(:\\d{2}){1,2}$").matches(it) } ?: ""
                val secs = durText.split(":").fold(0L) { n, v -> n * 60 + (v.toLongOrNull() ?: 0) }
                out.topResult = Song(
                    id = vid, title = title,
                    artist = artist.ifBlank { sub },
                    durationSec = secs, durationText = durText.ifBlank { formatDuration(secs) },
                    thumbnailUrl = bestThumbnail(card, "thumbnail", "musicThumbnailRenderer", "thumbnail")
                        .ifEmpty { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }
                )
            }
        }
        val r = o.getAsJsonObject("musicResponsiveListItemRenderer")
        if (r != null) {
            classifyGeneralItem(r, out)
        } else {
            for (e in o.entrySet()) parseGeneralSearch(e.value, out)
        }
    }

    private fun classifyGeneralItem(r: JsonObject, out: GeneralSearchParse) {
        val cols = r.getAsJsonArray("flexColumns") ?: return
        fun colText(i: Int): String {
            val col = if (i < cols.size()) cols.get(i).asJsonObject else null
            val runs = col?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")?.getAsJsonArray("runs") ?: return ""
            return runs.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" }
        }
        val title = colText(0)
        if (title.isBlank()) return
        val sub = colText(1)
        val type = sub.substringBefore("•").trim()
        val rest = sub.substringAfter("•", "").trim()
        val thumb = bestThumbnail(r, "thumbnail", "musicThumbnailRenderer", "thumbnail")
        val vid = r.getAsJsonObject("playlistItemData")?.get("videoId")?.asString
            ?: r.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString
        val browse = r.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
        when (type.lowercase(java.util.Locale.ROOT)) {
            "song" -> if (vid != null) out.songs.add(Song(vid, title, rest.ifBlank { "Unknown Artist" }, thumbnailUrl = thumb.ifEmpty { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }))
            "video" -> if (vid != null) out.videos.add(Song(vid, title, rest.ifBlank { "YouTube" }, thumbnailUrl = thumb.ifEmpty { "https://i.ytimg.com/vi/$vid/hqdefault.jpg" }, kind = "video"))
            "album", "ep", "single" -> if (browse != null) out.albums.add(com.contentagent.vibestream.data.model.SearchEntry(browse, title, sub, thumb, "album"))
            "artist" -> if (browse != null) out.artists.add(com.contentagent.vibestream.data.model.SearchEntry(browse, title, sub, thumb, "artist"))
            "playlist" -> if (browse != null) out.playlists.add(com.contentagent.vibestream.data.model.SearchEntry(browse, title, sub, thumb, "playlist"))
        }
    }

    /** Album page: track list for an album browseId (MPREb...). */
    suspend fun browseAlbumTracks(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        val body = browse(browseId) ?: return@withContext emptyList()
        val out = mutableListOf<Song>()
        collectShelfSongs(JsonParser.parseString(body), out)
        out.distinctBy { it.id }
    }

    /** Artist page: name, photo, top songs and album shelf for a channel browseId (UC...). */
    suspend fun browseArtist(browseId: String): com.contentagent.vibestream.data.model.ArtistPage? = withContext(Dispatchers.IO) {
        val body = browse(browseId) ?: return@withContext null
        val root = JsonParser.parseString(body)
        var name = ""
        var photo = ""
        val topSongs = mutableListOf<Song>()
        val albums = mutableListOf<com.contentagent.vibestream.data.model.SearchEntry>()
        findArtistHeader(root)?.let { h ->
            name = runsText(h, "title")
            photo = bestThumbnail(h, "thumbnail", "musicThumbnailRenderer", "thumbnail")
        }
        collectArtistShelves(root, topSongs, albums)
        if (name.isBlank() && topSongs.isEmpty()) null
        else com.contentagent.vibestream.data.model.ArtistPage(name, photo, topSongs.distinctBy { it.id }, albums.distinctBy { it.id })
    }

    private fun browse(browseId: String): String? {
        if (!Regex("[A-Za-z0-9_-]+").matches(browseId)) return null
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
                addProperty("browseId", browseId)
            }
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
            val authed = attachAuth(builder)
            val request = builder
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            execOrNull(request, authed)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The signed-in user's YouTube Music library playlists (Liked songs, created and
     * saved playlists). Requires sign-in; returns an empty list anonymously.
     */
    suspend fun libraryPlaylists(): List<com.contentagent.vibestream.data.model.SearchEntry> = withContext(Dispatchers.IO) {
        val body = browse("FEmusic_liked_playlists") ?: return@withContext emptyList()
        val out = mutableListOf<com.contentagent.vibestream.data.model.SearchEntry>()
        collectTwoRowItems(JsonParser.parseString(body), out)
        out.distinctBy { it.id }
    }

    private fun collectTwoRowItems(el: com.google.gson.JsonElement, out: MutableList<com.contentagent.vibestream.data.model.SearchEntry>) {
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val r = o.getAsJsonObject("musicTwoRowItemRenderer")
            if (r != null) {
                val id = r.getAsJsonObject("navigationEndpoint")
                    ?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                val title = r.getAsJsonObject("title")?.getAsJsonArray("runs")
                    ?.firstOrNull()?.asJsonObject?.get("text")?.asString
                if (!id.isNullOrBlank() && id.startsWith("VL") && !title.isNullOrBlank()) {
                    val sub = r.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
                        ?.mapNotNull { it.asJsonObject?.get("text")?.asString }?.joinToString("") ?: ""
                    val thumb = bestThumbnail(r, "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail")
                    out.add(com.contentagent.vibestream.data.model.SearchEntry(id, title, sub, thumb, "playlist"))
                }
            } else {
                for (e in o.entrySet()) collectTwoRowItems(e.value, out)
            }
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) collectTwoRowItems(e, out)
        }
    }

    private fun findArtistHeader(el: com.google.gson.JsonElement): JsonObject? {
        if (el.isJsonObject) {
            val h = el.asJsonObject.getAsJsonObject("musicImmersiveHeaderRenderer")
                ?: el.asJsonObject.getAsJsonObject("musicVisualHeaderRenderer")
            if (h != null) return h
            for (e in el.asJsonObject.entrySet()) findArtistHeader(e.value)?.let { return it }
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) findArtistHeader(e)?.let { return it }
        }
        return null
    }

    private fun collectShelfSongs(el: com.google.gson.JsonElement, out: MutableList<Song>) {
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val r = o.getAsJsonObject("musicResponsiveListItemRenderer")
            if (r != null) {
                parseMusicItem(r)?.let { out.add(it) }
            } else {
                for (e in o.entrySet()) collectShelfSongs(e.value, out)
            }
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) collectShelfSongs(e, out)
        }
    }

    private fun collectArtistShelves(el: com.google.gson.JsonElement, topSongs: MutableList<Song>, albums: MutableList<com.contentagent.vibestream.data.model.SearchEntry>) {
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val shelf = o.getAsJsonObject("musicShelfRenderer")
            if (shelf != null) {
                val title = runsText(shelf, "title")
                if (title.contains("song", ignoreCase = true)) {
                    val items = shelf.getAsJsonArray("contents")
                    items?.forEach { it.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer")?.let { r -> parseMusicItem(r)?.let(topSongs::add) } }
                }
                return
            }
            val carousel = o.getAsJsonObject("musicCarouselShelfRenderer")
            if (carousel != null) {
                carousel.getAsJsonArray("contents")?.forEach { item ->
                    val mtr = item.asJsonObject.getAsJsonObject("musicTwoRowItemRenderer") ?: return@forEach
                    val browse = mtr.getAsJsonObject("navigationEndpoint")?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString ?: return@forEach
                    // Albums/EPs/singles carry an MPREb browse id; playlists and channels do not.
                    if (!browse.startsWith("MPREb")) return@forEach
                    val t = runsText(mtr, "title")
                    if (t.isNotBlank()) albums.add(com.contentagent.vibestream.data.model.SearchEntry(
                        browse, t, runsText(mtr, "subtitle"),
                        bestThumbnail(mtr, "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail"), "album"))
                }
                return
            }
            for (e in o.entrySet()) collectArtistShelves(e.value, topSongs, albums)
        } else if (el.isJsonArray) {
            for (e in el.asJsonArray) collectArtistShelves(e, topSongs, albums)
        }
    }

    private val apiMirrors = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://piped-api.lunar.icu",
        "https://api.piped.projectsegfau.lt"
    )

    // Optional persistent cache (wired from MainActivity). Session memory cache works regardless.
    var searchCache: com.contentagent.vibestream.data.local.SearchCacheStore? = null
    private val memCache = object : LinkedHashMap<String, Pair<Long, List<Song>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Song>>>?) = size > 32
    }
    private val memSuggestCache = object : LinkedHashMap<String, Pair<Long, List<String>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<String>>>?) = size > 32
    }

    /** Explicit refresh and sign-in changes must not reuse anonymous/stale pools. */
    fun clearSearchCaches() {
        memCache.clear()
        sectionsCache.clear()
        searchCache?.clear()
    }

    /**
     * Searches YouTube for music tracks matching the given query.
     * Answers from the session cache (30 min) or the disk cache (24 h) when fresh.
     */
    suspend fun searchSongs(query: String, musicOnly: Boolean = true): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val key = query.trim().lowercase(java.util.Locale.ROOT) + "|" + musicOnly
        val now = System.currentTimeMillis()
        memCache[key]?.let { if (now - it.first < 30 * 60 * 1000L) return@withContext it.second }
        searchCache?.get(query, musicOnly, now)?.let {
            memCache[key] = now to it
            return@withContext it
        }
        val fresh = searchSongsLive(query, musicOnly)
        if (fresh.isNotEmpty()) {
            memCache[key] = now to fresh
            searchCache?.put(query, musicOnly, fresh)
        }
        fresh
    }

    private suspend fun searchSongsLive(query: String, musicOnly: Boolean): List<Song> = withContext(Dispatchers.IO) {
        if (musicOnly) {
            // Music mode: songs only, no podcasts, compilations or long videos.
            val policy = com.contentagent.vibestream.data.recommendation.MusicSearchPolicy
            val songs = policy.songsOnly(searchYouTubeMusic(query))
            // YouTube Music's anonymous catalog misses some tracks (seen with regional film
            // songs). When no song covers the query words, add matching YouTube uploads as
            // video results so the track is still reachable; they show under All/Videos.
            val merged = if (policy.coversQuery(songs, query)) songs else {
                val vids = policy.musicVideosOnly(searchYouTubeWeb(query))
                // Uploads that cover every query word lead, then the catalog songs.
                val hits = vids.filter { policy.coversQuery(listOf(it), query) }
                (hits + songs + (vids - hits.toSet())).distinctBy { it.id }
            }
            if (merged.isNotEmpty()) return@withContext merged
            return@withContext policy.songsOnly(searchViaMirrors(query, musicOnly))
        }
        // Podcasts & more: same results as the YouTube app first (compilations,
        // long mixes, channels), then YouTube Music songs not already listed.
        val web = searchYouTubeWeb(query)
        val songs = searchYouTubeMusic(query)
        val merged = (web + songs).distinctBy { it.id }
        if (merged.isNotEmpty()) return@withContext merged
        searchViaMirrors(query, musicOnly)
    }

    /** Anonymous song radio, bounded separately from normal search/playback. */
    suspend fun relatedSongs(seedId: String): List<Song> = withContext(Dispatchers.IO) {
        if (!Regex("[A-Za-z0-9_-]{11}").matches(seedId)) return@withContext emptyList()
        try {
            val payload = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "WEB_REMIX")
                        addProperty("clientVersion", "1.20241016.01.00")
                        addProperty("hl", "en")
                        addProperty("gl", "IN")
                    })
                })
                addProperty("videoId", seedId)
                addProperty("playlistId", "RDAMVM$seedId")
                addProperty("isAudioOnly", true)
            }
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0")
                .header("Origin", "https://music.youtube.com")
            val authed = attachAuth(builder)
            val request = builder
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            val body = execOrNull(request, authed)
                ?: return@withContext emptyList()
            parseRadio(body).filter { it.id != seedId }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    internal fun parseRadio(body: String): List<Song> {
        val out = mutableListOf<Song>()
        fun text(o: com.google.gson.JsonObject?, field: String): String =
            o?.getAsJsonObject(field)?.getAsJsonArray("runs")?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
        fun visit(x: com.google.gson.JsonElement) {
            if (x.isJsonArray) x.asJsonArray.forEach { visit(it) }
            if (!x.isJsonObject) return
            val obj = x.asJsonObject
            val row = obj.getAsJsonObject("playlistPanelVideoRenderer")
            if (row != null) {
                val id = row.get("videoId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val title = text(row, "title")
                if (Regex("[A-Za-z0-9_-]{11}").matches(id) && title.isNotBlank()) {
                    val duration = text(row, "lengthText")
                    val seconds = duration.split(":").fold(0L) { n, v -> n * 60 + (v.toLongOrNull() ?: 0) }
                    out.add(Song(id, title, text(row, "longBylineText").ifBlank { text(row, "shortBylineText") }.substringBefore(" • "),
                        seconds, duration, "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                }
            } else obj.entrySet().forEach { visit(it.value) }
        }
        visit(JsonParser.parseString(body))
        return com.contentagent.vibestream.data.recommendation.MusicSearchPolicy.songsOnly(out).distinctBy { it.id }.take(50)
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
            val builder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
            val authed = attachAuth(builder)
            val request = builder
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val body = execOrNull(request, authed) ?: return emptyList()
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

    private suspend fun searchViaMirrors(query: String, musicOnly: Boolean): List<Song> = withContext(Dispatchers.IO) {
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
        // Do not silently turn Music mode into video search when a provider fails.
        if (results.isEmpty() && !musicOnly) {
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
                                kind = "video",
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
     * Returns available M4A/Opus candidates. Container preference is compatibility policy, not a fidelity guarantee.
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
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
        val sKey = query.trim().lowercase(java.util.Locale.ROOT)
        memSuggestCache[sKey]?.let { if (System.currentTimeMillis() - it.first < 10 * 60 * 1000L) return@withContext it.second }
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        // suggestqueries-clients6.youtube.com now answers 400; the google.com host
        // is the one that still returns the JSON [query, [suggestions...]] shape
        // (verified 2026-11-18). This endpoint is how NewPipe/YouTube suggest works.
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
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
                    if (list.isNotEmpty()) memSuggestCache[sKey] = System.currentTimeMillis() to list
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
