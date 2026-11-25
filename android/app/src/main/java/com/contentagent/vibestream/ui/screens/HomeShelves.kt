package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy
import com.contentagent.vibestream.ui.theme.*

private val shelves = listOf(
    "Top picks for you" to "trending songs",
    "Tamil hits" to "tamil hit songs",
    "Hindi hits" to "bollywood hit songs",
    "Chill and focus" to "lofi chill songs",
    "Workout" to "workout songs"
)

internal object ShelfCache {
    val data = mutableStateMapOf<String, List<Song>>()
    val failed = mutableStateMapOf<String, Boolean>()
    // Shelves where "Show more" found nothing new; the card says so instead of
    // looking dead.
    val depleted = mutableStateMapOf<String, Boolean>()
    // Full fetched result pool per shelf (the UI shows a ranked window of it),
    // ids already shown this session, a drift counter for refill queries, and a
    // loading flag for the "Show more" card.
    val pool = mutableMapOf<String, List<Song>>()
    val seen = mutableMapOf<String, MutableSet<String>>()
    val drift = mutableMapOf<String, Int>()
    val loadingMore = mutableStateMapOf<String, Boolean>()
    val counts = mutableStateMapOf<String, Pair<Int, Int>>()
    var restored = false
}

internal fun clearShelfMemory() {
    ShelfCache.data.clear(); ShelfCache.pool.clear(); ShelfCache.seen.clear(); ShelfCache.drift.clear()
    ShelfCache.failed.clear(); ShelfCache.loadingMore.clear(); ShelfCache.counts.clear(); ShelfCache.depleted.clear()
}

/** Liked artists float up, artists skipped twice or more sink. Stable within a group. */
internal fun rankForTaste(items: List<Song>, likedArtists: Set<String>, skippedArtists: Set<String>): List<Song> {
    return items.sortedBy {
        when {
            likedArtists.contains(it.artist) -> 0
            skippedArtists.contains(it.artist) -> 2
            else -> 1
        }
    }
}

internal fun shelfPrefs(ctx: android.content.Context) =
    ctx.getSharedPreferences("vibestream_shelves", android.content.Context.MODE_PRIVATE)

internal fun cacheKey(query: String, musicOnly: Boolean) = "discovery-v4|" + (if (musicOnly) "M|" else "A|") + query

private fun restoreShelves(ctx: android.content.Context, queries: List<String>, musicOnly: Boolean) {
    val gson = com.google.gson.Gson()
    val type = object : com.google.gson.reflect.TypeToken<List<Song>>() {}.type
    for (q in queries) {
        val k = cacheKey(q, musicOnly)
        if (ShelfCache.data[k] != null) continue
        val json = shelfPrefs(ctx).getString(k, null) ?: continue
        val list: List<Song>? = try { gson.fromJson(json, type) } catch (e: Exception) { null }
        if (list.isNullOrEmpty()) continue
        // Pools saved by older builds can hold only recordings the current policy
        // rejects (e.g. unvetted uploads from before they were allowed on Home).
        // Restoring them dead-ends the shelf on "No eligible songs" until the
        // 6-hour refresh; drop them so the shelf refetches instead.
        if (DiscoveryPolicy.fresh(list, emptySet(), limit = 1, musicOnly = musicOnly).isEmpty()) {
            shelfPrefs(ctx).edit().remove(k).remove("t:$k").apply()
            continue
        }
        ShelfCache.data[k] = list
    }
}

