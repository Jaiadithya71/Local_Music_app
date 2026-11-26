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
import com.contentagent.vibestream.data.local.StreamUrlStore
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
    private val queueStore: com.contentagent.vibestream.data.local.QueueStore? = null,
    private val streamUrlStore: StreamUrlStore? = null
) {

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

    // ---- Tap-to-sound timer + stall counter (shown in the player sheet) ----
    // tapStartedAtMs is stamped when a tap starts playback; the first
    // onIsPlayingChanged(true) after it completes the measurement.
    private var tapStartedAtMs = 0L
    private var tapMeasured = false
    private val _tapToSoundMs = MutableStateFlow<Long?>(null)
    val tapToSoundMs: StateFlow<Long?> = _tapToSoundMs.asStateFlow()
    // Buffering spells after sound first played (rebuffers and seek pauses).
    private val _rebufferCount = MutableStateFlow(0)
    val rebufferCount: StateFlow<Int> = _rebufferCount.asStateFlow()

    // ---- Play queue ----
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _repeat = MutableStateFlow(0) // 0 off, 1 all, 2 one
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    // Live adaptation is opt-in per discovery playback session. Explicit
    // playlists/search/offline queues retain their order and never get additions.
    private val liveQueue = LiveQueueSession()
    private var adaptationJob: Job? = null
    var knownSongsProvider: () -> Set<String> = { emptySet() }
    var likedSongsProvider: () -> List<Song> = { emptyList() }
    var onEarlySkip: ((Song) -> Unit)? = null
    var isLikedProvider: (String) -> Boolean = { false }

    // One play event per play, logged on the device for the mood engine.
    private val playTracker = com.contentagent.vibestream.data.local.MoodRuntime.newTracker { id -> isLikedProvider(id) }
    // Long recordings: song names and jump points from chapters or description timestamps.
    private val _chapters = MutableStateFlow<List<com.contentagent.vibestream.data.recommendation.Chapter>>(emptyList())
    val chapters: StateFlow<List<com.contentagent.vibestream.data.recommendation.Chapter>> = _chapters.asStateFlow()
    private var chaptersFor: String? = null
    private fun loadChapters(song: Song) {
        if (chaptersFor == song.id) return
        chaptersFor = song.id
        _chapters.value = emptyList()
        if (song.durationSec < 1200L) return
        playerScope.launch {
            val c = try { extractor.chapters(song.id) } catch (e: Exception) { emptyList() }
            if (chaptersFor == song.id) _chapters.value = c
        }
    }

    /** Next/Previous inside a long recording. True when handled. */
    fun chapterNav(forward: Boolean): Boolean {
        val cs = _chapters.value
        if (cs.size < 2) return false
        val pos = controller?.currentPosition ?: _currentPosition.value
        val target = (if (forward) com.contentagent.vibestream.data.recommendation.ChapterPolicy.nextStartMs(cs, pos)
            else com.contentagent.vibestream.data.recommendation.ChapterPolicy.prevStartMs(cs, pos)) ?: return false
        seekTo(target)
        return true
    }

    private fun trackPlayStart(song: Song, auto: Boolean) {
        try {
            loadChapters(song)
            playTracker.begin(song, _currentPosition.value, _duration.value, auto,
                if (liveQueue.active) "home_mix" else "queue", System.currentTimeMillis())
            com.contentagent.vibestream.data.local.MoodRuntime.onSongStart()
        } catch (e: Exception) { }
    }
    private val _liveQueueStatus = MutableStateFlow("")
    val liveQueueStatus: StateFlow<String> = _liveQueueStatus.asStateFlow()

    fun onLikeChanged(song: Song, liked: Boolean) {
        if (liked) adaptDiscovery(song, com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.LIKE)
    }

    /** One path for in-app, notification, lock-screen and headset Next taps. */
    fun onUserNext(positionMs: Long) {
        val song = _currentSong.value ?: return
        if (!liveQueue.acceptEarlySkip(song, positionMs)) return
        onEarlySkip?.invoke(song)
        adaptDiscovery(song, com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.SKIP)
    }

    private fun adaptDiscovery(song: Song, signal: com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal) {
        if (!liveQueue.active) return
        val epoch = liveQueue.epoch
        adaptationJob?.cancel()
        val taste = com.contentagent.vibestream.data.local.TasteStore(context).load()
        fun apply(candidates: List<Song>) {
            if (!liveQueue.active || liveQueue.epoch != epoch) return
            val before = _queue.value
            val index = _queueIndex.value
            val actualId = controller?.currentMediaItem?.mediaId
            if (actualId != null && actualId != before.getOrNull(index)?.id) return
            val revised = liveQueue.revise(
                epoch, before, index, candidates,
                com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Feedback(song, signal),
                knownSongsProvider(), taste.languages, taste.moods,
                com.contentagent.vibestream.data.local.MoodRuntime.rankFn()
            )
            if (revised != before) {
                _queue.value = revised
                shufflePlayed.clear()
                // Only remove/rebuild the prefetched NEXT media item. No pause,
                // seek, setMediaItem or change to the playing item's URI/position.
                refreshUpcomingTrack()
                persistQueue()
            }
        }
        _liveQueueStatus.value = "Updating upcoming discoveries"
        adaptationJob = playerScope.launch {
            // Let an in-flight media-key skip finish before editing its tail.
            kotlinx.coroutines.yield()
            try {
                apply(emptyList())
                val seed = if (signal == com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.SKIP)
                    likedSongsProvider().firstOrNull { it.artist !in liveQueue.avoidedArtists } else song
                val candidates = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                    val related = seed?.let { extractor.relatedSongs(it.id) }.orEmpty()
                    if (related.size >= 5) related else {
                        val query = if (signal == com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.LIKE)
                            "${song.artist} songs"
                        else listOfNotNull(taste.languages.firstOrNull(), taste.moods.firstOrNull(), "new songs").joinToString(" ")
                        (related + extractor.searchSongs(query, true)).distinctBy { it.id }
                    }
                }.orEmpty()
                if (liveQueue.epoch == epoch && liveQueue.active) {
                    apply(candidates)
                    _liveQueueStatus.value = if (candidates.isEmpty()) "Upcoming discoveries updated; no new related tracks available" else "Upcoming discoveries updated"
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (liveQueue.epoch == epoch) _liveQueueStatus.value = "Kept upcoming discoveries; connection unavailable"
            }
        }
    }

    // ---- Sleep timer (fades volume out over the last 15s, then pauses) ----
    private val _sleepEndAtMs = MutableStateFlow<Long?>(null)
    val sleepEndAtMs: StateFlow<Long?> = _sleepEndAtMs.asStateFlow()
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack.asStateFlow()

    private val sleepTimer = SleepTimer(
        scope = playerScope,
        onDeadline = { _sleepEndAtMs.value = it },
        onVolume = { controller?.volume = it },
        onStop = { controller?.pause() }
    )

    fun setSleepTimerMinutes(minutes: Int) {
        cancelSleepTimer()
        sleepTimer.start(minutes)
    }

    fun setSleepEndOfTrack() {
        cancelSleepTimer()
        _sleepEndOfTrack.value = true
        // Remove the prefetched next media item. Otherwise it may start before
        // the transition callback can pause it at the song boundary.
        refreshUpcomingTrack()
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
        _sleepEndOfTrack.value = false
        refreshUpcomingTrack()
    }

    // ---- Device volume (synced with the phone's media volume) ----
    // The in-app slider is the same Android media-stream volume the hardware
    // buttons and the system panel drive: buttons move the slider, the slider
    // moves the buttons' volume, and over a Bluetooth link that honors volume
    // events this is the volume the connected device gets. No separate app-only
    // level, so nothing can silently drift or hide an attenuation.
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val _deviceVolume = MutableStateFlow(0)
    val deviceVolume: StateFlow<Int> = _deviceVolume.asStateFlow()
    private val _maxDeviceVolume = MutableStateFlow(1)
    val maxDeviceVolume: StateFlow<Int> = _maxDeviceVolume.asStateFlow()

    // The MediaController device-volume commands are not available through this
    // app's session (emulator evidence: get/set/adjust all false), so the
    // system -> app direction cannot rely on Player.onDeviceVolumeChanged. This
    // receiver follows the same system broadcast the hardware buttons trigger.
    private val volumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
            if (intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) !=
                android.media.AudioManager.STREAM_MUSIC) return
            _deviceVolume.value = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        }
    }
    private var volumeReceiverRegistered = false

    private fun refreshDeviceVolume() {
        _maxDeviceVolume.value = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _deviceVolume.value = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        if (!volumeReceiverRegistered) {
            volumeReceiverRegistered = true
            context.registerReceiver(
                volumeReceiver,
                android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            )
        }
    }

    // Direct AudioManager on the media stream: this app and its playback service
    // are one process, and routing the slider through the MediaController
    // device-volume commands proved unreliable on the emulator (run 42342161849:
    // no stream change), which would have shipped a dead slider.
    fun setDeviceVolumeFraction(fraction: Float) {
        val target = DeviceVolume.volumeForFraction(fraction, _maxDeviceVolume.value)
        // No UI flag: the app slider is already the visible feedback, and a
        // system panel popping on every drag tick would fight it.
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
        _deviceVolume.value = target
    }

    fun volumeUp() {
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, 0)
        _deviceVolume.value = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, 0)
        _deviceVolume.value = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
    }

    /** Diagnostic snapshot for instrumented tests and bug reports. */
    fun debugDeviceVolumeSupport(): String {
        val c = controller ?: return "no-controller"
        return "get=${c.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME)} " +
            "set=${c.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME)} " +
            "adj=${c.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME)} " +
            "streamVol=${audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)}"
    }

    // ---- Queue persistence ----
    private var pendingResumeMs: Long? = null
    private var playbackJob: Job? = null

    // Read before connecting/listening: a live service must not make us skip the
    // saved queue, or the next transition can overwrite it with an empty list.
    private fun restoreQueueFor(item: MediaItem?) {
        val saved = queueStore?.load()
        val restored = QueueReconnect.reconcile(saved?.songs.orEmpty(), saved?.index ?: -1, item?.mediaId)
        _queue.value = restored.songs
        _queueIndex.value = restored.index
        liveQueue.begin(saved?.discovery == true && restored.index in restored.songs.indices)
        if (liveQueue.active) {
            liveQueue.avoidedArtists.addAll(saved?.avoidedArtists.orEmpty())
            restored.songs.take(restored.index + 1).forEach { liveQueue.notePlaying(it) }
        }
        _liveQueueStatus.value = if (liveQueue.active) "Live discovery queue" else ""
        if (saved != null) {
            _shuffle.value = saved.shuffle
            _repeat.value = saved.repeat
        }
        if (item == null && restored.index in restored.songs.indices) {
            _currentSong.value = restored.songs[restored.index]
            pendingResumeMs = saved?.positionMs
        } else if (item != null && restored.index in restored.songs.indices) {
            _currentSong.value = restored.songs[restored.index]
        } else if (item != null) {
            restoreCurrentSong()
            _currentSong.value?.let { _queue.value = listOf(it); _queueIndex.value = 0 }
        }
    }

    private fun persistQueue() {
        if (_queue.value.isEmpty()) return // Never destroy a known saved session on reconnect.
        queueStore?.save(
            _queue.value, _queueIndex.value,
            if (controller?.currentMediaItem?.mediaId == _queue.value.getOrNull(_queueIndex.value)?.id)
                controller?.currentPosition ?: 0L else 0L,
            _shuffle.value, _repeat.value, liveQueue.active, liveQueue.avoidedArtists
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
        pendingResumeMs = null
        startPlayback(q[index])
        persistQueue()
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
        _sleepEndOfTrack.value -> -1
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

    fun next(userInitiated: Boolean = true) {
        if (userInitiated && chapterNav(true)) return
        if (userInitiated) onUserNext(controller?.currentPosition ?: _currentPosition.value)
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
        if (chapterNav(false)) return
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
            override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                _deviceVolume.value = volume
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    if (!tapMeasured && tapStartedAtMs > 0L) {
                        tapMeasured = true
                        val ms = android.os.SystemClock.elapsedRealtime() - tapStartedAtMs
                        _tapToSoundMs.value = ms
                        android.util.Log.i("VibeStream", "tap-to-sound ${ms}ms id=${_currentSong.value?.id}")
                    }
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _isBuffering.value = true
                        // Only count once sound has played: initial load is the
                        // tap-to-sound wait, later spells are stalls.
                        if (tapMeasured) _rebufferCount.value++
                    }
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        val d = controller?.duration ?: 0L
                        if (d > 0) _duration.value = d
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        _isPlaying.value = false
                        stopProgressTracker()
                        if (_sleepEndOfTrack.value) {
                            _sleepEndOfTrack.value = false
                            controller?.pause()
                        } else {
                            next(userInitiated = false)
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
                    if (old != idx) liveQueue.notePlaying(q[idx])
                    if (old != idx && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) trackPlayStart(q[idx], true)
                    if (old != idx) {
                        recoveryAttempts = 0
                        alternateHops = 0
                        urlRefreshAttempted = false
                        pendingCandidates.clear()
                        prefetchAlts.remove(q[idx].id)?.let { pendingCandidates.addAll(it) }
                    }
                } else {
                    restoreQueueFor(mediaItem)
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
                scheduleNextPrefetch()
                if (liveQueue.active && _queue.value.size - _queueIndex.value - 1 < 5 && adaptationJob?.isActive != true) {
                    _currentSong.value?.let { adaptDiscovery(it, com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.PLAY) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val song0 = _currentSong.value
                if (pendingCandidates.isEmpty() && song0 != null) {
                    prefetchAlts.remove(song0.id)?.let { pendingCandidates.addAll(it) }
                }
                val song = song0
                val resumeMs = controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value
                val wasRequestedToPlay = controller?.playWhenReady == true
                prefetchJob?.cancel()
                val next = pendingCandidates.removeFirstOrNull()
                if (song != null && recoveryAttempts < 5 && (next != null || !urlRefreshAttempted)) {
                    recoveryAttempts++
                    if (next == null) urlRefreshAttempted = true
                    lastPlayError = describe(error)
                    playbackGeneration++
                    val generation = playbackGeneration
                    playerScope.launch {
                        val resolvedFirst = if (next == null) {
                            val all = extractor.resolveAudioCandidates(song.id)
                            if (all.isNotEmpty()) streamUrlStore?.put(song.id, all)
                            pendingCandidates.addAll(all.drop(1).take(4))
                            all.firstOrNull()
                        } else null
                        val url = next ?: resolvedFirst
                        if (generation != playbackGeneration || _currentSong.value?.id != song.id) return@launch
                        if (url == null) {
                            _isBuffering.value = false
                            _errorMessage.value = "Could not refresh audio stream. Retry playback."
                            return@launch
                        }
                        controller?.let { c ->
                            song.streamUrl = url
                            c.setMediaItem(buildItem(song, Uri.parse(url)), resumeMs)
                            c.prepare()
                            c.playWhenReady = wasRequestedToPlay
                            scheduleNextPrefetch()
                        }
                    }
                    return
                }
                // Last resort before showing an error: a rejected or dead upload
                // (HTTP 403, broken link) should not strand the listener. Search
                // for the same song from another uploader and play the first
                // alternate that resolves, at most a couple of hops per chain and
                // never the same failed video twice.
                if (song != null && !song.isDownloaded && alternateHops < 3 && failedSourceIds.add(song.id)) {
                    alternateHops++
                    lastPlayError = describe(error)
                    playbackGeneration++
                    val generation = playbackGeneration
                    _isBuffering.value = true
                    playerScope.launch {
                        val alternate = findAlternateSource(song)
                        if (generation != playbackGeneration || _currentSong.value?.id != song.id) return@launch
                        if (alternate != null) {
                            val (altSong, altUrl) = alternate
                            altSong.streamUrl = altUrl
                            val q = _queue.value.toMutableList()
                            val idx = q.indexOfFirst { it.id == song.id }
                            if (idx >= 0) {
                                q[idx] = altSong
                                _queue.value = q
                            }
                            _currentSong.value = altSong
                            controller?.let { c ->
                                c.setMediaItem(buildItem(altSong, Uri.parse(altUrl)), resumeMs)
                                c.prepare()
                                c.playWhenReady = wasRequestedToPlay
                                scheduleNextPrefetch()
                            }
                        } else {
                            _isBuffering.value = false
                            _isPlaying.value = false
                            _errorMessage.value = "Could not play \"${song.title}\": the upload is blocked and no alternate source was found.\n${lastPlayError}"
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
                val connected = awaitController()
                // A selection made while the connection was pending wins.
                if (_queue.value.isEmpty()) restoreQueueFor(connected.currentMediaItem)
                connected.addListener(listener)
                // A previous build's per-player gain must not linger as a hidden
                // attenuation; an active sleep fade rewrites this every 250ms.
                connected.volume = 1f
                context.getSharedPreferences("vibestream_player", Context.MODE_PRIVATE)
                    .edit().remove("app_volume").apply()
                refreshDeviceVolume()
                PlaybackService.chapterNav = { fwd -> chapterNav(fwd) }
                PlaybackService.nextHandler = { next() }
                PlaybackService.skipFeedbackHandler = { position -> onUserNext(position) }
                PlaybackService.previousHandler = { previous() }
                if (connected.currentMediaItem != null && playbackJob?.isActive != true) {
                    _isBuffering.value = connected.playbackState == Player.STATE_BUFFERING
                    _duration.value = connected.duration.coerceAtLeast(0L)
                    _currentPosition.value = connected.currentPosition
                    scheduleNextPrefetch()
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
    private var recoveryAttempts = 0
    private var urlRefreshAttempted = false
    private var prefetchJob: Job? = null
    // Alternate stream URLs per song id, kept for the onPlayerError fallback when a
    // prefetched item turns out to be a dead link at play time.
    private val prefetchAlts = mutableMapOf<String, List<String>>()
    // Uploads whose streams were rejected for good this session, and how many
    // same-song alternate uploads the current recovery chain has walked.
    private val failedSourceIds = mutableSetOf<String>()
    private var alternateHops = 0

    /**
     * Searches YouTube Music for the same song (title + artist) and returns the
     * first other upload with a resolvable audio stream, skipping uploads that
     * already failed. Used when the chosen upload's URLs are rejected.
     */
    private suspend fun findAlternateSource(song: Song): Pair<Song, String>? {
        val results = try {
            extractor.searchSongs("${song.title} ${song.artist}", true)
        } catch (e: Exception) {
            return null
        }
        for (alt in results) {
            if (alt.id == song.id || alt.id in failedSourceIds) continue
            val urls = try {
                extractor.resolveAudioCandidates(alt.id)
            } catch (e: Exception) {
                emptyList()
            }
            val url = urls.firstOrNull() ?: continue
            streamUrlStore?.put(alt.id, urls)
            prefetchAlts[alt.id] = urls.drop(1).take(4)
            return alt to url
        }
        return null
    }

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
                val now = System.currentTimeMillis()
                val stored = streamUrlStore?.get(nextSong.id, now)
                val url: String = when {
                    nextSong.isDownloaded && !nextSong.localFilePath.isNullOrEmpty() &&
                        File(nextSong.localFilePath!!).isFile && File(nextSong.localFilePath!!).length() > 0 ->
                        "file://${nextSong.localFilePath}"
                    stored != null -> {
                        prefetchAlts[nextSong.id] = stored.alternates
                        nextSong.streamUrl = stored.url
                        stored.url
                    }
                    StreamUrlStore.isFreshUrl(nextSong.streamUrl, now) -> nextSong.streamUrl!!
                    else -> {
                        val all = extractor.resolveAudioCandidates(nextSong.id)
                        if (all.isEmpty()) return@launch
                        streamUrlStore?.put(nextSong.id, all)
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
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(if (song.thumbnailUrl.isNotEmpty()) Uri.parse(song.thumbnailUrl) else null)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaId(song.id).setMediaMetadata(metadata).build()
    }

    // ---- Visible-songs warmup ----
    // Resolves stream URLs and primes the first seconds of audio for songs the
    // user can see (search results, top Home shelf), so tapping one starts from
    // a resolved URL and cached bytes instead of a cold network resolve. Serial
    // and throttled: one extraction at a time, never competing with playback.
    private var warmupJob: Job? = null
    private val warmedThisSession = mutableSetOf<String>()

    fun prefetchVisible(songs: List<Song>, maxCount: Int = 4) {
        val store = streamUrlStore ?: return
        warmupJob?.cancel()
        val targets = songs
            .filter { it.id.isNotBlank() && !it.isDownloaded }
            .distinctBy { it.id }
            .filter { it.id !in warmedThisSession && _currentSong.value?.id != it.id }
            .take(maxCount)
        if (targets.isEmpty()) return
        warmupJob = playerScope.launch(Dispatchers.IO) {
            for (song in targets) {
                if (!isActive) break
                try {
                    warmedThisSession.add(song.id)
                    val url = store.get(song.id)?.url ?: run {
                        val all = extractor.resolveAudioCandidates(song.id)
                        if (all.isNotEmpty()) {
                            store.put(song.id, all)
                            song.streamUrl = all[0]
                        }
                        all.firstOrNull()
                    } ?: continue
                    StreamWarmup.warm(context, url)
                    delay(300)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Best effort: a tap later resolves on demand.
                }
            }
        }
    }

    fun playSong(song: Song, context: List<Song>? = null, discovery: Boolean = false) {
        liveQueue.begin(discovery)
        adaptationJob?.cancel()
        _liveQueueStatus.value = if (discovery) "Live discovery queue" else ""
        shufflePlayed.clear()
        if (context != null && context.any { it.id == song.id }) {
            _queue.value = context
            _queueIndex.value = context.indexOfFirst { it.id == song.id }
        } else {
            _queue.value = listOf(song)
            _queueIndex.value = 0
        }
        pendingResumeMs = null
        startPlayback(song)
        persistQueue()
        if (discovery) {
            liveQueue.notePlaying(song)
            adaptDiscovery(song, com.contentagent.vibestream.data.recommendation.LiveQueuePolicy.Signal.PLAY)
        }
    }

    private fun startPlayback(song: Song) {
        recoveryAttempts = 0
        urlRefreshAttempted = false
        playbackGeneration++
        val generation = playbackGeneration
        prefetchJob?.cancel()
        playbackJob?.cancel()
        plannedShuffleNext = -1
        trackPlayStart(song, false)
        // A tap is visible immediately, before network extraction completes.
        controller?.pause()
        _currentSong.value = song
        _currentPosition.value = pendingResumeMs ?: 0L
        _duration.value = song.durationSec * 1000L
        _isPlaying.value = false
        _isBuffering.value = true
        // Start the tap-to-sound clock and reset the stall counter for this pick.
        tapStartedAtMs = android.os.SystemClock.elapsedRealtime()
        tapMeasured = false
        _tapToSoundMs.value = null
        _rebufferCount.value = 0
        // Alternatives belong to this track, never to a previous selection.
        pendingCandidates.clear()
        playbackJob = playerScope.launch {
            _errorMessage.value = null
            _isBuffering.value = true

            val now = System.currentTimeMillis()
            val stored = streamUrlStore?.get(song.id, now)
            val playableUri: Uri? = when {
                // Play locally if file exists on device
                song.isDownloaded && !song.localFilePath.isNullOrEmpty() && File(song.localFilePath!!).isFile && File(song.localFilePath!!).length() > 0 -> {
                    Uri.fromFile(File(song.localFilePath!!))
                }
                // Warmed or previously resolved URL, still inside its signed lifetime
                stored != null -> {
                    pendingCandidates.addAll(stored.alternates)
                    song.streamUrl = stored.url
                    Uri.parse(stored.url)
                }
                // Use already cached stream URL only while its signature is valid
                StreamUrlStore.isFreshUrl(song.streamUrl, now) -> {
                    Uri.parse(song.streamUrl)
                }
                // Resolve stream on-the-fly from YouTube
                else -> {
                    val all = extractor.resolveAudioCandidates(song.id)
                    if (generation != playbackGeneration) return@launch
                    pendingCandidates.clear()
                    if (all.isNotEmpty()) {
                        streamUrlStore?.put(song.id, all)
                        pendingCandidates.addAll(all.drop(1).take(4))
                        song.streamUrl = all[0]
                        Uri.parse(all[0])
                    } else {
                        _isBuffering.value = false
                        _errorMessage.value = "Unable to fetch audio stream: ${extractor.lastResolveError ?: "unknown"}"
                        // Do not leave the previous song primed under this
                        // song's title; a later play tap must re-resolve, not
                        // resume the stale item.
                        controller?.let { it.stop(); it.clearMediaItems() }
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
        // The player must hold the song the UI shows. When a resolve fails the
        // previous track's item stays loaded; a play tap must re-resolve the
        // shown song, never resurrect the previous track under its title.
        val shown = _currentSong.value
        if (c != null && shown != null && c.currentMediaItem?.mediaId != shown.id) {
            startPlayback(shown)
            return
        }
        if (c?.isPlaying == true) {
            c.pause()
        } else {
            // A stream paused past its URL's signed lifetime would 403 on resume
            // and wait for the error-recovery path. Refresh it up front instead,
            // keeping the position, so resuming stays uninterrupted.
            val uri = c?.currentMediaItem?.localConfiguration?.uri?.toString()
            val song = _currentSong.value
            if (song != null && uri != null && uri.contains("googlevideo.com") &&
                !StreamUrlStore.isFreshUrl(uri, System.currentTimeMillis())) {
                resumeWithFreshStream(song)
            } else {
                c?.play()
            }
        }
    }

    /** Re-resolves the current song's expired stream and resumes at the position. */
    private fun resumeWithFreshStream(song: Song) {
        playbackGeneration++
        val generation = playbackGeneration
        _isBuffering.value = true
        playerScope.launch {
            val resumeMs = controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value
            val stored = streamUrlStore?.get(song.id)
            val fresh: List<String> = stored?.let { listOf(it.url) + it.alternates } ?: run {
                val all = extractor.resolveAudioCandidates(song.id)
                if (all.isNotEmpty()) streamUrlStore?.put(song.id, all)
                all
            }
            if (generation != playbackGeneration || _currentSong.value?.id != song.id) return@launch
            val url = fresh.firstOrNull()
            if (url == null) {
                _isBuffering.value = false
                _errorMessage.value = "Could not refresh audio stream. Retry playback."
                return@launch
            }
            pendingCandidates.clear()
            pendingCandidates.addAll(fresh.drop(1).take(4))
            song.streamUrl = url
            controller?.let { c ->
                c.setMediaItem(buildItem(song, Uri.parse(url)), resumeMs)
                c.prepare()
                c.playWhenReady = true
                scheduleNextPrefetch()
            }
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
        if (volumeReceiverRegistered) {
            volumeReceiverRegistered = false
            try { context.unregisterReceiver(volumeReceiver) } catch (_: Exception) {}
        }
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        playerScope.coroutineContext[Job]?.cancel()
    }
}
