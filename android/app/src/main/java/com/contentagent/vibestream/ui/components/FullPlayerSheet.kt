package com.contentagent.vibestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
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

            // Title & Artist
            Text(
                text = song.title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Storage Source Badge (Online Stream vs Offline File)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDownloaded) SuccessGreen.copy(alpha = 0.15f) else AmberOrange.copy(alpha = 0.15f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (isDownloaded) SuccessGreen else AmberOrange)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (isDownloaded) SuccessGreen else AmberOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDownloaded) "Playing from Local Storage (Offline)" else "Streaming Live via YouTube Pipeline",
                        color = if (isDownloaded) SuccessGreen else AmberOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download button
                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            progress = downloadProgress,
                            color = AmberOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    isDownloaded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Saved locally",
                            tint = SuccessGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    else -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download song for offline listening",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Skip -10s
                IconButton(onClick = onSkipBackward) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Big Play / Pause / Buffering button
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = AmberOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(AmberOrange)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Skip +10s
                IconButton(onClick = onSkipForward) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Fast-forward 10 seconds",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Close Sheet
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = TextSecondary,
                        modifier = Modifier.size(30.dp)
                    )
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