/** Fetches a shelf, keeps showing cached songs if the refresh fails. */
internal suspend fun loadShelf(ctx: android.content.Context, search: suspend (String, Boolean) -> List<Song>, query: String, musicOnly: Boolean, force: Boolean = false) {
    val k = cacheKey(query, musicOnly)
    ShelfCache.failed.remove(k)
    ShelfCache.depleted.remove(k)
    val now = System.currentTimeMillis()
    if (!force && ShelfCache.data[k]?.isNotEmpty() == true &&
        now - shelfPrefs(ctx).getLong("t:$k", 0) in 0 until 21_600_000L) return
    var result: List<Song> = emptyList()
    for (attempt in 1..2) {
        result = try {
            kotlinx.coroutines.withTimeout(20_000) { search(query, musicOnly) }
        } catch (e: Exception) { emptyList() }
        if (result.isNotEmpty()) break
    }
    if (result.isNotEmpty()) {
        val clean = result.map { it.copy(streamUrl = null) }
        ShelfCache.pool[k] = clean
        val seenSet = ShelfCache.seen.getOrPut(k) { mutableSetOf() }
        val window = rankForTaste(clean.filter { it.id !in seenSet }, likedArtistsProvider(), skippedArtistsProvider()).take(12)
        seenSet.addAll(window.map { it.id })
        ShelfCache.data[k] = clean
        val edit = shelfPrefs(ctx).edit()
        val oldKeys = shelfPrefs(ctx).all.keys.filter { it.startsWith("discovery-") && it != k }
            .sortedByDescending { shelfPrefs(ctx).getLong("t:$it", 0) }
        oldKeys.drop(63).forEach { edit.remove(it).remove("t:$it"); ShelfCache.data.remove(it); ShelfCache.pool.remove(it); ShelfCache.seen.remove(it); ShelfCache.drift.remove(it) }
        edit.putString(k, com.google.gson.Gson().toJson(clean)).putLong("t:$k", now).apply()
    } else {
        ShelfCache.failed[k] = true
    }
}

// Taste signals for ranking, read from the live library snapshot.
private var likedArtistsProvider: () -> Set<String> = { emptySet() }
private var skippedArtistsProvider: () -> Set<String> = { emptySet() }

/**
 * "Show more" on a shelf: append the next ranked window of unseen songs. When the
 * pool is exhausted, refetch with a drifting query (mix variant, then the top or
 * most-liked artist, then "new ...") so the shelf keeps serving fresh picks
 * instead of the same static list.
 */
internal suspend fun showMore(ctx: android.content.Context, search: suspend (String, Boolean) -> List<Song>, query: String, musicOnly: Boolean, driftArtist: String?) {
    val k = cacheKey(query, musicOnly)
    if (ShelfCache.loadingMore[k] == true) return
    ShelfCache.loadingMore[k] = true
    try {
        val dataIds = (ShelfCache.data[k] ?: emptyList()).map { it.id }.toHashSet()
        val seenSet = ShelfCache.seen.getOrPut(k) { dataIds.toMutableSet() }
        // Only songs not already on the shelf; the shelf renders the full pool,
        // so appending pool members again was a silent no-op.
        var more = (ShelfCache.pool[k] ?: emptyList()).filter { it.id !in seenSet && it.id !in dataIds }
        var attempts = 0
        val artist = driftArtist
            ?: ShelfCache.data[k]?.firstOrNull()?.artist?.takeIf { it.isNotBlank() && it != "Unknown Artist" && it != "YouTube" }
        while (more.isEmpty() && attempts < 4) {
            val d = ShelfCache.drift[k] ?: 0
            ShelfCache.drift[k] = d + 1
            val q2 = when (d % 4) {
                0 -> "$query mix"
                1 -> artist?.let { "$it songs" } ?: "new $query"
                2 -> "new $query"
                else -> "$query latest"
            }
            val result: List<Song> = try {
                kotlinx.coroutines.withTimeout(20_000) { search(q2, musicOnly) }
            } catch (e: Exception) { emptyList() }
            val existing = (ShelfCache.pool[k] ?: emptyList()).map { it.id }.toHashSet() + dataIds
            val fresh = result.map { it.copy(streamUrl = null) }.filter { it.id !in seenSet && it.id !in existing }
            ShelfCache.pool[k] = (ShelfCache.pool[k] ?: emptyList()) + fresh
            more = fresh
            attempts++
        }
        val take = rankForTaste(more, likedArtistsProvider(), skippedArtistsProvider()).take(12)
        if (take.isNotEmpty()) {
            seenSet.addAll(take.map { it.id })
            ShelfCache.data[k] = (ShelfCache.data[k] ?: emptyList()) + take
            ShelfCache.depleted.remove(k)
        } else {
            ShelfCache.depleted[k] = true
        }
    } finally {
        ShelfCache.loadingMore.remove(k)
    }
}

