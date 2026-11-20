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

/**
 * 2x2 artwork mosaic for a playlist, built from the first four song
 * thumbnails (the library pattern of the leading players, re-written in our
 * own code). Falls back to a tinted tile with a note icon when the playlist
 * has too little artwork.
 */
@Composable
private fun PlaylistMosaic(songs: List<Song>, size: androidx.compose.ui.unit.Dp) {
    val thumbs = songs.map { it.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${it.id}/hqdefault.jpg" } }.distinct().take(4)
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(DarkSurfaceElevated)) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = AmberOrange.copy(alpha = .65f), modifier = Modifier.align(Alignment.Center).size(size / 2))
        if (thumbs.size >= 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..1) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0..1) {
                            AsyncImage(
                                model = thumbs[row * 2 + col],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        } else if (thumbs.isNotEmpty()) {
            AsyncImage(
                model = thumbs.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = AmberOrange)
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
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(DarkSurfaceElevated), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            AsyncImage(
                model = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" },
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title, color = if (active) AmberOrange else TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (trailing == null) com.contentagent.vibestream.ui.components.SongOptionsButton(song)
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
    onDownload: (List<Song>) -> Unit = {},
    downloadTransfers: Map<String, com.contentagent.vibestream.data.local.DownloadTransfer> = emptyMap()
) {
    val liked by library.liked.collectAsState()
    val recent by library.recent.collectAsState()
    val playlists by library.playlists.collectAsState()
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }

    when (section) {
        1 -> SongList(liked, "Songs you like appear here. Use any song's actions menu.", currentSongId, onPlay)
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
                    onRemove = { library.removeFromPlaylist(open.name, it) },
                    downloadTransfers = downloadTransfers
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Text(
                        "Playlists", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    // "New playlist" sits in the list as its own row, like the
                    // leading players, instead of a floating text button.
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showNew = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(DarkSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AmberOrange)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist", color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    if (playlists.isEmpty()) {
                        Text("No playlists yet.", color = TextTertiary, modifier = Modifier.padding(16.dp))
                    }
                    LazyColumn {
                        items(playlists, key = { it.name }) { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openPlaylist = p.name }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistMosaic(p.songs, 56.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Music first; offline tools are secondary and song tools stay in overflow. */
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
    onRemove: (String) -> Unit,
    downloadTransfers: Map<String, com.contentagent.vibestream.data.local.DownloadTransfer> = emptyMap()
) {
    val missing = playlist.songs.distinctBy { it.id }.filter { it.id !in downloadedIds && it.id !in downloadProgress }
    val saved = playlist.songs.count { it.id in downloadedIds }
    val pending = playlist.songs.count { it.id in downloadProgress }
    var playlistMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { playlistMenu = true }) { Icon(Icons.Default.MoreVert, "Playlist options", tint = TextSecondary) }
                    DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { playlistMenu = false; confirmDelete = true })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                PlaylistMosaic(playlist.songs, 88.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR PLAYLIST", color = TextSecondary, fontSize = 10.sp)
                    Text(playlist.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Text("${playlist.songs.size} songs · $saved saved offline", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { playlist.songs.firstOrNull()?.let { onPlay(it, playlist.songs) } },
                    enabled = playlist.songs.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = AmberOrange, contentColor = DarkBackground)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play")
                }
                OutlinedButton(onClick = { onDownload(missing) }, enabled = missing.isNotEmpty()) {
                    Icon(if (saved == playlist.songs.size && saved > 0) Icons.Default.DownloadDone else Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(when {
                        playlist.songs.isEmpty() -> "Save offline"
                        saved == playlist.songs.size -> "Saved offline"
                        pending > 0 && missing.isEmpty() -> "Saving offline"
                        else -> "Save offline"
                    })
                }
            }
            if (pending > 0) {
                Text("$pending saving / queued · Keep the app open", color = AmberOrange, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (missing.isNotEmpty()) {
                Text("Save offline uses your current connection.", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text("Songs", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (playlist.songs.isEmpty()) Text("Add a song from its actions menu to start listening.", color = TextSecondary)
        }
        items(playlist.songs, key = { it.id }) { song ->
            Column {
                SongRow(song, song.id == currentSongId, { onPlay(song, playlist.songs) }) {
                    if (song.id in downloadedIds) Icon(Icons.Default.DownloadDone, "Downloaded: ${song.title}", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    var confirmRemove by remember(song.id) { mutableStateOf(false) }
                    com.contentagent.vibestream.ui.components.SongOptionsButton(song, onRemove = { confirmRemove = true })
                    if (confirmRemove) {
                        AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Remove song?") },
                            text = { Text("Remove this song from ${playlist.name}?") },
                            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove(song.id) }) { Text("Remove") } },
                            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } })
                    }
                }
                val transfer = downloadTransfers[song.id]
                val status = when {
                    song.id in downloadedIds -> null
                    transfer != null -> transfer.status
                    downloadProgress[song.id] == 0f -> "Queued"
                    song.id in downloadProgress -> "Downloading..."
                    else -> downloadErrors[song.id]
                }
                if (status != null) {
                    Column(Modifier.padding(start = 64.dp, bottom = 8.dp)) {
                        if (song.id in downloadProgress) {
                            if (transfer?.percent != null) LinearProgressIndicator(progress = transfer.percent!! / 100f, color = AmberOrange, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(color = AmberOrange, modifier = Modifier.fillMaxWidth())
                        }
                        Text(status, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete playlist?") },
        text = { Text("Your downloaded songs will stay on this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
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
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showNew = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("New playlist", color = AmberOrange, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                for (p in playlists) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { library.addToPlaylist(p.name, song); onDismiss() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistMosaic(p.songs, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.songs.size} songs", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
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
fun QueueDialog(queue: List<Song>, index: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, status: String = "") {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = {
            Column {
                Text("Queue (${queue.size})", color = TextPrimary)
                if (status.isNotBlank()) Text(status, color = AmberOrange, fontSize = 12.sp)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(queue) { i, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (i == index) "▶" else "${i + 1}",
                            color = if (i == index) AmberOrange else TextTertiary,
                            modifier = Modifier.width(24.dp), fontSize = 12.sp
                        )
                        AsyncImage(
                            model = s.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${s.id}/hqdefault.jpg" },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = if (i == index) AmberOrange else TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        com.contentagent.vibestream.ui.components.SongOptionsButton(s)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = AmberOrange) } }
    )
}
