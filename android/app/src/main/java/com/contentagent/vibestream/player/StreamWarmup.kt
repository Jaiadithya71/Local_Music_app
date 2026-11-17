package com.contentagent.vibestream.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource

/**
 * Warms the first seconds of a resolved audio stream into the read-through
 * playback cache, so tapping a visible song starts from cached bytes instead of
 * a cold connection. Best effort: any failure is swallowed by the caller's
 * catch; playback itself never depends on this having run.
 */
object StreamWarmup {

    const val WARM_BYTES = 192 * 1024L

    /**
     * The User-Agent a googlevideo URL expects, derived from the client the URL
     * was minted for. Shared by playback (PlaybackService) and warmup so both
     * hit the same CDN path with acceptable headers.
     */
    fun userAgentFor(url: String): String = when {
        url.contains("c=IOS") -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
        url.contains("c=ANDROID") -> "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip"
        else -> "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }

    /** The data-source stack playback uses, for any component that must read stream bytes. */
    fun resolvingFactory(context: Context): ResolvingDataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val sources = DefaultDataSource.Factory(context, http)
        return ResolvingDataSource.Factory(sources) { spec: DataSpec ->
            val u = spec.uri.toString()
            if (u.contains("googlevideo.com")) {
                spec.buildUpon()
                    .setHttpRequestHeaders(spec.httpRequestHeaders + mapOf("User-Agent" to userAgentFor(u)))
                    .build()
            } else spec
        }
    }

    /**
     * Reads the first [WARM_BYTES] of [url] through the playback cache and stops.
     * The cache key matches what ExoPlayer computes for the same URL, so the
     * primed span is exactly what the player asks for first. Blocking; call off
     * the main thread.
     */
    fun warm(context: Context, url: String) {
        val source = PlaybackCache.factory(context.applicationContext, resolvingFactory(context)).createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
            val buffer = ByteArray(16 * 1024)
            var left = WARM_BYTES
            while (left > 0) {
                val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                left -= n
            }
        } finally {
            try { source.close() } catch (_: Exception) {}
        }
    }
}
