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
    private val extractor: YouTubeExtractor
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
                    }
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        _isPlaying.value = false
                        stopProgressTracker()
                    }
                    Player.STATE_IDLE -> {
                        _isBuffering.value = false
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                restoreCurrentSong()
            }

            override fun onPlayerError(error: PlaybackException) {
                val next = pendingCandidates.removeFirstOrNull()
                val song = _currentSong.value
                if (next != null && song != null) {
                    lastPlayError = describe(error)
                    playerScope.launch {
                        controller?.let { c ->
                            c.setMediaItem(buildItem(song, Uri.parse(next)))
                            c.prepare()
                            c.play()
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
                restoreCurrentSong()
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

    fun playSong(song: Song) {
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

            if (playableUri != null) {
                val player = try { awaitController() } catch (e: Exception) {
                    _isBuffering.value = false
                    _errorMessage.value = "Playback service unavailable: ${e.localizedMessage}"
                    return@launch
                }
                val mediaItem = buildItem(song, playableUri)

                _currentSong.value = song
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
        }
    }

    fun togglePlayPause() {
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play()
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
    }

    fun release() {
        stopProgressTracker()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        playerScope.coroutineContext[Job]?.cancel()
    }
}
