package com.contentagent.vibestream.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Controller orchestrating Media3 ExoPlayer for both direct YouTube streaming
 * and offline local file playback.
 */
class MusicPlayerController(
    private val context: Context,
    private val extractor: YouTubeExtractor,
    private val queueStore: com.contentagent.vibestream.data.local.QueueStore? = null
) {

    private val compilationCache = com.contentagent.vibestream.data.analysis.CompilationCache(context)
    private val _analysisEnabled = MutableStateFlow(compilationCache.enabled)
    val analysisEnabled = _analysisEnabled.asStateFlow()
    private val _analysisStatus = MutableStateFlow("")
    val analysisStatus = _analysisStatus.asStateFlow()
    private var analysisJob: Job? = null
    private val attemptedAnalysis = mutableSetOf<String>()
    fun setAutomaticAnalysis(enabled: Boolean) {
        compilationCache.enabled = enabled
        _analysisEnabled.value = enabled
        if (!enabled) { analysisJob?.cancel(); _analysisStatus.value = "Automatic naming off" }
        else { attemptedAnalysis.clear(); _currentSong.value?.let { maybeAnalyze(it) } }
    }
    private fun attachChapters(song: Song, points: List<com.contentagent.vibestream.data.chapters.SongChapter>) {
        val player = controller ?: return
        if (player.currentMediaItem?.mediaId != song.id) return
        val enriched = song.copy(chapters = points)
        _queue.value = _queue.value.map { if (it.id == song.id) enriched else it }
        _currentSong.value = enriched
        val old = player.currentMediaItem ?: return
        player.replaceMediaItem(player.currentMediaItemIndex, old.buildUpon().setMediaMetadata(old.mediaMetadata.buildUpon()
            .setExtras(ChapterNavigation.extras(points)).build()).build())
        persistQueue()
    }
    private fun maybeAnalyze(song: Song) {
        if (song.chapters.orEmpty().size >= 2) return
        compilationCache.get(song.id)?.let { attachChapters(song,it); return }
        if (!_analysisEnabled.value || song.id in attemptedAnalysis) return
        val length = (controller?.duration ?: 0L).takeIf { it > 0 } ?: song.durationSec * 1000
        if (length !in 600000..10800000) return
        val source = song.playableUri ?: controller?.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        attemptedAnalysis.add(song.id)
        analysisJob?.cancel()
        _analysisStatus.value = "Naming songs automatically..."
        analysisJob = playerScope.launch {
            val ownerJob = coroutineContext[Job]!!
            try {
                val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    com.contentagent.vibestream.data.analysis.CompilationIndexer().index(source,length,
                        cancelled = { !ownerJob.isActive }, progress = { count,total ->
                            _analysisStatus.value = "Naming songs: $count checks (about $total initial windows)"
                        })
                }
                compilationCache.save(song.id,result.chapters)
                attachChapters(song,result.chapters)
                _analysisStatus.value = "Automatic naming finished. Boundaries are approximate; misses stay Unknown."
            } catch (_:kotlinx.coroutines.CancellationException) { throw kotlinx.coroutines.CancellationException() }
            catch (e:Exception) { _analysisStatus.value = "Naming unavailable: ${e.message?.take(90)}. No guessed names." }
        }
    }

    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null

    // The service owns the sole player. Reconnecting an Activity never creates a second player.
    private val controllerFuture = MediaController.Builder(
        context, SessionToken(context, ComponentName(context, PlaybackService::class.java))
    ).buildAsync()
    private var controller: MediaController? = null

    private suspend fun awaitController(): MediaController = suspendCancellableCoroutine { continuation ->
        controller?.let { continuation.resume(it); return@suspendCancellableCoroutine }
        controllerFuture.addListener({
            try {
                val connected = controllerFuture.get()
                controller = connected
                if (continuation.isActive) continuation.resume(connected)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.cancel(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- Play queue ----
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(0) // 0 off, 1 all, 2 one
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    // ---- Sleep timer (fades volume out over the last 15s, then pauses) ----
    private var sleepJob: Job? = null
    private val _sleepEndAtMs = MutableStateFlow<Long?>(null)
    val sleepEndAtMs: StateFlow<Long?> = _sleepEndAtMs.asStateFlow()
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack.asStateFlow()

    fun setSleepTimerMinutes(minutes: Int) {
        cancelSleepTimer()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepEndAtMs.value = endAt
        sleepJob = playerScope.launch {
            while (isActive) {
                val remaining = endAt - System.currentTimeMillis()
                if (remaining <= 0) break
                if (remaining <= 15_000L) {
                    controller?.volume = (remaining / 15_000f).coerceIn(0f, 1f)
                }
                delay(250)
            }
            controller?.pause()
            controller?.volume = 1f
            _sleepEndAtMs.value = null
        }
    }

    fun setSleepEndOfTrack() {
        cancelSleepTimer()
        _sleepEndOfTrack.value = true
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepEndAtMs.value = null
        _sleepEndOfTrack.value = false
        controller?.volume = 1f
    }

    // ---- Queue persistence ----
    private var pendingResumeMs: Long? = null

    private fun persistQueue() {
        queueStore?.save(
            _queue.value, _queueIndex.value,
            controller?.currentPosition ?: 0L,
            _shuffle.value, _repeat.value
        )
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        shufflePlayed.clear()
        refreshUpcomingTrack()
        persistQueue()
    }
    fun setShuffle(on: Boolean) {
        _shuffle.value = on
        shufflePlayed.clear()
        refreshUpcomingTrack()
        persistQueue()
    }
    fun cycleRepeat() {
        _repeat.value = (_repeat.value + 1) % 3
        refreshUpcomingTrack()
        persistQueue()
    }

    private fun refreshUpcomingTrack() {
        prefetchJob?.cancel()
        plannedShuffleNext = -1
        controller?.let { c ->
            val first = c.currentMediaItemIndex + 1
            if (first >= 0 && first < c.mediaItemCount) c.removeMediaItems(first, c.mediaItemCount)
        }
        scheduleNextPrefetch()
    }

    fun playAt(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        _queueIndex.value = index
        persistQueue()
        startPlayback(q[index])
    }

    // Smart shuffle: tracks already played in this shuffle pass are not repeated
    // until the queue has cycled, and consecutive songs avoid the same artist when
    // the queue has alternatives (artist spread, like Spotify's smart shuffle feel).
    private val shufflePlayed = mutableListOf<Int>()

    // Pure pick so the gapless prefetch can plan the shuffle target without
    // mutating the pass history; the mutation happens when the track is left.
    private fun smartPick(q: List<Song>, current: Int, played: List<Int>): Int {
        var pool = q.indices.filter { it != current && it !in played }
        if (pool.isEmpty()) pool = q.indices.filter { it != current }
        if (pool.isEmpty()) return -1
        val currentArtist = q.getOrNull(current)?.artist.orEmpty()
        val prevArtist = q.getOrNull(played.dropLast(1).lastOrNull() ?: -1)?.artist.orEmpty()
        // Prefer a different artist than the last two played.
        val spread = pool.filter { q[it].artist != currentArtist && q[it].artist != prevArtist }
        return (if (spread.isNotEmpty()) spread else pool).random()
    }

    // Planned shuffle target: computed once per track so the prefetched next item
    // and an actual skip agree on where "next" is.
    private var plannedShuffleNext: Int = -1

    private fun computeNextIndex(q: List<Song>, i: Int): Int = when {
        _repeat.value == 2 -> -1
        _shuffle.value && q.size > 1 -> {
            if (plannedShuffleNext !in q.indices || plannedShuffleNext == i) {
                plannedShuffleNext = smartPick(q, i, if (i >= 0) shufflePlayed + i else shufflePlayed)
            }
            plannedShuffleNext
        }
        i + 1 < q.size -> i + 1
        _repeat.value == 1 -> 0
        else -> -1
    }

    fun next() {
        controller?.let { c ->
            ChapterNavigation.next(c.currentMediaItem, c.currentPosition)?.let { seekTo(it); return }
        }
        val q = _queue.value
        if (q.isEmpty()) return
        val i = _queueIndex.value
        if (_repeat.value == 2) { playAt(i.coerceAtLeast(0)); return }
        val target = computeNextIndex(q, i)
        if (_shuffle.value && i >= 0 && i != target && i !in shufflePlayed) shufflePlayed.add(i)
        plannedShuffleNext = -1
        if (target < 0) return
        // Gapless path: ExoPlayer already holds the prefetched target as its next
        // item, so seek inside the playlist instead of re-resolving.
        val c = controller
        if (c != null && c.hasNextMediaItem() &&
            c.getMediaItemAt(c.currentMediaItemIndex + 1).mediaId == q[target].id) {
            c.seekToNextMediaItem()
            return
        }
        playAt(target)
    }

    fun previous() {
        controller?.let { c ->
            ChapterNavigation.previous(c.currentMediaItem, c.currentPosition)?.let { seekTo(it); return }
        }
        val q = _queue.value
        if (q.isEmpty()) return
        if ((controller?.currentPosition ?: 0L) > 3000L) { seekTo(0); return }
        // Played history lives in the player playlist (kept by the transition
        // listener), so "previous" is an instant native seek when possible.
        controller?.let { c ->
            if (c.currentMediaItemIndex > 0) { c.seekToPreviousMediaItem(); return }
        }
        val i = _queueIndex.value
        val target = when {
            i - 1 >= 0 -> i - 1
            _repeat.value == 1 -> q.size - 1
            else -> 0
        }
        playAt(target)
    }

    private val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        val d = controller?.duration ?: 0L
                        if (d > 0) _duration.value = d
                        _currentSong.value?.let { maybeAnalyze(it) }
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        _isPlaying.value = false
                        stopProgressTracker()
                        if (_sleepEndOfTrack.value) {
                            _sleepEndOfTrack.value = false
                        } else {
                            next()
                        }
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val q = _queue.value
                val idx = q.indexOfFirst { it.id == mediaItem?.mediaId }
                if (idx >= 0) {
                    val old = _queueIndex.value
                    if (_shuffle.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        old >= 0 && old != idx && old !in shufflePlayed) {
                        shufflePlayed.add(old)
                    }
                    _queueIndex.value = idx
                    _currentSong.value = q[idx]
                    if (old != idx) {
                        pendingCandidates.clear()
                        prefetchAlts.remove(q[idx].id)?.let { pendingCandidates.addAll(it) }
                    }
                } else {
                    restoreCurrentSong()
                }
                plannedShuffleNext = -1
                if (_sleepEndOfTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    _sleepEndOfTrack.value = false
                    controller?.pause()
                }
                persistQueue()
                // Drop already-played items so the player playlist stays compact.
                controller?.let { c ->
                    // Keep recent played items so notification/lock-screen Previous works.
                    while (c.mediaItemCount > 1 && c.currentMediaItemIndex > 10) c.removeMediaItem(0)
                }
                _currentSong.value?.let { ensureChapters(it) }
                scheduleNextPrefetch()
            }

            override fun onPlayerError(error: PlaybackException) {
                val song0 = _currentSong.value
                if (pendingCandidates.isEmpty() && song0 != null) {
                    prefetchAlts.remove(song0.id)?.let { pendingCandidates.addAll(it) }
                }
                val next = pendingCandidates.removeFirstOrNull()
                val song = song0
                if (next != null && song != null) {
                    lastPlayError = describe(error)
                    playerScope.launch {
                        controller?.let { c ->
                            playbackGeneration++
                            c.setMediaItem(buildItem(song, Uri.parse(next)))
                            c.prepare()
                            c.play()
                            scheduleNextPrefetch()
                        }
                    }
                    return
                }
                _isBuffering.value = false
                _isPlaying.value = false
                _errorMessage.value = "Playback error: ${error.localizedMessage}\n${describe(error)}"
            }
        }

    init {
        playerScope.launch {
            try {
                awaitController().addListener(listener)
                PlaybackService.nextHandler = { next() }
                PlaybackService.previousHandler = { previous() }
                restoreCurrentSong()
                if (_currentSong.value == null) {
                    // Cold start: bring back the last listening session without
                    // starting playback. Play/next resolves the stream on demand.
                    queueStore?.load()?.let { saved ->
                        if (saved.index in saved.songs.indices) {
                            _queue.value = saved.songs
                            _queueIndex.value = saved.index
                            _currentSong.value = saved.songs[saved.index]
                            _shuffle.value = saved.shuffle
                            _repeat.value = saved.repeat
                            pendingResumeMs = saved.positionMs
                        }
                    }
                }
                _isPlaying.value = controller?.isPlaying == true
                if (_isPlaying.value) startProgressTracker()
            } catch (e: Exception) {
                _errorMessage.value = "Unable to connect to playback service: ${e.localizedMessage}"
            }
        }
    }

    private fun restoreCurrentSong() {
        val item = controller?.currentMediaItem
        if (item == null) {
            _currentSong.value = null
            return
        }
        val metadata = item.mediaMetadata
        val id = item.mediaId
        if (id.isBlank()) { _currentSong.value = null; return }
        val uri = item.localConfiguration?.uri
        val localPath = if (uri?.scheme == "file") uri.path else null
        _currentSong.value = Song(
            id = id,
            title = metadata.title?.toString() ?: "Unknown title",
            artist = metadata.artist?.toString() ?: "Unknown artist",
            thumbnailUrl = metadata.artworkUri?.toString() ?: "",
            streamUrl = if (localPath == null) uri?.toString() else null,
            localFilePath = localPath,
            chapters = ChapterNavigation.chapters(item).takeIf { it.isNotEmpty() },
            isDownloaded = localPath != null
        )
    }

    /**
     * Plays a song:
     * - If already downloaded, plays directly from device storage (file://).
     * - If not downloaded, resolves direct audio stream URL and streams instantly.
     */
    private val pendingCandidates = ArrayDeque<String>()
    private var lastPlayError: String = ""

    // ---- Gapless next-track pipeline ----
    // Bumped on every fresh playback start; a stale prefetch job checks it before
    // touching the player so it can never append an out-of-date track.
    private var playbackGeneration = 0
    private var prefetchJob: Job? = null
    // Alternate stream URLs per song id, kept for the onPlayerError fallback when a
    // prefetched item turns out to be a dead link at play time.
    private val prefetchAlts = mutableMapOf<String, List<String>>()

    /**
     * Resolves the upcoming track's stream in the background and appends it to the
     * ExoPlayer playlist while the current song is still playing. ExoPlayer then
     * prebuffers and auto-advances with no audible gap, instead of starting the
     * network resolve only after STATE_ENDED.
     */
    private fun scheduleNextPrefetch() {
        prefetchJob?.cancel()
        val q = _queue.value
        val i = _queueIndex.value
        if (i !in q.indices) return
        val gen = playbackGeneration
        val target = computeNextIndex(q, i)
        if (target < 0) return
        val nextSong = q[target]
        prefetchJob = playerScope.launch {
            try {
                val url: String = when {
                    nextSong.isDownloaded && !nextSong.localFilePath.isNullOrEmpty() &&
                        File(nextSong.localFilePath!!).isFile && File(nextSong.localFilePath!!).length() > 0 ->
                        "file://${nextSong.localFilePath}"
                    !nextSong.streamUrl.isNullOrEmpty() -> nextSong.streamUrl!!
                    else -> {
                        val all = extractor.resolveAudioCandidates(nextSong.id)
                        if (all.isEmpty()) return@launch
                        prefetchAlts[nextSong.id] = all.drop(1).take(4)
                        nextSong.streamUrl = all[0]
                        all[0]
                    }
                }
                if (gen != playbackGeneration) return@launch
                val c = controller ?: return@launch
                if (c.currentMediaItem?.mediaId != q[i].id) return@launch
                val hasItNext = c.hasNextMediaItem() &&
                    c.getMediaItemAt(c.currentMediaItemIndex + 1).mediaId == nextSong.id
                if (!hasItNext) {
                    c.addMediaItem(buildItem(nextSong, Uri.parse(url)))
                }
            } catch (e: Exception) {
                // Prefetch is best-effort: the STATE_ENDED -> next() path still
                // resolves and starts the next track if we never appended it.
            }
        }
    }

    private var chapterJob: Job? = null
    private fun ensureChapters(song: Song) {
        chapterJob?.cancel()
        if (song.chapters != null || !song.id.matches(Regex("[A-Za-z0-9_-]{11}")) ||
            (song.durationSec in 1..599)) return
        chapterJob = playerScope.launch {
            val chapters = extractor.resolveChapters(song.id) ?: return@launch
            val player = controller ?: return@launch
            if (player.currentMediaItem?.mediaId != song.id) return@launch
            val enriched = song.copy(chapters = chapters)
            _queue.value = _queue.value.map { if (it.id == song.id) enriched else it }
            _currentSong.value = enriched
            val index = player.currentMediaItemIndex
            val old = player.currentMediaItem ?: return@launch
            player.replaceMediaItem(index, old.buildUpon().setMediaMetadata(old.mediaMetadata.buildUpon()
                .setExtras(ChapterNavigation.extras(chapters)).build()).build())
            persistQueue()
            maybeAnalyze(_currentSong.value ?: song)
        }
    }

    private fun describe(e: PlaybackException): String {
        var c: Throwable? = e.cause
        val parts = mutableListOf(e.errorCodeName)
        while (c != null) {
            val code = (c as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            parts.add(if (code != null) "HTTP $code" else "${c.javaClass.simpleName}: ${c.message}")
            c = c.cause
        }
        return parts.joinToString(" | ").take(220)
    }

    private fun buildItem(song: Song, uri: Uri): MediaItem {
        extractor.cachedChapters(song.id)?.let { song.chapters = it }
        val metadata = MediaMetadata.Builder()
            .setExtras(ChapterNavigation.extras(song.chapters.orEmpty()))
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(if (song.thumbnailUrl.isNotEmpty()) Uri.parse(song.thumbnailUrl) else null)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaId(song.id).setMediaMetadata(metadata).build()
    }

    fun playSong(song: Song, context: List<Song>? = null) {
        shufflePlayed.clear()
        if (context != null && context.any { it.id == song.id }) {
            _queue.value = context
            _queueIndex.value = context.indexOfFirst { it.id == song.id }
        } else {
            _queue.value = listOf(song)
            _queueIndex.value = 0
        }
        persistQueue()
        startPlayback(song)
    }

    private fun startPlayback(song: Song) {
        analysisJob?.cancel()
        _analysisStatus.value = ""
        playbackGeneration++
        val generation = playbackGeneration
        prefetchJob?.cancel()
        // Alternatives belong to this track, never to a previous selection.
        pendingCandidates.clear()
        playerScope.launch {
            _errorMessage.value = null
            _isBuffering.value = true

            val playableUri: Uri? = when {
                // Play locally if file exists on device
                song.isDownloaded && !song.localFilePath.isNullOrEmpty() && File(song.localFilePath!!).isFile && File(song.localFilePath!!).length() > 0 -> {
                    Uri.fromFile(File(song.localFilePath!!))
                }
                // Use already cached stream URL
                !song.streamUrl.isNullOrEmpty() -> {
                    Uri.parse(song.streamUrl)
                }
                // Resolve stream on-the-fly from YouTube
                else -> {
                    val all = extractor.resolveAudioCandidates(song.id)
                    if (generation != playbackGeneration) return@launch
                    pendingCandidates.clear()
                    if (all.isNotEmpty()) {
                        pendingCandidates.addAll(all.drop(1).take(4))
                        Uri.parse(all[0])
                    } else {
                        _isBuffering.value = false
                        _errorMessage.value = "Unable to fetch audio stream: ${extractor.lastResolveError ?: "unknown"}"
                        return@launch
                    }
                }
            }

            if (generation != playbackGeneration) return@launch
            if (playableUri != null) {
                val player = try { awaitController() } catch (e: Exception) {
                    _isBuffering.value = false
                    _errorMessage.value = "Playback service unavailable: ${e.localizedMessage}"
                    return@launch
                }
                if (generation != playbackGeneration) return@launch
                val mediaItem = buildItem(song, playableUri)

                _currentSong.value = song
                player.setMediaItem(mediaItem)
                player.prepare()
                val resume = pendingResumeMs
                pendingResumeMs = null
                if (resume != null && resume > 0) player.seekTo(resume)
                player.play()
                persistQueue()
                ensureChapters(song)
                scheduleNextPrefetch()
            }
        }
    }

    fun togglePlayPause() {
        val c = controller
        if ((c == null || c.mediaItemCount == 0) && _currentSong.value != null) {
            // Restored queue that was never loaded into the player: start it.
            startPlayback(_currentSong.value!!)
            return
        }
        if (c?.isPlaying == true) {
            c.pause()
        } else {
            c?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipForward(seconds: Int = 10) {
        val player = controller ?: return
        val newPos = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration.coerceAtLeast(0))
        seekTo(newPos)
    }

    fun skipBackward(seconds: Int = 10) {
        val player = controller ?: return
        val newPos = (player.currentPosition - seconds * 1000).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = playerScope.launch {
            while (isActive) {
                _currentPosition.value = controller?.currentPosition ?: 0L
                val d = controller?.duration ?: 0L
                if (d > 0) _duration.value = d
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
        queueStore?.savePosition(controller?.currentPosition ?: 0L)
    }

    fun release() {
        stopProgressTracker()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        playerScope.coroutineContext[Job]?.cancel()
    }
}
