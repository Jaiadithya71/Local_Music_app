package com.contentagent.vibestream.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.contentagent.vibestream.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Android MediaSessionService ensuring audio continues seamlessly
 * in the background when screen is off or app is minimized.
 *
 * The media notification carries Spotify-style extra action buttons
 * (like/unlike the current track, toggle shuffle) via the session's
 * custom layout, next to the usual previous / play / next controls.
 */
class PlaybackService : MediaSessionService() {

    companion object {
        // Set by MusicPlayerController: notification / lock-screen skip taps land
        // here when the ExoPlayer playlist itself has no adjacent item to seek to.
        var nextHandler: (() -> Unit)? = null
        var previousHandler: (() -> Unit)? = null

        // Spotify-style notification actions, wired from MainActivity.
        var likeHandler: (() -> Unit)? = null
        var shuffleHandler: (() -> Unit)? = null
        var isLikedProvider: (() -> Boolean)? = null
        var isShuffleProvider: (() -> Boolean)? = null
        var refreshButtons: (() -> Unit)? = null

        // ExoPlayer's audio session, used by EqualizerManager for the system EQ.
        @Volatile var audioSessionId: Int = 0
        // Ephemeral local-only diagnostics; no URLs, account identifiers or history.
        @Volatile var audioFormatSummary: String = "Not playing"
        @Volatile var audioUnderruns: Int = 0

        private const val CMD_LIKE = "com.contentagent.vibestream.LIKE"
        private const val CMD_SHUFFLE = "com.contentagent.vibestream.SHUFFLE"
    }

    private var mediaSession: MediaSession? = null

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val sessionCommands = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_LIKE -> {
                    likeHandler?.invoke()
                    updateCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_SHUFFLE -> {
                    shuffleHandler?.invoke()
                    updateCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    private fun updateCustomLayout() {
        val session = mediaSession ?: return
        val liked = isLikedProvider?.invoke() == true
        val shuffling = isShuffleProvider?.invoke() == true
        val like = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .setIconResId(if (liked) R.drawable.ic_notif_like_filled else R.drawable.ic_notif_like)
            .setDisplayName(if (liked) "Unlike" else "Like")
            .build()
        val shuffle = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notif_shuffle)
            .setDisplayName(if (shuffling) "Shuffle on" else "Shuffle")
            .build()
        session.setCustomLayout(ImmutableList.of(like, shuffle))
    }

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        // DefaultHttpDataSource alone rejects file:// offline tracks. The default
        // wrapper selects the file/asset/content reader and delegates HTTP only.
        val sources = androidx.media3.datasource.DefaultDataSource.Factory(this, http)
        val factory = ResolvingDataSource.Factory(sources) { spec: DataSpec ->
            val u = spec.uri.toString()
            if (u.contains("googlevideo.com")) {
                val ua = when {
                    u.contains("c=IOS") -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
                    u.contains("c=ANDROID") -> "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip"
                    else -> "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
                }
                spec.buildUpon()
                    .setHttpRequestHeaders(spec.httpRequestHeaders + mapOf("User-Agent" to ua))
                    .build()
            } else spec
        }
        // Let ExoPlayer own audio focus: calls, alarms and other apps taking focus
        // pause playback (and resume after a transient loss), a permanent focus
        // loss stops it, and yanking headphones pauses instead of blasting
        // through the speaker.
        val audioAttrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(audioAttrs, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                val rate = if (format.sampleRate > 0) "${format.sampleRate} Hz" else "rate unknown"
                val channels = if (format.channelCount > 0) "${format.channelCount} channels" else "channels unknown"
                val bitrate = if (format.averageBitrate > 0) "${format.averageBitrate / 1000} kbps" else "bitrate unknown"
                audioFormatSummary = "${format.sampleMimeType ?: "codec unknown"} · $rate · $channels · $bitrate"
            }
            override fun onAudioUnderrun(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long
            ) {
                audioUnderruns++
            }
        })
        audioSessionId = exo.audioSessionId
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                if (item != null && item.mediaId.isNotBlank()) {
                    // The service owns background transitions. Do not depend on a
                    // foreground Compose observer to remember what was played.
                    com.contentagent.vibestream.data.local.LibraryStore(this@PlaybackService).recordPlayed(
                        com.contentagent.vibestream.data.model.Song(
                            item.mediaId, item.mediaMetadata.title?.toString().orEmpty(),
                            item.mediaMetadata.artist?.toString().orEmpty()))
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackService.audioSessionId = audioSessionId
            }
        })
        // Always advertise prev/next so the lock-screen and notification controls
        // show skip buttons. When the playlist has the adjacent item we let
        // ExoPlayer seek (gapless); otherwise the app controller resolves the
        // next track on the fly.
        val player = object : androidx.media3.common.ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
            override fun seekToNext() {
                if (exo.hasNextMediaItem()) super.seekToNextMediaItem() else nextHandler?.invoke()
            }
            override fun seekToNextMediaItem() {
                if (exo.hasNextMediaItem()) super.seekToNextMediaItem() else nextHandler?.invoke()
            }
            override fun seekToPrevious() {
                if (exo.currentMediaItemIndex > 0) super.seekToPreviousMediaItem() else previousHandler?.invoke()
            }
            override fun seekToPreviousMediaItem() {
                if (exo.currentMediaItemIndex > 0) super.seekToPreviousMediaItem() else previousHandler?.invoke()
            }
        }
        val session = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()
        mediaSession = session
        refreshButtons = { updateCustomLayout() }
        updateCustomLayout()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        refreshButtons = null
        audioSessionId = 0
        audioFormatSummary = "Not playing"
        audioUnderruns = 0
        super.onDestroy()
    }
}
