package com.contentagent.vibestream

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.player.PlaybackCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PlaybackCacheTest {
    @Test fun cacheSeparatesEncodingsButReusesRotatedSignedUrls() {
        fun key(url: String) = PlaybackCache.key(DataSpec(Uri.parse(url)))
        val base = "https://rr1.googlevideo.com/videoplayback?id=recording&itag=140&clen=1000"
        assertEquals(key("$base&expire=1"), key("$base&expire=2"))
        assertNotEquals(key(base), key(base.replace("itag=140", "itag=251")))
        assertNotEquals(key(base), key(base.replace("clen=1000", "clen=2000")))
        assertNotEquals(key("https://example.com/audio?a=1"), key("https://example.com/audio?a=2"))
    }
    @Test fun readThroughCachesRequestedBytesAndOfflineReplayNeedsNoUpstream() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        PlaybackCache.clear(ctx)
        val bytes = ByteArray(120_000) { (it % 251).toByte() }
        var opens = 0
        val factory = DataSource.Factory { object : DataSource {
            private var at = 0; private var end = 0
            override fun addTransferListener(listener: TransferListener) { }
            override fun open(spec: DataSpec): Long {
                opens++; at = spec.position.toInt(); end = if (spec.length < 0) bytes.size else minOf(bytes.size, at + spec.length.toInt()); return (end-at).toLong()
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if(at >= end) return -1
                val n = minOf(length, end-at); bytes.copyInto(buffer, offset, at, at+n); at += n; return n
            }
            override fun getUri() = Uri.parse("https://fixture.invalid/test.wav")
            override fun close() { }
        } }
        fun read(upstream: DataSource.Factory): ByteArray {
            val source = PlaybackCache.factory(ctx, upstream).createDataSource()
            source.open(DataSpec(Uri.parse("https://fixture.invalid/test.wav")))
            val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) { val n = source.read(buffer, 0, buffer.size); if (n < 0) break; out.write(buffer, 0, n) }
            source.close(); return out.toByteArray()
        }
        assertArrayEquals(bytes, read(factory)); assertEquals(1, opens)
        assertArrayEquals(bytes, read(factory))
        assertEquals(1, opens)
        PlaybackCache.clear(ctx)
    }
}
