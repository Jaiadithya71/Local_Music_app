package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.RelatedSongCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelatedSongCacheTest {
    @Test fun ttlOfflineFallbackAndCancellation() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_radio_v1", 0).edit().clear().commit()
        val cache = RelatedSongCache(ctx); var calls = 0
        val fetch: suspend (String) -> List<Song> = { calls++; listOf(Song("x", "Music", "Artist", streamUrl = "ephemeral")) }
        val first = cache.get("seed", fetch, 1)
        assertNull(first[0].streamUrl)
        assertEquals(first, cache.get("seed", fetch, 100)); assertEquals(1, calls)
        assertEquals(first, cache.get("seed", { emptyList() }, 86_400_002L))
        assertTrue(cache.get("unknown", { throw java.io.IOException() }, 100).isEmpty())
        try { cache.get("cancel", { throw CancellationException() }, 100); fail("cancellation swallowed") } catch (_: CancellationException) { }
    }
}
