package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.contentagent.vibestream.data.model.ArtistPage
import com.contentagent.vibestream.data.model.SearchEntry
import com.contentagent.vibestream.data.model.SearchSections
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.ui.components.SongCard
import com.contentagent.vibestream.ui.theme.AmberOrange
import com.contentagent.vibestream.ui.theme.DarkBackground
import com.contentagent.vibestream.ui.theme.DarkSurfaceBorder
import com.contentagent.vibestream.ui.theme.DarkSurfaceElevated
import com.contentagent.vibestream.ui.theme.TextPrimary
import com.contentagent.vibestream.ui.theme.TextSecondary
import com.contentagent.vibestream.ui.theme.TextTertiary

/**
 * Sectioned search results in the YouTube Music layout: top result hero, then
 * Songs / Videos / Albums / Artists / Playlists sections. The chips choose one
 * expanded section or the All summary. Albums and artists open their own pages.
 */
@Composable
fun SectionedSearchResults(
    sections: SearchSections,
    activeChip: String,
    onChip: (String) -> Unit,
    currentSongId: String?,
    isPlaying: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    downloadProgress: (String) -> Float,
    onPlay: (Song, List<Song>) -> Unit,
    onDownload: (Song) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(DarkBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((key, label) in listOf("all" to "All", "song" to "Songs", "video" to "Videos", "album" to "Albums", "artist" to "Artists")) {
                FilterChip(
                    selected = activeChip == key,
                    onClick = { onChip(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DarkSurfaceElevated,
                        labelColor = TextSecondary,
                        selectedContainerColor = AmberOrange,
                        selectedLabelColor = DarkBackground
                    )
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val showAll = activeChip == "all"
            if (showAll && sections.topResult != null) {
                item { TopResultHero(sections.topResult, onPlay = { onPlay(sections.topResult, sections.playable) }) }
            }
            if (showAll || activeChip == "song") {
                sectionList(
                    title = "Songs",
                    items = sections.songs,
                    summaryCount = 4,
                    showAll = activeChip == "song",
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    context = sections.playable,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onExpand = { onChip("song") }
                )
            }
            if (showAll || activeChip == "video") {
                sectionList(
                    title = "Videos",
                    items = sections.videos,
                    summaryCount = 2,
                    showAll = activeChip == "video",
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    context = sections.playable,
                    onPlay = onPlay,
                    onDownload = onDownload,
                    onExpand = { onChip("video") }
                )
            }
            if ((showAll || activeChip == "album") && sections.albums.isNotEmpty()) {
                item { EntryRowSection("Albums", sections.albums, onOpenEntry) }
            }
            if ((showAll || activeChip == "artist") && sections.artists.isNotEmpty()) {
                item { EntryRowSection("Artists", sections.artists, onOpenEntry, circular = true) }
            }
            if (showAll && sections.playlists.isNotEmpty()) {
                item { EntryRowSection("Playlists", sections.playlists, onOpenEntry) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionList(
    title: String,
    items: List<Song>,
    summaryCount: Int,
    showAll: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    downloadProgress: (String) -> Float,
    context: List<Song>,
    onPlay: (Song, List<Song>) -> Unit,
    onDownload: (Song) -> Unit,
    onExpand: () -> Unit
) {
    if (items.isEmpty()) return
    val visible = if (showAll) items else items.take(summaryCount)
    item {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
    items(visible, key = { title + "|" + it.id }) { song ->
        SongCard(
            song = song,
            isCurrentSong = currentSongId == song.id,
            isPlaying = currentSongId == song.id && isPlaying,
            isDownloading = isDownloading(song.id),
            downloadProgress = downloadProgress(song.id),
            isDownloaded = isDownloaded(song.id),
            onPlayClick = { onPlay(song, context) },
            onDownloadClick = { onDownload(song) }
        )
    }
    if (!showAll && items.size > summaryCount) {
        item {
            Text("Show all ${items.size}", color = AmberOrange, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpand() }
                    .padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun TopResultHero(song: Song, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg" },
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Top result", color = TextTertiary, fontSize = 11.sp)
            Text(song.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = AmberOrange, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun EntryRowSection(title: String, entries: List<SearchEntry>, onOpen: (SearchEntry) -> Unit, circular: Boolean = false) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { title + "|" + it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(entry) },
                    horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
                    if (entry.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = entry.thumbnailUrl,
                            contentDescription = entry.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(if (circular) 72.dp else 110.dp).clip(shape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(if (circular) 72.dp else 110.dp).clip(shape).background(DarkSurfaceBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(entry.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle.substringBefore("•").trim(), color = TextSecondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Album page: art, Play/Shuffle and the track list. */
@Composable
fun AlbumPageView(
    entry: SearchEntry,
    tracks: List<Song>,
    loading: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    downloadProgress: (String) -> Float,
    onPlay: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onDownload: (Song) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary) }
            Text("Album", color = TextSecondary, fontSize = 13.sp)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF134E54), DarkBackground)))
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (entry.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = entry.thumbnailUrl, contentDescription = entry.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(entry.title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onPlayAll(tracks) },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberOrange, contentColor = DarkBackground),
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onShuffle(tracks) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            enabled = tracks.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberOrange)
                    }
                }
            }
            items(tracks, key = { "album|" + it.id }) { song ->
                SongCard(
                    song = song,
                    isCurrentSong = currentSongId == song.id,
                    isPlaying = currentSongId == song.id && isPlaying,
                    isDownloading = isDownloading(song.id),
                    downloadProgress = downloadProgress(song.id),
                    isDownloaded = isDownloaded(song.id),
                    onPlayClick = { onPlay(song, tracks) },
                    onDownloadClick = { onDownload(song) }
                )
            }
        }
    }
}

/** Artist page: photo, Play, top songs and albums. */
@Composable
fun ArtistPageView(
    page: ArtistPage?,
    loading: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    downloadProgress: (String) -> Float,
    onPlay: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onDownload: (Song) -> Unit,
    onOpenEntry: (SearchEntry) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary) }
            Text("Artist", color = TextSecondary, fontSize = 13.sp)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A2D5C), DarkBackground)))
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val photo = page?.thumbnailUrl ?: ""
                    if (photo.isNotEmpty()) {
                        AsyncImage(
                            model = photo, contentDescription = page?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(88.dp).clip(CircleShape).background(DarkSurfaceBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(page?.name ?: "", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onPlayAll(page?.topSongs ?: emptyList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberOrange, contentColor = DarkBackground),
                            enabled = !page?.topSongs.isNullOrEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AmberOrange)
                    }
                }
            }
            val songs = page?.topSongs ?: emptyList()
            if (songs.isNotEmpty()) {
                item {
                    Text("Top songs", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }
                items(songs, key = { "artist|" + it.id }) { song ->
                    SongCard(
                        song = song,
                        isCurrentSong = currentSongId == song.id,
                        isPlaying = currentSongId == song.id && isPlaying,
                        isDownloading = isDownloading(song.id),
                        downloadProgress = downloadProgress(song.id),
                        isDownloaded = isDownloaded(song.id),
                        onPlayClick = { onPlay(song, songs) },
                        onDownloadClick = { onDownload(song) }
                    )
                }
            }
            val albums = page?.albums ?: emptyList()
            if (albums.isNotEmpty()) {
                item { EntryRowSection("Albums", albums, onOpenEntry) }
            }
        }
    }
}
