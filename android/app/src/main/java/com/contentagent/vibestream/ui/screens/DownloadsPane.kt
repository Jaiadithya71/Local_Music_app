package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.data.local.DownloadTransfer
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.SongCard
import com.contentagent.vibestream.ui.theme.*

@Composable
fun DownloadsPane(
    saved: List<Song>, transfers: List<DownloadTransfer>, currentSongId: String?, isPlaying: Boolean,
    onPlay: (Song) -> Unit, onRetry: (Song) -> Unit, onDelete: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    // Completed rows win during the brief handoff between file save and transfer removal.
    val pending = transfers.filter { t -> saved.none { it.id == t.song.id } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pending.isNotEmpty()) {
            item { Text("Downloads in progress (${pending.size})", color = TextPrimary, fontSize = 16.sp) }
            items(pending, key = { "pending:${it.song.id}" }) { transfer ->
                val failed = transfer.phase.contains("retry", ignoreCase = true)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(transfer.song.title, color = TextPrimary, fontSize = 15.sp)
                    Text(transfer.song.artist, color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (!failed) {
                        val percent = transfer.percent
                        if (percent != null) LinearProgressIndicator(progress = percent / 100f, color = AmberOrange, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(color = AmberOrange, modifier = Modifier.fillMaxWidth())
                    }
                    Text(transfer.status, color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp).then(if (failed) Modifier.clickable { onRetry(transfer.song) } else Modifier))
                    if (failed) TextButton(onClick = { onRetry(transfer.song) }) { Text("Retry", color = AmberOrange) }
                }
            }
        }
        item { Text("Stored on Device (${saved.size} tracks)", color = TextSecondary, fontSize = 13.sp) }
        if (saved.isEmpty()) {
            item { Text(if (pending.isEmpty()) "No Offline Tracks Saved" else "Completed downloads will appear here.", color = TextSecondary) }
        }
        items(saved, key = { "saved:${it.id}" }) { song ->
            SongCard(song, song.id == currentSongId, song.id == currentSongId && isPlaying,
                isDownloading = false, downloadProgress = 1f, isDownloaded = true,
                onPlayClick = { onPlay(song) }, onDownloadClick = {}, onDeleteClick = { onDelete(song) })
        }
    }
}
