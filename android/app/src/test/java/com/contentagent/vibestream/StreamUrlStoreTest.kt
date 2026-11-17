package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.local.StreamUrlStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamUrlStoreTest {

    private val now = 1_800_000_000_000L // fixed point in time for determinism

    @Test fun expiryComesFromSignedUrlParam() {
        val url = "https://rr1.googlevideo.com/videoplayback?id=abc&expire=1800003600&itag=140"
        assertEquals(1_800_003_600_000L, StreamUrlStore.expiryMsOf(url, now))
        // No expire parameter: conservative fallback TTL from now.
        assertEquals(now + StreamUrlStore.FALLBACK_TTL_MS, StreamUrlStore.expiryMsOf("https://x.test/a", now))
    }

    @Test fun freshnessHonoursSafetyMargin() {
        val future = "https://rr1.googlevideo.com/v?expire=${now / 1000 + 3600}"
        val soon = "https://rr1.googlevideo.com/v?expire=${now / 1000 + 60}" // inside the 10-min margin
        val past = "https://rr1.googlevideo.com/v?expire=${now / 1000 - 60}"
        assertTrue(StreamUrlStore.isFreshUrl(future, now))
        assertFalse(StreamUrlStore.isFreshUrl(soon, now))
        assertFalse(StreamUrlStore.isFreshUrl(past, now))
        assertFalse(StreamUrlStore.isFreshUrl("https://rr1.googlevideo.com/v?itag=140", now))
        assertFalse(StreamUrlStore.isFreshUrl(null, now))
        assertFalse(StreamUrlStore.isFreshUrl("", now))
    }

    @Test fun putGetRoundTripWithAlternatesAndExpiry() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = StreamUrlStore(ctx)
        store.clear()
        val t = System.currentTimeMillis()
        val main = "https://rr1.googlevideo.com/v?id=song1&expire=${t / 1000 + 3600}&itag=140"
        val alt = "https://rr2.googlevideo.com/v?id=song1&expire=${t / 1000 + 3600}&itag=251"
        store.put("song1", listOf(main, alt))
        val got = store.get("song1")
        assertNotNull(got)
        assertEquals(main, got!!.url)
        assertEquals(listOf(alt), got.alternates)
        assertTrue(got.expiresAtMs > t)
    }

    @Test fun staleEntryIsNotReturned() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = StreamUrlStore(ctx)
        store.clear()
        val t = System.currentTimeMillis()
        val dead = "https://rr1.googlevideo.com/v?id=old&expire=${t / 1000 - 10}&itag=140"
        store.put("old", listOf(dead))
        assertNull(store.get("old"))
    }

    @Test fun storeIsBounded() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = StreamUrlStore(ctx)
        store.clear()
        val t = System.currentTimeMillis()
        for (i in 0 until StreamUrlStore.MAX_ENTRIES + 10) {
            store.put("song$i", listOf("https://rr1.googlevideo.com/v?id=song$i&expire=${t / 1000 + 3600}&itag=140"), nowMs = t + i)
        }
        assertNull("oldest entry evicted beyond the cap", store.get("song0"))
        assertNotNull("newest entry kept", store.get("song${StreamUrlStore.MAX_ENTRIES + 9}"))
        store.clear()
    }
}
