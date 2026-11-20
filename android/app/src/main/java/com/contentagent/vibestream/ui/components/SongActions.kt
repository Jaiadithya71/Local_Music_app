package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.theme.*

data class SongActionRequest(val song: Song, val onRemove: (() -> Unit)? = null)
val LocalSongActions = staticCompositionLocalOf<((SongActionRequest) -> Unit)?> { null }

@Composable
fun SongOptionsButton(song: Song, onRemove: (() -> Unit)? = null) {
    val open = LocalSongActions.current ?: return
    IconButton(onClick = { open(SongActionRequest(song, onRemove)) }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Song actions: ${song.title}", tint = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song, isLiked: Boolean, isDownloaded: Boolean, isDownloading: Boolean,
    onLike: () -> Unit, onSave: () -> Unit, onDownload: () -> Unit,
    onRemove: (() -> Unit)? = null, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkSurfaceElevated) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(song.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(song.artist, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onLike, modifier = Modifier.fillMaxWidth()) {
                Text(if (isLiked) "Unlike song" else "Like song", color = TextPrimary)
            }
            TextButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Add to playlist", color = TextPrimary)
            }
            TextButton(onClick = onDownload, enabled = !isDownloaded && !isDownloading, modifier = Modifier.fillMaxWidth()) {
                Text(if (isDownloaded) "Downloaded" else if (isDownloading) "Downloading" else "Download song", color = TextSecondary)
            }
            onRemove?.let { remove ->
                TextButton(onClick = remove, modifier = Modifier.fillMaxWidth()) { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
