package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.local.DownloadManager
import com.contentagent.vibestream.data.local.OfflineStorage
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.player.MusicPlayerController
import com.contentagent.vibestream.ui.components.FullPlayerSheet
import com.contentagent.vibestream.ui.components.MiniPlayer
import com.contentagent.vibestream.ui.components.SongCard
import com.contentagent.vibestream.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    extractor: YouTubeExtractor,
    offlineStorage: OfflineStorage,
    downloadManager: DownloadManager,
    playerController: MusicPlayerController,
    library: com.contentagent.vibestream.data.local.LibraryStore
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Find, 2 = Library
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isPlayerSheetVisible by remember { mutableStateOf(false) }

    // Real-time search recommendation autocomplete as user types
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            kotlinx.coroutines.delay(220) // Debounce typing
            try {
                searchSuggestions = extractor.getSearchSuggestions(searchQuery.trim())
                showSuggestions = searchSuggestions.isNotEmpty()
            } catch (e: Exception) {
                searchSuggestions = emptyList()
                showSuggestions = false
            }
        } else {
            searchSuggestions = emptyList()
            showSuggestions = false
        }
    }

    // Collect reactive state flows
    val downloadedSongs by offlineStorage.downloadedSongs.collectAsState()
    val downloadProgressMap by downloadManager.downloadProgressMap.collectAsState()
    val currentSong by playerController.currentSong.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val isBuffering by playerController.isBuffering.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    val duration by playerController.duration.collectAsState()
    val playerError by playerController.errorMessage.collectAsState()
    val likedSongs by library.liked.collectAsState()
    val recentSongs by library.recent.collectAsState()
    var librarySection by remember { mutableStateOf(0) } // 0 downloads, 1 liked, 2 playlists, 3 recent
    val queueList by playerController.queue.collectAsState()
    val queueIdx by playerController.queueIndex.collectAsState()
    val shuffleOn by playerController.shuffle.collectAsState()
    val repeatMode by playerController.repeat.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(currentSong?.id) {
        currentSong?.let { library.addRecent(it) }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        showSuggestions = false
        isSearching = true
        searchError = null

        coroutineScope.launch {
            try {
                val results = extractor.searchSongs(query)
                searchResults = results
                if (results.isEmpty()) {
                    searchError = "No songs found for '$query'. Try another search term."
                }
            } catch (e: Exception) {
                searchError = "Search error: ${e.localizedMessage}"
            } finally {
                isSearching = false
            }
        }
    }

    val quickGenres = listOf("Trending", "Lo-Fi Hip Hop", "Synthwave", "Rock Anthems", "Chill Acoustic", "EDM Hits")

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
          Column {
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong!!,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    currentPosition = currentPosition,
                    duration = duration,
                    onTogglePlayPause = { playerController.togglePlayPause() },
                    onClick = { isPlayerSheetVisible = true }
                )
            }
            NavigationBar(containerColor = DarkSurface) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("Find", Icons.Default.Search, 1),
                    Triple("Library", Icons.Default.LibraryMusic, 2)
                )
                for ((label, icon, idx) in items) {
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(if (idx == 2) "Library (${downloadedSongs.size})" else label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = AmberOrange,
                            indicatorColor = AmberOrange,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
          }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AmberOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VibeStream",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SuccessGreen, shape = RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "On-Device",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }


            if (selectedTab == 1) {
                // DISCOVER TAB
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("Search songs, artists, albums…", color = TextTertiary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    searchError = null
                                    showSuggestions = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
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

                    // Real-time Search Suggestions / Autocomplete Recommendations
                    if (showSuggestions && searchSuggestions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                for (suggestion in searchSuggestions.take(6)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = suggestion
                                                showSuggestions = false
                                                performSearch(suggestion)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = suggestion,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowOutward,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Genre Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (genre in quickGenres) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    searchQuery = genre
                                    performSearch(genre)
                                },
                                label = { Text(genre, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = DarkSurfaceBorder
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Error Notification if any
                    if (searchError != null) {
                        Text(
                            text = searchError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (playerError != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = playerError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Content: Loading, Empty state, or Results
                    when {
                        isSearching -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AmberOrange)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Searching YouTube without servers…", color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                        searchResults.isEmpty() -> {
                            CategoryTiles(onPick = { q -> searchQuery = q; performSearch(q) })
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(searchResults, key = { it.id }) { song ->
                                    val isCurrent = currentSong?.id == song.id
                                    val isDownloaded = offlineStorage.isDownloaded(song.id)
                                    val isDownloading = downloadManager.isDownloading(song.id)
                                    val progress = downloadProgressMap[song.id] ?: 0f

                                    // If already downloaded locally, use the local song version with stored path
                                    val activeSong = if (isDownloaded) (offlineStorage.getLocalSong(song.id) ?: song) else song

                                    SongCard(
                                        song = activeSong,
                                        isCurrentSong = isCurrent,
                                        isPlaying = isCurrent && isPlaying,
                                        isDownloading = isDownloading,
                                        downloadProgress = progress,
                                        isDownloaded = isDownloaded,
                                        onPlayClick = {
                                            playerController.playSong(activeSong, searchResults)
                                        },
                                        onDownloadClick = {
                                            coroutineScope.launch {
                                                downloadManager.downloadTrack(song)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 2) {
              Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val names = listOf("Downloads", "Liked (${likedSongs.size})", "Playlists", "Recent")
                    for ((i, n) in names.withIndex()) {
                        FilterChip(
                            selected = librarySection == i,
                            onClick = { librarySection = i },
                            label = { Text(n, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary,
                                selectedContainerColor = AmberOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
                if (librarySection != 0) {
                    Box(modifier = Modifier.weight(1f)) {
                        LibrarySection(
                            section = librarySection,
                            library = library,
                            currentSongId = currentSong?.id,
                            onPlay = { sg, ctx -> playerController.playSong(offlineStorage.getLocalSong(sg.id) ?: sg, ctx) }
                        )
                    }
                } else
                // DOWNLOADS / OFFLINE TAB
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (downloadedSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = DarkSurfaceBorder,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Offline Tracks Saved",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Songs downloaded from the Discover tab are stored locally on your device for offline listening without internet.",
                                    color = TextTertiary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stored on Device (${downloadedSongs.size} tracks)",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(downloadedSongs, key = { it.id }) { song ->
                                val isCurrent = currentSong?.id == song.id

                                SongCard(
                                    song = song,
                                    isCurrentSong = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    isDownloading = false,
                                    downloadProgress = 1f,
                                    isDownloaded = true,
                                    onPlayClick = {
                                        playerController.playSong(song, downloadedSongs)
                                    },
                                    onDownloadClick = {},
                                    onDeleteClick = {
                                        offlineStorage.removeSong(song.id)
                                    }
                                )
                            }
                        }
                    }
                }
              }
            } else {
                HomeShelves(
                    recent = recentSongs,
                    extractor = extractor,
                    currentSongId = currentSong?.id,
                    onPlay = { sg, ctx -> playerController.playSong(sg, ctx) },
                    onSeeAll = { q -> selectedTab = 1; searchQuery = q; performSearch(q) }
                )
            }
        }
    }

    // Expandable Full Player Sheet
    if (isPlayerSheetVisible && currentSong != null) {
        val song = currentSong!!
        val isDownloaded = offlineStorage.isDownloaded(song.id)
        val isDownloading = downloadManager.isDownloading(song.id)
        val progress = downloadProgressMap[song.id] ?: 0f

        FullPlayerSheet(
            song = song,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPosition = currentPosition,
            duration = duration,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            downloadProgress = progress,
            onTogglePlayPause = { playerController.togglePlayPause() },
            onSeekTo = { playerController.seekTo(it) },
            onSkipForward = { playerController.skipForward(10) },
            onSkipBackward = { playerController.skipBackward(10) },
            onDownloadClick = {
                coroutineScope.launch {
                    downloadManager.downloadTrack(song)
                }
            },
            shuffle = shuffleOn,
            repeat = repeatMode,
            onShuffle = { playerController.toggleShuffle() },
            onRepeat = { playerController.cycleRepeat() },
            onNext = { playerController.next() },
            onPrevious = { playerController.previous() },
            onQueue = { showQueue = true },
            isLiked = likedSongs.any { it.id == song.id },
            onToggleLike = { library.toggleLike(song) },
            onAddToPlaylist = { addToPlaylistSong = song },
            onDismiss = { isPlayerSheetVisible = false }
        )
    }
    if (showQueue) {
        QueueDialog(queue = queueList, index = queueIdx, onPick = { playerController.playAt(it) }, onDismiss = { showQueue = false })
    }
    addToPlaylistSong?.let { sg ->
        AddToPlaylistDialog(library = library, song = sg, onDismiss = { addToPlaylistSong = null })
    }
}
