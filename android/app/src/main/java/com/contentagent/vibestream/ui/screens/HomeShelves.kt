package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.theme.*

private val shelves = listOf(
    "Top picks for you" to "trending songs",
    "Tamil hits" to "tamil hit songs",
    "Hindi hits" to "bollywood hit songs",
    "Chill and focus" to "lofi chill songs",
    "Workout" to "workout songs"
)

private object ShelfCache {
    val data = mutableStateMapOf<String, List<Song>>()
    val failed = mutableStateMapOf<String, Boolean>()
    var restored = false
}

private val mixSeedQueries = listOf(
    "tamil hit songs", "bollywood hit songs", "tamil philosophical songs", "retro classic songs"
)

private fun restoreMoodMix(ctx: android.content.Context): List<Song> {
    val json = shelfPrefs(ctx).getString("mood_mix", null) ?: return emptyList()
    val type = object : com.google.gson.reflect.TypeToken<List<Song>>() {}.type
    return try { com.google.gson.Gson().fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
}

private fun saveMoodMix(ctx: android.content.Context, songs: List<Song>) {
    shelfPrefs(ctx).edit().putString("mood_mix", com.google.gson.Gson().toJson(songs)).apply()
}

/** Liked + recent + his playlists + cached taste-genre songs, deduped. No network. */
private fun buildMoodMix(
    liked: List<Song>,
    recent: List<Song>,
    playlists: List<com.contentagent.vibestream.data.local.Playlist>,
    genreSongs: List<Song>
): List<Song> {
    return (liked + recent + playlists.flatMap { it.songs } + genreSongs)
        .distinctBy { it.id }
        .take(50)
}

private fun shelfPrefs(ctx: android.content.Context) =
    ctx.getSharedPreferences("vibestream_shelves", android.content.Context.MODE_PRIVATE)

private fun cacheKey(query: String, musicOnly: Boolean) = (if (musicOnly) "M|" else "A|") + query

private fun restoreShelves(ctx: android.content.Context, queries: List<String>, musicOnly: Boolean) {
    val gson = com.google.gson.Gson()
    val type = object : com.google.gson.reflect.TypeToken<List<Song>>() {}.type
    for (q in queries) {
        val k = cacheKey(q, musicOnly)
        if (ShelfCache.data[k] != null) continue
        val json = shelfPrefs(ctx).getString(k, null) ?: continue
        val list: List<Song>? = try { gson.fromJson(json, type) } catch (e: Exception) { null }
        if (!list.isNullOrEmpty()) ShelfCache.data[k] = list
    }
}

/** Fetches a shelf, keeps showing cached songs if the refresh fails. */
private suspend fun loadShelf(ctx: android.content.Context, extractor: YouTubeExtractor, query: String, musicOnly: Boolean) {
    val k = cacheKey(query, musicOnly)
    ShelfCache.failed.remove(k)
    var result: List<Song> = emptyList()
    for (attempt in 1..2) {
        result = try {
            kotlinx.coroutines.withTimeout(20_000) { extractor.searchSongs(query, musicOnly) }
        } catch (e: Exception) { emptyList() }
        if (result.isNotEmpty()) break
    }
    if (result.isNotEmpty()) {
        val clean = result.take(12).map { it.copy(streamUrl = null) }
        ShelfCache.data[k] = clean
        shelfPrefs(ctx).edit().putString(k, com.google.gson.Gson().toJson(clean)).apply()
    } else if (ShelfCache.data[k] == null) {
        ShelfCache.failed[k] = true
    }
}

/** Amazon Music style Home: titled horizontal shelves of square cards. */
@Composable
fun HomeShelves(
    extractor: YouTubeExtractor,
    currentSongId: String?,
    onPlay: (Song, List<Song>) -> Unit,
    onSeeAll: (String) -> Unit,
    recent: List<Song> = emptyList(),
    liked: List<Song> = emptyList(),
    playlists: List<com.contentagent.vibestream.data.local.Playlist> = emptyList(),
    onPlayMood: (List<Song>) -> Unit = {},
    onEditTaste: () -> Unit = {},
    musicOnly: Boolean = true,
    onModeChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Top picks follow what he plays and likes: most frequent artist across liked + recent.
    val topArtist = (liked + recent).groupingBy { it.artist }.eachCount()
        .filterKeys { it.isNotBlank() && it != "Unknown Artist" && it != "YouTube" }
        .maxByOrNull { it.value }?.key
    // Taste onboarding answers seed the shelves; defaults when skipped.
    val profile = remember { com.contentagent.vibestream.data.local.TasteStore(ctx).load() }
    val tasteShelves = profile.languages.take(2).map { "$it hits" to "$it hit songs" } +
        profile.moods.take(2).map { it to "$it songs" }
    val shelfList = remember(topArtist) {
        val topQuery = when {
            topArtist != null -> "$topArtist songs"
            profile.artists.isNotEmpty() -> "${profile.artists.first()} songs"
            else -> "trending songs"
        }
        listOf("Top picks for you" to topQuery) + tasteShelves.ifEmpty { shelves.drop(1) }
    }
    val seedQueries = (shelfList.map { it.second } + mixSeedQueries).distinct()
    val seedSongs = seedQueries.flatMap { ShelfCache.data[cacheKey(it, musicOnly)] ?: emptyList() }
    var moodMix by remember { mutableStateOf(restoreMoodMix(ctx)) }
    // Show cached shelves instantly, then refresh one at a time in the background.
    LaunchedEffect(shelfList, musicOnly) {
        restoreShelves(ctx, shelfList.map { it.second }, musicOnly)
        for ((_, q) in shelfList) loadShelf(ctx, extractor, q, musicOnly)
        for (q in seedQueries) if (ShelfCache.data[cacheKey(q, musicOnly)] == null) loadShelf(ctx, extractor, q, musicOnly)
        moodMix = buildMoodMix(liked, recent, playlists, seedSongs)
        if (moodMix.isNotEmpty()) saveMoodMix(ctx, moodMix)
    }
    // Keep the mix in step with his library without hitting the network.
    LaunchedEffect(liked.size, recent.size, playlists.size) {
        val rebuilt = buildMoodMix(liked, recent, playlists, seedSongs)
        if (rebuilt.isNotEmpty()) {
            moodMix = rebuilt
            saveMoodMix(ctx, rebuilt)
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
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
        if (moodMix.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .clickable {
                            val q = moodMix.shuffled()
                            onPlayMood(q)
                        }
                        .padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your mood",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (profile.languages.isNotEmpty() || profile.moods.isNotEmpty())
                                "A mix from your taste: " + (profile.languages + profile.moods).take(4).joinToString(", ")
                            else "Your Tamil, Hindi, philosophical and retro mix",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onEditTaste) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit taste",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(AmberOrange),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play your mood mix",
                            tint = androidx.compose.ui.graphics.Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Recently played",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recent.take(12), key = { it.id }) { song ->
                            Column(modifier = Modifier.width(140.dp).clickable { onPlay(song, recent) }) {
                                AsyncImage(
                                    model = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" },
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(song.title, color = if (song.id == currentSongId) AmberOrange else TextPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        items(shelfList) { (title, query) ->
            val songs = ShelfCache.data[cacheKey(query, musicOnly)]
            val failed = ShelfCache.failed[cacheKey(query, musicOnly)] == true
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onSeeAll(query) }
                )
                val list = songs
                if (list == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        if (failed) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Could not load this shelf", color = TextSecondary, fontSize = 13.sp)
                                androidx.compose.material3.TextButton(onClick = { scope.launch { loadShelf(ctx, extractor, query, musicOnly) } }) {
                                    Text("Retry", color = AmberOrange)
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list, key = { it.id }) { song ->
                            Column(
                                modifier = Modifier.width(140.dp).clickable { onPlay(song, list) }
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    song.title,
                                    color = if (song.id == currentSongId) AmberOrange else TextPrimary,
                                    fontSize = 13.sp,
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
                    }
                }
            }
        }
    }
}
