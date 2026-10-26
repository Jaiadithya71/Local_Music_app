package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
    shuffle: Boolean = false,
    repeat: Int = 0,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onQueue: () -> Unit = {},
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    moreFromArtist: List<Song> = emptyList(),
    onPlayArtistSong: (Song, List<Song>) -> Unit = { _, _ -> },
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    sleepTimerActive: Boolean = false,
    automaticNaming: Boolean = false,
    analysisStatus: String = "",
    onAutomaticNaming: (Boolean) -> Unit = {},
    lyricsContent: (@Composable () -> Unit)? = null
) {
    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = playerSheetState,
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = DarkSurfaceBorder,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                val thumb = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" }
                if (thumb.isNotEmpty()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AmberOrange,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title, artist and like
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDownloaded) "${song.artist}  ·  Offline" else song.artist,
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) AmberOrange else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (song.durationSec >= 600 || duration >= 600000) {
                var confirmNaming by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatically name songs", color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = automaticNaming, onCheckedChange = { if (it) confirmNaming = true else onAutomaticNaming(false) })
                }
                if (analysisStatus.isNotBlank()) Text(analysisStatus, color = TextSecondary, fontSize = 12.sp)
                if (confirmNaming) AlertDialog(onDismissRequest = { confirmNaming = false },
                    title = { Text("Enable automatic song naming?") },
                    text = { Text("For each new 10-minute to 3-hour recording you play, audio fingerprints will be sent to an unofficial Shazam connection. This reveals what's playing. No microphone or whole audio is uploaded. Uses extra data, battery and processing time. Names can be wrong; misses stay Unknown and song starts are approximate. The service may stop working. You can turn this off anytime.") },
                    confirmButton = { TextButton(onClick = { confirmNaming = false; onAutomaticNaming(true) }) { Text("Enable") } },
                    dismissButton = { TextButton(onClick = { confirmNaming = false }) { Text("Cancel") } })
                Spacer(Modifier.height(8.dp))
            }
            val chapters = song.chapters.orEmpty()
            if (chapters.size >= 2) {
                val chapterIndex = chapters.indexOfLast { it.startMs <= currentPosition }.coerceAtLeast(0)
                Text("Song ${chapterIndex + 1} of ${chapters.size} · ${chapters[chapterIndex].title}",
                    color = AmberOrange, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(chapters.size) { i ->
                        SuggestionChip(onClick = { onSeekTo(chapters[i].startMs) },
                            label = { Text("${i + 1}. ${chapters[i].title}", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) })
                    }
                }
                Text("Next skips within this recording. Timings supplied by the publisher.",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            } else if (song.durationSec >= 600) {
                Text(if (song.chapters == null) "Song timings not loaded. Next skips this recording."
                    else "No song timings found. Next skips this recording.",
                    color = TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            // Scrubber Slider
            var isUserSeeking by remember { mutableStateOf(false) }
            var seekValue by remember { mutableStateOf(0f) }

            val effectivePosition = if (isUserSeeking) seekValue.toLong() else currentPosition
            val maxRange = if (duration > 0) duration.toFloat() else 1f

            Slider(
                value = effectivePosition.toFloat().coerceIn(0f, maxRange),
                onValueChange = {
                    isUserSeeking = true
                    seekValue = it
                },
                onValueChangeFinished = {
                    onSeekTo(seekValue.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..maxRange,
                colors = SliderDefaults.colors(
                    thumbColor = AmberOrange,
                    activeTrackColor = AmberOrange,
                    inactiveTrackColor = DarkSurfaceBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(effectivePosition),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Text(
                    text = formatMs(duration),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Playback controls: shuffle, previous, play/pause, next, repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle, contentDescription = "Shuffle",
                        tint = if (shuffle) AmberOrange else TextSecondary, modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
                if (isBuffering) {
                    CircularProgressIndicator(color = AmberOrange, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(AmberOrange)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black, modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeat > 0) AmberOrange else TextSecondary, modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isDownloading -> CircularProgressIndicator(progress = downloadProgress, color = AmberOrange, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    isDownloaded -> Icon(Icons.Default.CheckCircle, contentDescription = "Saved locally", tint = SuccessGreen, modifier = Modifier.size(28.dp))
                    else -> IconButton(onClick = onDownloadClick) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = TextSecondary, modifier = Modifier.size(28.dp))
                    }
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onQueue) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse player", tint = TextSecondary, modifier = Modifier.size(30.dp))
                }
            }

            // Lyrics, sleep timer, equalizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        Icons.Default.Lyrics, contentDescription = "Lyrics",
                        tint = if (showLyrics) AmberOrange else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onSleepTimer) {
                    Icon(
                        Icons.Default.Bedtime, contentDescription = "Sleep timer",
                        tint = if (sleepTimerActive) AmberOrange else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onEqualizer) {
                    Icon(
                        Icons.Default.Equalizer, contentDescription = "Equalizer",
                        tint = TextSecondary, modifier = Modifier.size(26.dp)
                    )
                }
            }

            if (showLyrics && lyricsContent != null) {
                Spacer(modifier = Modifier.height(16.dp))
                lyricsContent()
            }

            // More from this artist - related picks under the controls, like
            // Spotify's Now Playing "More from <artist>" carousel.
            if (moreFromArtist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "More from ${song.artist}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(moreFromArtist.size) { idx ->
                            val pick = moreFromArtist[idx]
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .clickable { onPlayArtistSong(pick, moreFromArtist) }
                                    .padding(8.dp)
                            ) {
                                AsyncImage(
                                    model = pick.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${pick.id}/hqdefault.jpg" },
                                    contentDescription = pick.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pick.title,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = pick.artist,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
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

private fun formatMs(milliseconds: Long): String {
    if (milliseconds <= 0) return "0:00"
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