/** Amazon Music style Home: titled horizontal shelves of square cards. */
@Composable
fun HomeShelves(
    extractor: YouTubeExtractor,
    search: suspend (String, Boolean) -> List<Song> = { q, m -> extractor.searchSongs(q, m) },
    related: suspend (String) -> List<Song> = { extractor.relatedSongs(it) },
    refreshCatalog: () -> Unit = { extractor.clearSearchCaches() },
    currentSongId: String?,
    onPlay: (Song, List<Song>) -> Unit,
    onSeeAll: (String, String) -> Unit,
    recent: List<Song> = emptyList(),
    playedKeys: Set<String> = emptySet(),
    liked: List<Song> = emptyList(),
    playlists: List<com.contentagent.vibestream.data.local.Playlist> = emptyList(),
    onPlayMood: (List<Song>) -> Unit = {},
    onEditTaste: () -> Unit = {},
    musicOnly: Boolean = true,
    onModeChange: (Boolean) -> Unit = {},
    skippedArtists: Set<String> = emptySet(),
    // Fires with the leading songs of the top shelf once shelves have loaded,
    // so the player can warm their streams before they are tapped.
    onSongsVisible: (List<Song>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Taste onboarding answers seed the shelves; defaults when skipped.
    val profile = remember { com.contentagent.vibestream.data.local.TasteStore(ctx).load() }
    // Taste = liked songs + everything in his playlists, per his instruction.
    val tasteArtists = (liked + playlists.flatMap { it.songs }).map { it.artist }.toSet()
    likedArtistsProvider = { tasteArtists }
    skippedArtistsProvider = { skippedArtists }
    val tasteShelves = profile.languages.take(2).map { "$it hits" to "$it hit songs" } +
        profile.moods.take(2).map { mood ->
            mood to "${profile.languages.firstOrNull()?.plus(" ") ?: ""}$mood songs"
        }
    val homeSeed = com.contentagent.vibestream.data.recommendation.HomeRecommendation.seed(
        liked, recent, profile.languages, profile.artists)
    val shelfList = remember(homeSeed, profile) {
        listOf(homeSeed.label to homeSeed.query) + tasteShelves.ifEmpty { shelves.drop(1) }
    }
    var discoveryRound by remember { mutableStateOf(shelfPrefs(ctx).getInt("discovery_round", 0) + 1) }
    var refreshing by remember { mutableStateOf(false) }
    var forceRefresh by remember { mutableStateOf(false) }
    var cacheDialog by remember { mutableStateOf(false) }
    var diagnosticsDialog by remember { mutableStateOf(false) }
    var cacheEpoch by remember { mutableStateOf(0) }
    val known = playedKeys + (recent + liked + playlists.flatMap { it.songs }).flatMap { DiscoveryPolicy.keys(it) }
    val seedQueries = DiscoveryPolicy.queries(profile.languages, profile.moods, profile.artists, discoveryRound)
    val seedSongs = seedQueries.flatMap { ShelfCache.data[cacheKey(it, musicOnly)] ?: emptyList() }
    val radioCache = remember { com.contentagent.vibestream.data.recommendation.RelatedSongCache(ctx) }
    var radioSongs by remember { mutableStateOf(emptyList<Song>()) }
    val radioSeeds = com.contentagent.vibestream.data.recommendation.SongSeedPolicy.seeds(liked, playlists.flatMap { it.songs }, discoveryRound)
    val keywordPool = shelfList.flatMap { ShelfCache.data[cacheKey(it.second, musicOnly)] ?: emptyList() } + seedSongs
    val discoveryPool = com.contentagent.vibestream.data.recommendation.SongSeedPolicy.blend(radioSongs, keywordPool, profile.languages)
    // "Your mood": the new-to-you pool ranked by taste first, so the mix leads
    // with artists he already loves instead of a flat shuffle of strangers.
    val moodMix = DiscoveryPolicy.fresh(rankForTaste(discoveryPool, tasteArtists, skippedArtists), known, skippedArtists, musicOnly = musicOnly)
    // Show cached shelves instantly, then refresh one at a time in the background.
    LaunchedEffect(shelfList, musicOnly, discoveryRound, radioSeeds.map { it.id }, cacheEpoch) {
        refreshing = true
        shelfPrefs(ctx).edit().putInt("discovery_round", discoveryRound).apply()
        // Radio (related-track) loading is fully independent of keyword shelves:
        // it runs as its own child and never holds the section in "Finding..."
        // or disables New picks. Last good radio picks stay until replaced.
        launch {
            val fetched = kotlinx.coroutines.coroutineScope {
                radioSeeds.map { seed -> async { radioCache.get(seed.id, related) } }.awaitAll()
            }.flatten().distinctBy { it.id }
            if (fetched.isNotEmpty()) radioSongs = fetched
        }
        try {
            restoreShelves(ctx, (seedQueries + shelfList.map { it.second }).distinct(), musicOnly)
            // Different taste-scoped queries refill the pool, not exact played titles.
            // Batches of three: one slow query must not serialize Home, but a
            // mobile-data connection is not flooded either.
            val queries = (seedQueries + shelfList.map { it.second }).distinct()
            for (batch in queries.chunked(3)) {
                kotlinx.coroutines.coroutineScope {
                    batch.map { q -> async { loadShelf(ctx, search, q, musicOnly, force = forceRefresh) } }.awaitAll()
                }
            }
            // Refill depleted categories individually, retaining strict no-replay rules.
            for (batch in shelfList.chunked(3)) {
                kotlinx.coroutines.coroutineScope {
                    batch.map { (_, q) -> async {
                        val key = cacheKey(q, musicOnly)
                        var pool = ShelfCache.data[key].orEmpty()
                        val policy = com.contentagent.vibestream.data.recommendation.ShelfRefillPolicy
                        if (policy.needsRefill(pool, known, musicOnly)) {
                            for (variant in policy.queries(q, discoveryRound)) {
                                loadShelf(ctx, search, variant, musicOnly, force = forceRefresh)
                                pool = policy.merge(pool, ShelfCache.data[cacheKey(variant, musicOnly)].orEmpty())
                                if (!policy.needsRefill(pool, known, musicOnly)) break
                            }
                            if (pool.isNotEmpty()) {
                                ShelfCache.pool[key] = pool
                                ShelfCache.data[key] = pool
                                shelfPrefs(ctx).edit().putString(key, com.google.gson.Gson().toJson(pool))
                                    .putLong("t:$key", System.currentTimeMillis()).apply()
                            }
                        }
                        val eligible = DiscoveryPolicy.fresh(pool, emptySet(), limit = pool.size, musicOnly = musicOnly).size
                        ShelfCache.counts[key] = pool.size to eligible
                        android.util.Log.d("VibeStreamShelves", "shelf=${q.hashCode()} fetched=${pool.size} eligible=$eligible knownKeys=${known.size} visible=${DiscoveryPolicy.fresh(pool, known, musicOnly = musicOnly).size} refresh=$forceRefresh")
                    } }.awaitAll()
                }
            }
        } finally { refreshing = false; forceRefresh = false }
        val topShelf = ShelfCache.data[cacheKey(shelfList.first().second, musicOnly)].orEmpty()
        if (topShelf.isNotEmpty()) onSongsVisible(topShelf.take(6))
    }
    if (diagnosticsDialog) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { diagnosticsDialog = false },
            title = { Text("Home diagnostics", modifier = Modifier.testTag("home-diagnostics-title")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Counts only. No song names or account details.")
                Text("History keys ${playedKeys.size} | library songs ${(liked + playlists.flatMap { it.songs }).distinctBy { it.id }.size} | loading $refreshing")
                shelfList.forEach { (title, query) ->
                    val pool = if (query == shelfList.first().second) discoveryPool else ShelfCache.data[cacheKey(query, musicOnly)].orEmpty()
                    Text(title + "\n" + DiscoveryPolicy.diagnostic(pool, known, musicOnly), fontSize = 12.sp)
                }
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { diagnosticsDialog = false }) { Text("Close") } })
    }
    if (cacheDialog) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { cacheDialog = false },
            title = { Text("Clear loading cache?") },
            text = { Text("Removes saved Home picks, artwork and disposable streamed audio. Artwork cache is capped at 256 MB and streamed audio at 512 MB; Home stores up to 64 search pools and 64 radio pools. Downloads, likes, playlists and played history stay untouched. New artwork and picks will need the network again.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        coil.Coil.imageLoader(ctx).diskCache?.clear()
                        com.contentagent.vibestream.player.PlaybackCache.clear(ctx)
                        ctx.getSharedPreferences("vibestream_radio_v1", 0).edit().clear().commit()
                        shelfPrefs(ctx).edit().clear().commit()
                    }
                    coil.Coil.imageLoader(ctx).memoryCache?.clear()
                    refreshCatalog()
                    clearShelfMemory()
                    radioSongs = emptyList(); cacheEpoch++; discoveryRound++; forceRefresh = true
                    cacheDialog = false
                }
            }) { Text("Clear cache") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cacheDialog = false }) { Text("Keep cache") } })
    }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("home-shelves"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.FilterChip(
                    selected = musicOnly,
                    onClick = { onModeChange(true) },
                    label = { Text("Music") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextSecondary,
                        selectedContainerColor = AmberOrange,
                        selectedLabelColor = DarkBackground
                    )
                )
                androidx.compose.material3.FilterChip(
                    selected = !musicOnly,
                    onClick = { onModeChange(false) },
                    label = { Text("Podcasts & more") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextSecondary,
                        selectedContainerColor = AmberOrange,
                        selectedLabelColor = DarkBackground
                    )
                )
            }
        }
        val mixDefs: List<Triple<String, String, String>> =
            if (profile.languages.isNotEmpty() || profile.moods.isNotEmpty()) {
                profile.languages.take(2).map { Triple("$it mix", "Hits and fresh picks", "$it hit songs") } +
                    profile.moods.take(2).map { Triple("$it mix", "Made for your mood", "${profile.languages.firstOrNull()?.plus(" ") ?: ""}$it songs") }
            } else {
                listOf(
                    Triple("Tamil mix", "Hits and fresh picks", "tamil hit songs"),
                    Triple("Hindi mix", "Hits and fresh picks", "bollywood hit songs"),
                    Triple("Philosophical mix", "Made for your mood", "tamil philosophical songs"),
                    Triple("Retro mix", "Made for your mood", "retro classic songs")
                )
            }
        // One card per mix: "Your mood" first, then the named mixes. Each is its own
        // shuffled queue, like Spotify's Made For You hub.
        val namedMixes: List<Triple<String, String, List<Song>>> = run {
            val out = mutableListOf<Triple<String, String, List<Song>>>()
            if (moodMix.isNotEmpty()) {
                out.add(Triple("Your mood", "New-to-you picks from your taste", moodMix))
            }
            for ((name, sub, q) in mixDefs) {
                val base = ShelfCache.data[cacheKey(q, musicOnly)] ?: continue
                val songs = DiscoveryPolicy.fresh(base, known, skippedArtists, musicOnly = musicOnly)
                if (songs.size >= 5) out.add(Triple(name, sub, songs))
            }
            out
        }
        item {
            DiscoveryHeader(
                refreshing = refreshing,
                empty = moodMix.isEmpty(),
                onRefresh = {
                    if (!refreshing) {
                        refreshCatalog()
                        refreshing = true
                        forceRefresh = true
                        discoveryRound++
                        shelfPrefs(ctx).edit().putInt("discovery_round", discoveryRound).apply()
                    }
                },
                onEditTaste = onEditTaste
            )
            Row {
                androidx.compose.material3.TextButton(enabled = !refreshing, onClick = { cacheDialog = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Loading cache", color = TextSecondary, fontSize = 12.sp)
                }
                androidx.compose.material3.TextButton(onClick = { diagnosticsDialog = true }) {
                    Text("Home diagnostics", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        if (namedMixes.isNotEmpty()) {
            item {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(namedMixes.size) { i ->
                            val (name, sub, songs) = namedMixes[i]
                            val palette = listOf(
                                AmberOrange,
                                androidx.compose.ui.graphics.Color(0xFF7C4DFF),
                                androidx.compose.ui.graphics.Color(0xFF00897B),
                                androidx.compose.ui.graphics.Color(0xFFE53935),
                                androidx.compose.ui.graphics.Color(0xFF3949AB)
                            )
                            Column(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette[i % palette.size].copy(alpha = 0.85f))
                                    .clickable { onPlayMood(songs) }
                                    .padding(14.dp)
                            ) {
                                Text(name, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(sub, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(18.dp))
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("${songs.size} songs", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(17.dp))
                                            .background(androidx.compose.ui.graphics.Color.White),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play $name",
                                            tint = androidx.compose.ui.graphics.Color.Black,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Text(
                        text = "Recently played",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    // 4-row grid of compact rows (the "quick picks" pattern from
                    // leading open players, re-written in our own code): denser
                    // and faster to scan than a row of wide cards.
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp * 4 + 8.dp * 3)
                    ) {
                        gridItems(recent.take(16), key = { it.id }) { song ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .width(250.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPlay(song, recent) }
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" },
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceElevated)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, color = if (song.id == currentSongId) AmberOrange else TextPrimary,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                com.contentagent.vibestream.ui.components.SongOptionsButton(song)
                            }
                        }
                    }
                }
            }
        }
        items(shelfList) { (title, query) ->
            val base = if (query == shelfList.first().second) discoveryPool.takeIf { it.isNotEmpty() }
                else ShelfCache.data[cacheKey(query, musicOnly)]
            val songs = base?.let { DiscoveryPolicy.fresh(it, known, skippedArtists, limit = it.size, musicOnly = musicOnly) }
            val failed = ShelfCache.failed[cacheKey(query, musicOnly)] == true
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeeAll(title, query) }
                        .padding(horizontal = 16.dp)
                        .testTag("shelf-title-$title")
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("›", color = TextSecondary, fontSize = 20.sp)
                }
                Spacer(Modifier.height(10.dp))
                val list = songs
                if (list == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        if (failed) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Could not load this shelf", color = TextSecondary, fontSize = 13.sp)
                                androidx.compose.material3.TextButton(onClick = { refreshCatalog(); forceRefresh = true; discoveryRound++ }) {
                                    Text("Retry", color = AmberOrange)
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (list.isEmpty()) {
                    val message = when {
                        failed -> "Could not refresh this shelf. Try again."
                        base.isNotEmpty() && DiscoveryPolicy.fresh(base, emptySet(), limit = 1, musicOnly = musicOnly).isEmpty() -> "No eligible songs in these results. Try more picks."
                        else -> "These picks are already in your listening history or library. Try more picks."
                    }
                    Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    androidx.compose.material3.TextButton(enabled = !refreshing, onClick = {
                        refreshCatalog(); forceRefresh = true; discoveryRound++
                    }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("More picks", color = AmberOrange) }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list, key = { it.id }) { song ->
                            Column(
                                modifier = Modifier.width(140.dp).clickable { onPlay(song, list) }
                            ) {
                                Box {
                                    ShelfArtwork(song)
                                    Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd).background(DarkBackground.copy(alpha = 0.8f), RoundedCornerShape(24.dp))) {
                                        com.contentagent.vibestream.ui.components.SongOptionsButton(song)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    song.title,
                                    color = if (song.id == currentSongId) AmberOrange else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // End-of-shelf card: keeps the same shelf going with fresh
                        // unseen picks instead of a dead end.
                        item {
                            val k = cacheKey(query, musicOnly)
                            val loading = ShelfCache.loadingMore[k] == true
                            val depleted = ShelfCache.depleted[k] == true
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .clickable(enabled = !loading && !depleted) {
                                        scope.launch { showMore(ctx, search, query, musicOnly, null) }
                                    }
                                    .padding(8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(22.dp))
                                } else if (depleted) {
                                    Text("No new picks right now", color = TextSecondary, fontSize = 11.sp)
                                } else {
                                    Text("+", color = AmberOrange, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Show more", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfArtwork(song: Song) {
    var failed by remember(song.id, song.thumbnailUrl) { mutableStateOf(false) }
    Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(DarkSurfaceElevated),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("♪", color = TextSecondary, fontSize = 32.sp)
        AsyncImage(
            model = if (failed || song.thumbnailUrl.isBlank()) "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" else song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Shared by Home and the phone-size render regression. */
@Composable
fun DiscoveryHeader(refreshing: Boolean, empty: Boolean, onRefresh: () -> Unit, onEditTaste: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp)) {
        Text(
            if (refreshing) "Finding new-to-you songs..." else if (empty) "No new picks yet. Retry or edit your taste." else "Discovery excludes your known songs",
            color = TextSecondary, fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Made for you", style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            androidx.compose.material3.FilledTonalButton(
                enabled = !refreshing,
                onClick = onRefresh,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceElevated, contentColor = AmberOrange,
                    disabledContainerColor = DarkSurfaceElevated, disabledContentColor = TextSecondary)
            ) {
                if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = AmberOrange, strokeWidth = 2.dp)
                else androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (refreshing) "Finding..." else "New picks", fontSize = 12.sp)
            }
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.IconButton(onClick = onEditTaste, modifier = Modifier.size(40.dp)) {
                androidx.compose.material3.Icon(Icons.Default.Edit, contentDescription = "Edit taste", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
