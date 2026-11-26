package com.contentagent.vibestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.imageLoader
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.auth.YtMusicAuth
import com.contentagent.vibestream.data.local.DownloadManager
import com.contentagent.vibestream.data.local.LibraryBackup
import com.contentagent.vibestream.data.local.OfflineStorage
import com.contentagent.vibestream.data.lyrics.LyricsRepository
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.model.ArtistPage
import com.contentagent.vibestream.data.model.SearchEntry
import com.contentagent.vibestream.data.model.SearchSections
import com.contentagent.vibestream.player.EqualizerManager
import com.contentagent.vibestream.player.MusicPlayerController
import com.contentagent.vibestream.player.PlaybackService
import com.contentagent.vibestream.ui.components.EqualizerDialog
import com.contentagent.vibestream.ui.components.FullPlayerSheet
import com.contentagent.vibestream.ui.components.LyricsView
import com.contentagent.vibestream.ui.components.MiniPlayer
import com.contentagent.vibestream.ui.components.SleepTimerDialog
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
    library: com.contentagent.vibestream.data.local.LibraryStore,
    onEditTaste: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Find, 2 = Library
    // Open category page (title to query); null means the normal tab content.
    var openShelf by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchSections by remember { mutableStateOf<SearchSections?>(null) }
    var sectionChip by remember { mutableStateOf("all") }
    var openedEntry by remember { mutableStateOf<SearchEntry?>(null) }
    var openedAlbumTracks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var openedArtistPage by remember { mutableStateOf<ArtistPage?>(null) }
    var entryLoading by remember { mutableStateOf(false) }
    var showYtmSignIn by remember { mutableStateOf(false) }
    var ytmSignedIn by remember { mutableStateOf(YtMusicAuth.isSignedIn()) }
    var ytmPlaylists by remember { mutableStateOf<List<SearchEntry>>(emptyList()) }
    var ytmOpened by remember { mutableStateOf<SearchEntry?>(null) }
    var ytmOpenedTracks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var ytmLoading by remember { mutableStateOf(false) }

    // Load the signed-in YouTube Music library (Liked songs + playlists) once per sign-in.
    LaunchedEffect(ytmSignedIn) {
        if (ytmSignedIn && ytmPlaylists.isEmpty()) {
            ytmLoading = true
            var lists = extractor.libraryPlaylists()
            if (lists.none { it.id == "VLLM" }) {
                lists = listOf(SearchEntry("VLLM", "Liked songs", "YouTube Music", "", "playlist")) + lists
            }
            ytmPlaylists = lists
            ytmLoading = false
        }
    }

    val openYtmList: (SearchEntry) -> Unit = { entry ->
        ytmOpened = entry
        ytmOpenedTracks = emptyList()
        ytmLoading = true
        coroutineScope.launch {
            ytmOpenedTracks = extractor.browseAlbumTracks(entry.id)
            ytmLoading = false
        }
    }
    // (text, isPersonal): his own searches/plays come first, general feed after.
    var searchSuggestions by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isPlayerSheetVisible by remember { mutableStateOf(false) }

    // Real-time search recommendation autocomplete as user types.
    // Personal suggestions (his past searches and played songs matching what he
    // typed, 2026-11-18 ask) lead; the general YouTube feed fills the rest.
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            kotlinx.coroutines.delay(220) // Debounce typing
            val lower = q.lowercase()
            val fromSearches = library.recentSearches.value
                .filter { it.lowercase().contains(lower) }
                .take(3)
            val fromPlays = library.recent.value
                .filter { it.title.lowercase().contains(lower) || it.artist.lowercase().contains(lower) }
                .take(3)
                .map { song -> listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" ") }
            val personal = (fromSearches + fromPlays).distinctBy { it.lowercase() }
            val general = try {
                extractor.getSearchSuggestions(q)
                    .filter { g -> personal.none { it.equals(g, ignoreCase = true) } }
            } catch (e: Exception) {
                emptyList()
            }
            searchSuggestions = personal.map { it to true } + general.map { it to false }
            showSuggestions = searchSuggestions.isNotEmpty()
        } else {
            searchSuggestions = emptyList()
            showSuggestions = false
        }
    }

    // Collect reactive state flows
    val downloadedSongs by offlineStorage.downloadedSongs.collectAsState()
    val downloadProgressMap by downloadManager.downloadProgressMap.collectAsState()
    val downloadErrors by downloadManager.downloadErrors.collectAsState()
    val downloadTransfers by downloadManager.downloadTransfers.collectAsState()
    val currentSong by playerController.currentSong.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val isBuffering by playerController.isBuffering.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    val duration by playerController.duration.collectAsState()
    val playerError by playerController.errorMessage.collectAsState()
    val likedSongs by library.liked.collectAsState()
    val recentSongs by library.recent.collectAsState()
    val playedKeys by library.playedKeys.collectAsState()
    val userPlaylists by library.playlists.collectAsState()
    val artistSkips by library.artistSkips.collectAsState()
    var librarySection by remember { mutableStateOf(0) } // 0 downloads, 1 liked, 2 playlists, 3 recent
    val queueList by playerController.queue.collectAsState()
    val queueIdx by playerController.queueIndex.collectAsState()
    val shuffleOn by playerController.shuffle.collectAsState()
    val repeatMode by playerController.repeat.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    // "More from artist" picks for the Now Playing sheet, cached per artist.
    var moreFromArtist by remember { mutableStateOf<List<com.contentagent.vibestream.data.model.Song>>(emptyList()) }
    val moreFromArtistCache = remember { mutableMapOf<String, List<com.contentagent.vibestream.data.model.Song>>() }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var songActions by remember { mutableStateOf<com.contentagent.vibestream.ui.components.SongActionRequest?>(null) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val tasteStore = remember { com.contentagent.vibestream.data.local.TasteStore(appCtx) }
    var musicOnly by remember { mutableStateOf(tasteStore.getMusicOnly()) }
    var resultFilter by remember { mutableStateOf("all") }

    // Lyrics / sleep timer / equalizer state
    val lyricsRepo = remember { LyricsRepository(appCtx) }
    val eqManager = remember { EqualizerManager(appCtx) }
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsResult by remember { mutableStateOf<LyricsRepository.Lyrics?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showListeningSync by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    val sleepEndAt by playerController.sleepEndAtMs.collectAsState()
    val deviceVolume by playerController.deviceVolume.collectAsState()
    val maxDeviceVolume by playerController.maxDeviceVolume.collectAsState()
    val tapToSoundMs by playerController.tapToSoundMs.collectAsState()
    val rebufferCount by playerController.rebufferCount.collectAsState()
    val liveQueueStatus by playerController.liveQueueStatus.collectAsState()
    val playbackStats = listOfNotNull(
        liveQueueStatus.takeIf { it.isNotBlank() },
        tapToSoundMs?.let { "Started in ${"%.1f".format(it / 1000f)} s" },
        if (rebufferCount > 0) "Stalls: $rebufferCount" else null
    ).joinToString("  ·  ")
    val sleepEndOfTrack by playerController.sleepEndOfTrack.collectAsState()
    // Attach the EQ to the player audio session once playback exists.
    LaunchedEffect(isPlaying) { if (isPlaying) eqManager.ensure() }
    LaunchedEffect(currentSong?.id, showLyrics) {
        val s = currentSong
        if (showLyrics && s != null) {
            lyricsLoading = true
            lyricsResult = lyricsRepo.get(s)
            lyricsLoading = false
        }
    }

    // Library backup (export/import via the system file picker)
    val backup = remember { LibraryBackup(appCtx, library) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val ok = backup.exportTo(uri)
            android.widget.Toast.makeText(
                appCtx, if (ok) "Library backed up" else "Backup failed",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) coroutineScope.launch {
            val ok = backup.importFrom(uri)
            android.widget.Toast.makeText(
                appCtx, if (ok) "Library restored" else "Import failed",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Loading selection is immediate; record history only once audio plays.
    LaunchedEffect(currentSong?.id, isPlaying, isBuffering) {
        if (isPlaying && !isBuffering) currentSong?.let { library.addRecent(it) }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        library.recordSearch(query)
        focusManager.clearFocus()
        showSuggestions = false
        isSearching = true
        searchError = null
        resultFilter = "all"

        coroutineScope.launch {
            try {
                val sec = extractor.searchSectioned(query, musicOnly)
                searchSections = sec
                sectionChip = "all"
                openedEntry = null
                val results = sec.playable
                searchResults = results
                if (results.isEmpty()) {
                    searchError = "No songs found for '$query'. Try another search term."
                } else {
                    // Warm the top results so tapping one starts immediately.
                    playerController.prefetchVisible(results.take(6), maxCount = 6)
                    // Preload the first artworks so rows paint with their art, not after it.
                    results.take(8).forEach { s ->
                        val art = s.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${s.id}/hqdefault.jpg" }
                        appCtx.imageLoader.enqueue(coil.request.ImageRequest.Builder(appCtx).data(art).build())
                    }
                }
            } catch (e: Exception) {
                searchError = "Search error: ${e.localizedMessage}"
            } finally {
                isSearching = false
            }
        }
    }

    fun openEntryHandler(entry: SearchEntry) {
        openedEntry = entry
        entryLoading = true
        openedAlbumTracks = emptyList()
        openedArtistPage = null
        coroutineScope.launch {
            try {
                when (entry.kind) {
                    "artist" -> openedArtistPage = extractor.browseArtist(entry.id)
                    else -> openedAlbumTracks = extractor.browseAlbumTracks(entry.id)
                }
            } catch (e: Exception) {
                // The page still opens with its header if the browse fails.
            }
            entryLoading = false
        }
    }

    val quickGenres = listOf("Trending", "Lo-Fi Hip Hop", "Synthwave", "Rock Anthems", "Chill Acoustic", "EDM Hits")

    CompositionLocalProvider(com.contentagent.vibestream.ui.components.LocalSongActions provides { songActions = it }) {
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
                    onClick = { isPlayerSheetVisible = true },
                    onNext = {
                        playerController.next()
                    },
                    onPrevious = { playerController.previous() }
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
                        onClick = { selectedTab = idx; openShelf = null },
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

                IconButton(onClick = { showListeningSync = true }) {
                    Icon(Icons.Default.Cloud, "Listening sync", tint = if (com.contentagent.vibestream.data.local.ListeningSync.connected()) AmberOrange else TextSecondary)
                }
                if (currentSong != null) {
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(Icons.Default.Bedtime, "Sleep timer", tint = if (sleepEndAt != null || sleepEndOfTrack) AmberOrange else TextSecondary)
                    }
                }
                if (selectedTab == 2 && ytmSignedIn) {
                    com.contentagent.vibestream.ui.components.YouTubeAccountMenu(onSignOut = {
                        val tokenToRevoke = YtMusicAuth.refreshTokenOrNull()
                        YtMusicAuth.signOut()
                        extractor.clearSearchCaches()
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            tokenToRevoke?.let { com.contentagent.vibestream.data.auth.GoogleDeviceAuth.revoke(it) }
                        }
                        ytmSignedIn = false; ytmPlaylists = emptyList()
                        ytmOpened = null; ytmOpenedTracks = emptyList()
                        if (librarySection == 4) librarySection = 0
                        extractor.authRejected = false
                        clearShelfMemory()
                    })
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


            if (showYtmSignIn) {
                YtMusicSignInScreen(onDone = {
                    showYtmSignIn = false
                    extractor.clearSearchCaches()
                    clearShelfMemory()
                    ytmSignedIn = YtMusicAuth.isSignedIn()
                    extractor.authRejected = false
                })
            } else if (openShelf != null) {
                val (shelfTitle, shelfQuery) = openShelf!!
                ShelfScreen(
                    title = shelfTitle,
                    query = shelfQuery,
                    extractor = extractor,
                    musicOnly = musicOnly,
                    currentSongId = currentSong?.id,
                    known = playedKeys,
                    onPlay = { sg, ctx2 -> playerController.playSong(sg, ctx2, discovery = true) },
                    onBack = { openShelf = null }
                )
            } else if (selectedTab == 1) {
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

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = musicOnly,
                            onClick = { musicOnly = true; tasteStore.setMusicOnly(true) },
                            label = { Text("Music", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary,
                                selectedContainerColor = AmberOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                        FilterChip(
                            selected = !musicOnly,
                            onClick = { musicOnly = false; tasteStore.setMusicOnly(false) },
                            label = { Text("Podcasts & more", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextSecondary,
                                selectedContainerColor = AmberOrange,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }

                    // Real-time Search Suggestions / Autocomplete Recommendations.
                    // While suggesting, the list owns the space above the mini player
                    // and scrolls, so every row stays tappable with the keyboard open
                    // (2026-11-18 layout fix); the rest of the tab returns on search.
                    if (showSuggestions && searchSuggestions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                                for ((suggestion, isPersonal) in searchSuggestions.take(6)) {
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
                                            imageVector = if (isPersonal) Icons.Default.History else Icons.Default.Search,
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
                    } else {

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
                          val open = openedEntry
                          Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            when {
                              open != null && open.kind == "artist" -> {
                                ArtistPageView(
                                  page = openedArtistPage,
                                  loading = entryLoading,
                                  currentSongId = currentSong?.id,
                                  isPlaying = isPlaying,
                                  isDownloaded = { offlineStorage.isDownloaded(it) },
                                  isDownloading = { downloadManager.isDownloading(it) },
                                  downloadProgress = { downloadProgressMap[it] ?: 0f },
                                  onPlay = { sg, ctx -> playerController.playSong(if (offlineStorage.isDownloaded(sg.id)) (offlineStorage.getLocalSong(sg.id) ?: sg) else sg, ctx) },
                                  onPlayAll = { list -> list.firstOrNull()?.let { playerController.playSong(it, list) } },
                                  onDownload = { sg -> coroutineScope.launch { downloadManager.downloadTrack(sg) } },
                                  onOpenEntry = { openEntryHandler(it) },
                                  onBack = { openedEntry = null }
                                )
                              }
                              open != null -> {
                                AlbumPageView(
                                  entry = open,
                                  tracks = openedAlbumTracks,
                                  loading = entryLoading,
                                  currentSongId = currentSong?.id,
                                  isPlaying = isPlaying,
                                  isDownloaded = { offlineStorage.isDownloaded(it) },
                                  isDownloading = { downloadManager.isDownloading(it) },
                                  downloadProgress = { downloadProgressMap[it] ?: 0f },
                                  onPlay = { sg, ctx -> playerController.playSong(if (offlineStorage.isDownloaded(sg.id)) (offlineStorage.getLocalSong(sg.id) ?: sg) else sg, ctx) },
                                  onPlayAll = { list -> list.firstOrNull()?.let { playerController.playSong(it, list) } },
                                  onShuffle = { list -> if (list.isNotEmpty()) { val sh = list.shuffled(); playerController.playSong(sh.first(), sh) } },
                                  onDownload = { sg -> coroutineScope.launch { downloadManager.downloadTrack(sg) } },
                                  onBack = { openedEntry = null }
                                )
                              }
                              else -> {
                                val sec = searchSections
                                if (sec != null) {
                                  SectionedSearchResults(
                                    sections = sec,
                                    activeChip = sectionChip,
                                    onChip = { sectionChip = it },
                                    currentSongId = currentSong?.id,
                                    isPlaying = isPlaying,
                                    isDownloaded = { offlineStorage.isDownloaded(it) },
                                    isDownloading = { downloadManager.isDownloading(it) },
                                    downloadProgress = { downloadProgressMap[it] ?: 0f },
                                    onPlay = { sg, ctx -> playerController.playSong(if (offlineStorage.isDownloaded(sg.id)) (offlineStorage.getLocalSong(sg.id) ?: sg) else sg, ctx) },
                                    onDownload = { sg -> coroutineScope.launch { downloadManager.downloadTrack(sg) } },
                                    onOpenEntry = { openEntryHandler(it) }
                                  )
                                }
                              }
                            }
                          }
                        }
                    }
                    }
                }
            } else if (selectedTab == 2) {
              Column(modifier = Modifier.fillMaxSize()) {
                if (!ytmSignedIn) {
                    TextButton(onClick = { showYtmSignIn = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in to YouTube Music")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val names = listOf("Downloads", "Liked (${likedSongs.size})", "Playlists", "Recent") +
                        (if (ytmSignedIn) listOf("YT Music") else emptyList())
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
                    IconButton(onClick = { exportLauncher.launch("vibestream-library.json") }) {
                        Icon(Icons.Default.Upload, contentDescription = "Back up library", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Restore library backup", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                if (librarySection == 4 && ytmSignedIn) {
                    Box(modifier = Modifier.weight(1f)) {
                        YtmLibrarySection(
                            playlists = ytmPlaylists,
                            opened = ytmOpened,
                            openedTracks = ytmOpenedTracks,
                            loading = ytmLoading,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            isDownloaded = { offlineStorage.isDownloaded(it) },
                            isDownloading = { downloadManager.isDownloading(it) },
                            downloadProgress = { downloadProgressMap[it] ?: 0f },
                            onOpen = openYtmList,
                            onBack = { ytmOpened = null },
                            onPlay = { sg, ctx -> playerController.playSong(if (offlineStorage.isDownloaded(sg.id)) (offlineStorage.getLocalSong(sg.id) ?: sg) else sg, ctx) },
                            onDownload = { sg -> coroutineScope.launch { downloadManager.downloadTrack(sg) } }
                        )
                    }
                } else if (librarySection != 0) {
                    Box(modifier = Modifier.weight(1f)) {
                        LibrarySection(
                            section = librarySection,
                            library = library,
                            currentSongId = currentSong?.id,
                            downloadedIds = downloadedSongs.map { it.id }.toSet(),
                            downloadProgress = downloadProgressMap,
                            downloadErrors = downloadErrors,
                            downloadTransfers = downloadTransfers,
                            onDownload = { downloadManager.enqueueTracks(it) },
                            onPlay = { sg, ctx -> playerController.playSong(offlineStorage.getLocalSong(sg.id) ?: sg, ctx) }
                        )
                    }
                } else
                // DOWNLOADS / OFFLINE TAB
                DownloadsPane(
                    saved = downloadedSongs,
                    transfers = downloadTransfers.values.toList(),
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    onPlay = { playerController.playSong(it, downloadedSongs) },
                    onRetry = { downloadManager.enqueueTracks(listOf(it)) },
                    onDelete = { offlineStorage.removeSong(it.id) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
              }
            } else {
                HomeShelves(
                    recent = recentSongs,
                    playedKeys = playedKeys,
                    liked = likedSongs,
                    playlists = userPlaylists,
                    musicOnly = musicOnly,
                    onModeChange = { musicOnly = it; tasteStore.setMusicOnly(it) },
                    onEditTaste = onEditTaste,
                    onPlayMood = { q ->
                        playerController.setShuffle(false)
                        playerController.playSong(q.first(), q, discovery = true)
                    },
                    extractor = extractor,
                    currentSongId = currentSong?.id,
                    onPlay = { sg, ctx -> playerController.playSong(sg, ctx, discovery = true) },
                    onSeeAll = { t, q -> openShelf = t to q },
                    skippedArtists = artistSkips.filterValues { it >= 2 }.keys,
                    onSongsVisible = { songs -> playerController.prefetchVisible(songs.take(4)) }
                )
            }
        }
    }

    // Expandable Full Player Sheet
    // Fetch related songs when the playing track's artist changes.
    LaunchedEffect(currentSong?.artist) {
        val artist = currentSong?.artist.orEmpty()
        if (artist.isBlank() || artist == "Unknown Artist") {
            moreFromArtist = emptyList()
        } else {
            val cached = moreFromArtistCache[artist]
            if (cached != null) {
                moreFromArtist = cached.filter { it.id != currentSong?.id }
            } else {
                moreFromArtist = emptyList()
                val found = try {
                    extractor.searchSongs(artist, true).filter { it.kind == "song" }.take(10)
                } catch (e: Exception) { emptyList() }
                if (found.isNotEmpty()) {
                    moreFromArtistCache[artist] = found
                    if (currentSong?.artist == artist) {
                        moreFromArtist = found.filter { it.id != currentSong?.id }
                    }
                }
            }
        }
    }
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
            chapters = playerController.chapters.collectAsState().value,
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
            onNext = {
                playerController.next()
            },
            onPrevious = { playerController.previous() },
            onQueue = { showQueue = true },
            isLiked = likedSongs.any { it.id == song.id },
            onToggleLike = { library.toggleLike(song); PlaybackService.refreshButtons?.invoke() },
            onAddToPlaylist = { addToPlaylistSong = song },
            moreFromArtist = moreFromArtist,
            onPlayArtistSong = { sg, ctx -> playerController.playSong(sg, ctx) },
            onDismiss = { isPlayerSheetVisible = false },
            showLyrics = showLyrics,
            onToggleLyrics = { showLyrics = !showLyrics },
            onSleepTimer = { showSleepDialog = true },
            onEqualizer = { eqManager.ensure(); showEqDialog = true },
            deviceVolume = deviceVolume,
            maxDeviceVolume = maxDeviceVolume,
            onDeviceVolumeChange = { playerController.setDeviceVolumeFraction(it) },
            onVolumeUp = { playerController.volumeUp() },
            onVolumeDown = { playerController.volumeDown() },
            sleepTimerActive = sleepEndAt != null || sleepEndOfTrack,
            playbackStats = playbackStats,
            lyricsContent = {
                LyricsView(loading = lyricsLoading, lyrics = lyricsResult, positionMs = currentPosition)
            }
        )
    }
    if (showQueue) {
        QueueDialog(queue = queueList, index = queueIdx, onPick = { playerController.playAt(it) }, onDismiss = { showQueue = false }, status = liveQueueStatus)
    }
    addToPlaylistSong?.let { sg ->
        AddToPlaylistDialog(library = library, song = sg, onDismiss = { addToPlaylistSong = null })
    }
    if (showSleepDialog) {
        SleepTimerDialog(
            endAtMs = sleepEndAt,
            endOfTrack = sleepEndOfTrack,
            onPickMinutes = { playerController.setSleepTimerMinutes(it) },
            onPickEndOfTrack = { playerController.setSleepEndOfTrack() },
            onCancelTimer = { playerController.cancelSleepTimer() },
            onDismiss = { showSleepDialog = false }
        )
    }
    if (showListeningSync) { com.contentagent.vibestream.ui.components.ListeningSyncDialog(onDismiss = { showListeningSync = false }) }
    if (showEqDialog) {
        EqualizerDialog(eq = eqManager, onDismiss = { showEqDialog = false })
    }
    songActions?.let { request ->
        val s = request.song
        com.contentagent.vibestream.ui.components.SongActionsSheet(
            song = s, isLiked = likedSongs.any { it.id == s.id },
            isDownloaded = offlineStorage.isDownloaded(s.id), isDownloading = downloadManager.isDownloading(s.id),
            onLike = { library.toggleLike(s); PlaybackService.refreshButtons?.invoke(); songActions = null },
            onSave = { addToPlaylistSong = s; songActions = null },
            onDownload = { downloadManager.enqueueTracks(listOf(s)); songActions = null },
            onRemove = request.onRemove?.let { remove -> { songActions = null; remove() } },
            onDismiss = { songActions = null },
            onNotForMe = { library.notForMe(s); songActions = null
                android.widget.Toast.makeText(appCtx, "Won't suggest this again", android.widget.Toast.LENGTH_SHORT).show() }
        )
    }

    } // Song actions provider
}

/** Signed-in YouTube Music library: Liked songs and the user's playlists. */
@Composable
private fun YtmLibrarySection(
    playlists: List<SearchEntry>,
    opened: SearchEntry?,
    openedTracks: List<Song>,
    loading: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    downloadProgress: (String) -> Float,
    onOpen: (SearchEntry) -> Unit,
    onBack: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onDownload: (Song) -> Unit
) {
    if (opened != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Text(
                    opened.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Loading from YouTube Music…", color = TextSecondary, fontSize = 13.sp)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(openedTracks, key = { it.id }) { sg ->
                    val isCurrent = currentSongId == sg.id
                    SongCard(
                        song = sg,
                        isCurrentSong = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isDownloading = isDownloading(sg.id),
                        downloadProgress = downloadProgress(sg.id),
                        isDownloaded = isDownloaded(sg.id),
                        onPlayClick = { onPlay(sg, openedTracks) },
                        onDownloadClick = { onDownload(sg) }
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Loading your YouTube Music library…", color = TextSecondary, fontSize = 13.sp)
                }
            } else if (playlists.isEmpty()) {
                Text(
                    "No YouTube Music playlists found on this account.",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(playlists, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = entry.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (entry.id == "VLLM") Icons.Default.Favorite else Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = AmberOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    entry.subtitle,
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
