package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentagent.vibestream.data.local.LibraryStore
import com.contentagent.vibestream.data.local.Playlist
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.theme.*

private val categories = listOf(
    "Tamil" to Color(0xFF0E7C86), "Hindi" to Color(0xFF7B3FA0), "Telugu" to Color(0xFFB5532A),
    "Malayalam" to Color(0xFF2E7D4F), "English Pop" to Color(0xFFC2185B), "Hip Hop" to Color(0xFF455A64),
    "Lo-Fi" to Color(0xFF3F51B5), "Devotional" to Color(0xFFB8860B), "Workout" to Color(0xFFD84315),
    "Romance" to Color(0xFFAD1457), "Retro classics" to Color(0xFF6D4C41), "Indie" to Color(0xFF00838F)
)

/** Find tab landing: grid of category tiles that run a search. */
@Composable
fun CategoryTiles(onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Browse all", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(categories) { (name, color) ->
                Box(
                    modifier = Modifier
                        .height(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                        .clickable { onPick(if (name == "Retro classics") "retro classic songs" else "$name songs") }
                        .padding(12.dp)
                ) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, active: Boolean, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, color = if (active) AmberOrange else TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Library sections other than Downloads: Liked, Playlists, Recent. */
@Composable
fun LibrarySection(
    section: Int, // 1 liked, 2 playlists, 3 recent
    library: LibraryStore,
    currentSongId: String?,
    onPlay: (Song) -> Unit
) {
    val liked by library.liked.collectAsState()
    val recent by library.recent.collectAsState()
    val playlists by library.playlists.collectAsState()
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }

    when (section) {
        1 -> SongList(liked, "Songs you like appear here. Tap the heart in Now Playing.", currentSongId, onPlay)
        3 -> SongList(recent, "Nothing played yet.", currentSongId, onPlay)
        else -> {
            val open = playlists.firstOrNull { it.name == openPlaylist }
            if (open != null) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { openPlaylist = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Text(open.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { library.deletePlaylist(open.name); openPlaylist = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete playlist", tint = TextSecondary)
                        }
                    }
                    if (open.songs.isEmpty()) {
                        Text("Empty. Add songs from Now Playing.", color = TextTertiary, modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn {
                        items(open.songs, key = { it.id }) { s ->
                            SongRow(s, s.id == currentSongId, { onPlay(s) }) {
                                IconButton(onClick = { library.removeFromPlaylist(open.name, s.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextSecondary)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    TextButton(onClick = { showNew = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AmberOrange)
                        Spacer(Modifier.width(6.dp))
                        Text("New playlist", color = AmberOrange)
                    }
                    if (playlists.isEmpty()) {
                        Text("No playlists yet.", color = TextTertiary, modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn {
                        items(playlists, key = { it.name }) { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { openPlaylist = p.name }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = AmberOrange)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("${p.songs.size} songs", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showNew) {
        NewPlaylistDialog(onDismiss = { showNew = false }, onCreate = { library.createPlaylist(it); showNew = false })
    }
}

@Composable
private fun SongList(songs: List<Song>, emptyText: String, currentSongId: String?, onPlay: (Song) -> Unit) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextTertiary, modifier = Modifier.padding(32.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(songs, key = { it.id }) { s -> SongRow(s, s.id == currentSongId, { onPlay(s) }) }
        }
    }
}

@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = { Text("New playlist", color = TextPrimary) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Name") })
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("Create", color = AmberOrange) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun AddToPlaylistDialog(library: LibraryStore, song: Song, onDismiss: () -> Unit) {
    val playlists by library.playlists.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = { Text("Add to playlist", color = TextPrimary) },
        text = {
            Column {
                TextButton(onClick = { showNew = true }) { Text("+ New playlist", color = AmberOrange) }
                for (p in playlists) {
                    Text(
                        p.name, color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().clickable { library.addToPlaylist(p.name, song); onDismiss() }.padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) } }
    )
    if (showNew) {
        NewPlaylistDialog(onDismiss = { showNew = false }, onCreate = { library.createPlaylist(it); library.addToPlaylist(it.trim(), song); showNew = false; onDismiss() })
    }
}
