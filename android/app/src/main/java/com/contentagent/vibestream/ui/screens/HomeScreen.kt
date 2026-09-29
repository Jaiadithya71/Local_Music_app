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

private val featuredTrendingTracks = listOf(
    Song(
        id = "IqwIOlhfCak",
        title = "Badass (Leo)",
        artist = "Anirudh Ravichander",
        durationSec = 236,
        durationText = "3:56",
        thumbnailUrl = "https://i.ytimg.com/vi/IqwIOlhfCak/hqdefault.jpg"
    ),
    Song(
        id = "DsjRNPrvq6U",
        title = "Hukum - Thalaivar Alappara (Jailer)",
        artist = "Anirudh Ravichander",
        durationSec = 208,
        durationText = "3:28",
        thumbnailUrl = "https://i.ytimg.com/vi/DsjRNPrvq6U/hqdefault.jpg"
    ),
    Song(
        id = "szvt1vD0Uug",
        title = "Naa Ready (Leo)",
        artist = "Thalapathy Vijay, Anirudh",
        durationSec = 255,
        durationText = "4:15",
        thumbnailUrl = "https://i.ytimg.com/vi/szvt1vD0Uug/hqdefault.jpg"
    ),
    Song(
        id = "-aVdxDDrcTc",
        title = "Arabic Kuthu - Halamithi Habibo (Beast)",
        artist = "Anirudh, Jonita Gandhi",
        durationSec = 280,
        durationText = "4:40",
        thumbnailUrl = "https://i.ytimg.com/vi/-aVdxDDrcTc/hqdefault.jpg"
    ),
    Song(
        id = "VU23OPQ1Pmc",
        title = "Katchi Sera",
        artist = "Sai Abhyankkar",
        durationSec = 185,
        durationText = "3:05",
        thumbnailUrl = "https://i.ytimg.com/vi/VU23OPQ1Pmc/hqdefault.jpg"
    ),
    Song(
        id = "a3Ue-LN5B9U",
        title = "Aasa Kooda",
        artist = "Sai Abhyankkar, Sai Smriti",
        durationSec = 229,
        durationText = "3:49",
        thumbnailUrl = "https://i.ytimg.com/vi/a3Ue-LN5B9U/hqdefault.jpg"
    ),
    Song(
        id = "g0sYCahy_vg",
        title = "Marakkuma Nenjam (VTK)",
        artist = "A.R. Rahman",
        durationSec = 278,
        durationText = "4:38",
        thumbnailUrl = "https://i.ytimg.com/vi/g0sYCahy_vg/hqdefault.jpg"
    ),
    Song(
        id = "YFYiTS46x-8",
        title = "Hosanna (Vinnaithaandi Varuvaayaa)",
        artist = "A.R. Rahman, Vijay Prakash",
        durationSec = 337,
        durationText = "5:37",
        thumbnailUrl = "https://i.ytimg.com/vi/YFYiTS46x-8/hqdefault.jpg"
    ),
    Song(
        id = "thtAxtEuX6c",
        title = "En Kadhal Solla (Paiyaa)",
        artist = "Yuvan Shankar Raja",
        durationSec = 287,
        durationText = "4:47",
        thumbnailUrl = "https://i.ytimg.com/vi/thtAxtEuX6c/hqdefault.jpg"
    ),
    Song(
        id = "fHI8X4OXluQ",
        title = "Blinding Lights",
        artist = "The Weeknd",
        durationSec = 200,
        durationText = "3:20",
        thumbnailUrl = "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    extractor: YouTubeExtractor,
    offlineStorage: OfflineStorage,
    downloadManager: DownloadManager,
    playerController: MusicPlayerController
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) } // 0 = Discover, 1 = Downloads
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Song>>(featuredTrendingTracks) }
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

            // Tab Switcher: Discover vs Downloads
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkBackground,
                contentColor = AmberOrange,
                divider = {
                    Divider(color = DarkSurfaceBorder)
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Discover & Stream",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    icon = { Icon(Icons.Default.TravelExplore, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Downloads (${downloadedSongs.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    icon = { Icon(Icons.Default.DownloadDone, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
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
                                    searchResults = featuredTrendingTracks
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
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = DarkSurfaceBorder,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Search & Stream Any Song",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Type an artist or track above to stream directly from YouTube or tap download to save offline.",
                                        color = TextTertiary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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
                                            playerController.playSong(activeSong)
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
            } else {
                // DOWNLOADS / OFFLINE TAB
                Column(modifier = Modifier.fillMaxSize()) {
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
                                        playerController.playSong(song)
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
            onDismiss = { isPlayerSheetVisible = false }
        )
    }
}
