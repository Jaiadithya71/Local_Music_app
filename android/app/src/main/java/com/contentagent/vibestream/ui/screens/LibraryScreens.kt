package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    onPlay: (Song, List<Song>) -> Unit,
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    downloadErrors: Map<String, String> = emptyMap(),
    onDownload: (List<Song>) -> Unit = {}
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
                PlaylistDetail(
                    playlist = open, currentSongId = currentSongId,
                    downloadedIds = downloadedIds, downloadProgress = downloadProgress,
                    downloadErrors = downloadErrors, onDownload = onDownload,
                    onPlay = onPlay, onBack = { openPlaylist = null },
                    onDelete = { library.deletePlaylist(open.name); openPlaylist = null },
                    onRemove = { library.removeFromPlaylist(open.name, it) }
                )
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

/** Download actions stay separate from play and remove, with 48dp touch targets. */
@Composable
fun PlaylistDetail(
    playlist: Playlist,
    currentSongId: String?,
    downloadedIds: Set<String>,
    downloadProgress: Map<String, Float>,
    downloadErrors: Map<String, String>,
    onDownload: (List<Song>) -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (String) -> Unit
) {
    val missing = playlist.songs.distinctBy { it.id }.filter { it.id !in downloadedIds && it.id !in downloadProgress }
    val saved = playlist.songs.count { it.id in downloadedIds }
    val pending = playlist.songs.count { it.id in downloadProgress }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
            Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete playlist", tint = TextSecondary) }
        }
        Text("${playlist.songs.size} songs · $saved offline" + if (pending > 0) " · $pending queued / downloading" else "",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        FilledTonalButton(onClick = { onDownload(missing) }, enabled = missing.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(if (saved == playlist.songs.size && saved > 0) Icons.Default.DownloadDone else Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(when {
                playlist.songs.isEmpty() -> "No songs to download"
                saved == playlist.songs.size -> "Playlist downloaded"
                missing.isEmpty() -> "Downloads in progress"
                else -> "Download ${missing.size} songs"
            })
        }
        Text("Saves audio on this device. Uses your current connection. Keep the app open until finished.",
            color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (playlist.songs.isEmpty()) Text("Empty. Add songs from Now Playing.", color = TextTertiary, modifier = Modifier.padding(16.dp))
        LazyColumn {
            items(playlist.songs, key = { it.id }) { song ->
                Column {
                    SongRow(song, song.id == currentSongId, { onPlay(song, playlist.songs) }) {
                        val savedSong = song.id in downloadedIds
                        val progress = downloadProgress[song.id]
                        IconButton(onClick = { onDownload(listOf(song)) }, enabled = !savedSong && progress == null) {
                            when {
                                savedSong -> Icon(Icons.Default.DownloadDone, "Downloaded: ${song.title}", tint = AmberOrange)
                                progress != null -> CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AmberOrange, strokeWidth = 2.dp)
                                else -> Icon(Icons.Default.Download, "Download: ${song.title}", tint = AmberOrange)
                            }
                        }
                        // Remove lives behind an overflow menu plus a confirm, so a
                        // stray tap can never drop a song (Jai's 2026-11-18 ask).
                        var menuOpen by remember(song.id) { mutableStateOf(false) }
                        var confirmRemove by remember(song.id) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Options: ${song.title}", tint = TextSecondary) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Remove from playlist") },
                                    onClick = { menuOpen = false; confirmRemove = true }
                                )
                            }
                        }
                        if (confirmRemove) {
                            AlertDialog(
                                onDismissRequest = { confirmRemove = false },
                                title = { Text("Remove song?") },
                                text = { Text("Remove \"${song.title}\" from ${playlist.name}?") },
                                confirmButton = {
                                    TextButton(onClick = { confirmRemove = false; onRemove(song.id) }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                    val progress = downloadProgress[song.id]
                    val status = when {
                        song.id in downloadedIds -> "Available offline"
                        progress == 0f -> "Queued"
                        progress != null -> "Downloading ${(progress * 100).toInt()}%"
                        else -> downloadErrors[song.id]
                    }
                    if (status != null) Text(status, color = if (song.id in downloadErrors) MaterialTheme.colorScheme.error else TextSecondary,
                        fontSize = 11.sp, modifier = Modifier.padding(start = 64.dp, bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, emptyText: String, currentSongId: String?, onPlay: (Song, List<Song>) -> Unit) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = TextTertiary, modifier = Modifier.padding(32.dp))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(songs, key = { it.id }) { s -> SongRow(s, s.id == currentSongId, { onPlay(s, songs) }) }
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

@Composable
fun QueueDialog(queue: List<Song>, index: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = { Text("Queue (${queue.size})", color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(queue) { i, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (i == index) "▶" else "${i + 1}",
                            color = if (i == index) AmberOrange else TextTertiary,
                            modifier = Modifier.width(28.dp), fontSize = 12.sp
                        )
                        Column {
                            Text(s.title, color = if (i == index) AmberOrange else TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = AmberOrange) } }
    )
}
