package com.contentagent.vibestream.player

import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/** Read-through only: stores bytes the player actually requests, never bulk downloads. */
object PlaybackCache {
    const val MAX_BYTES = 512L * 1024 * 1024
    private var cache: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        context.applicationContext.cacheDir.resolve("playback_audio_v1"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES), StandaloneDatabaseProvider(context.applicationContext)
    ).also { cache = it }

    fun key(spec: DataSpec): String {
        val uri = spec.uri
        // Only stable resource identity WITH encoding and length can share bytes.
        // Never use song ID alone: alternate AAC/Opus encodings differ.
        if (uri.host?.endsWith(".googlevideo.com") == true) {
            val id = uri.getQueryParameter("id")
            val itag = uri.getQueryParameter("itag")
            val length = uri.getQueryParameter("clen")
            if (!id.isNullOrBlank() && !itag.isNullOrBlank() && !length.isNullOrBlank())
                return "youtube:$id:$itag:$length"
        }
        return spec.key ?: uri.toString()
    }

    fun factory(context: Context, upstream: DataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(get(context)).setUpstreamDataSourceFactory(upstream)
        .setCacheKeyFactory { key(it) }
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach { c.removeResource(it) }
    }
}
