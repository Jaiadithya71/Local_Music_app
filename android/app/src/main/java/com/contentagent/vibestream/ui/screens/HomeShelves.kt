package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Amazon Music style Home: titled horizontal shelves of square cards. */
@Composable
fun HomeShelves(
    extractor: YouTubeExtractor,
    currentSongId: String?,
    onPlay: (Song, List<Song>) -> Unit,
    onSeeAll: (String) -> Unit,
    recent: List<Song> = emptyList()
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
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
        items(shelves) { (title, query) ->
            var songs by remember(query) { mutableStateOf<List<Song>?>(null) }
            LaunchedEffect(query) {
                songs = try { extractor.searchSongs(query).take(12) } catch (e: Exception) { emptyList() }
            }
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
                        CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(24.dp))
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
