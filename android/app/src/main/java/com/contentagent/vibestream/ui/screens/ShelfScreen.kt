package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.SongOptionsButton
import com.contentagent.vibestream.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One shelf as a full page: the whole list vertically, a search bar that stays
 * inside the category ("tamil" on the Workout page searches "tamil workout
 * songs"), and Show more at the bottom using the same refill machinery as Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    title: String,
    query: String,
    extractor: YouTubeExtractor,
    search: suspend (String, Boolean) -> List<Song> = { q, m -> extractor.searchSongs(q, m) },
    musicOnly: Boolean,
    currentSongId: String?,
    known: Set<String>,
    onPlay: (Song, List<Song>) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable { mutableStateOf("") }
    val effectiveQuery = if (searchText.isBlank()) query else "${searchText.trim()} $query"
    val k = cacheKey(effectiveQuery, musicOnly)
    val songs = ShelfCache.data[k] ?: emptyList()
    val searching = ShelfCache.failed[k] != true && songs.isEmpty()
    val loadingMore = ShelfCache.loadingMore[k] == true
    val depleted = ShelfCache.depleted[k] == true

    // Load the scoped list (full category list when blank). The first load is
    // immediate; only later query edits are debounced.
    var typedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveQuery, musicOnly) {
        if (typedOnce) delay(350)
        typedOnce = true
        if (ShelfCache.data[k].isNullOrEmpty()) {
            loadShelf(ctx, search, effectiveQuery, musicOnly, force = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("shelf-back")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("shelf-search"),
            placeholder = { Text("Search in $title", color = TextTertiary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                scope.launch { loadShelf(ctx, search, effectiveQuery, musicOnly, force = true) }
            }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberOrange,
                unfocusedBorderColor = DarkSurfaceBorder,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        when {
            searching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmberOrange)
            }
            songs.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs found here yet.", color = TextSecondary, fontSize = 14.sp)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("shelf-list")) {
                items(songs, key = { it.id }) { song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(song, songs) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(DarkSurfaceElevated)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (song.id == currentSongId) AmberOrange else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SongOptionsButton(song)
                    }
                }
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                    ) {
                        when {
                            loadingMore -> CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(24.dp))
                            depleted -> Text("No new picks right now", color = TextSecondary, fontSize = 13.sp)
                            else -> TextButton(
                                onClick = { scope.launch { showMore(ctx, search, effectiveQuery, musicOnly, null) } },
                                modifier = Modifier.testTag("shelf-show-more")
                            ) {
                                Text("Show more", color = AmberOrange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
