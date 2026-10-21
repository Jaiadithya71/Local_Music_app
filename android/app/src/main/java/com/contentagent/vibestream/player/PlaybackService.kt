package com.contentagent.vibestream.player

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Android MediaSessionService ensuring audio continues seamlessly
 * in the background when screen is off or app is minimized.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val factory = ResolvingDataSource.Factory(http) { spec: DataSpec ->
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
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(audioAttrs, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
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
        super.onDestroy()
    }
}
