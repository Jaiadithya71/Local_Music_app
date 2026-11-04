package com.contentagent.vibestream

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.recommendation.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Bounded public catalog probe. Not a personal listening-quality score. */
@RunWith(AndroidJUnit4::class)
class LiveSongRadioTest {
    @Test fun comparePublicSeedsAndWarmCache() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("vibestream_radio_v1", 0).edit().clear().commit()
        val extractor = YouTubeExtractor()
        val baseline = extractor.searchSongs("Tamil new releases", true)
        assertTrue("baseline search unavailable", baseline.size >= 5)
        val seeds = listOf("3wDiqlTNlfQ", "unQlCp-lL6I", "2qCpY38ompo")
        val cache = RelatedSongCache(ctx)
        val rows = seeds.map { seed ->
            var calls = 0
            val start = System.nanoTime()
            val radio = cache.get(seed, { calls++; extractor.relatedSongs(it) })
            val coldMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("radio unavailable for $seed", radio.size >= 5)
            val warmStart = System.nanoTime()
            val warm = cache.get(seed, { calls++; error("warm cache should not fetch") })
            val warmMs = (System.nanoTime() - warmStart) / 1_000_000
            assertEquals(1, calls); assertEquals(radio, warm)
            val known = setOf("id:$seed") + baseline.take(5).flatMap { DiscoveryPolicy.keys(it) }
            val old = DiscoveryPolicy.fresh(baseline, known, limit = 20)
            val fresh = DiscoveryPolicy.fresh(SongSeedPolicy.blend(radio, baseline, listOf("Tamil")), known, limit = 20)
            assertTrue(fresh.size >= 10)
            assertFalse(fresh.flatMap { DiscoveryPolicy.keys(it) }.any { it in known })
            mapOf("seed_id" to seed, "baseline_count" to baseline.size, "radio_count" to radio.size,
                "cold_radio_ms" to coldMs, "warm_cache_ms" to warmMs, "radio_fetch_calls_for_two_reads" to calls,
                "baseline_top20" to old.map { mapOf("id" to it.id, "title" to it.title, "artist" to it.artist) },
                "new_top20" to fresh.map { mapOf("id" to it.id, "title" to it.title, "artist" to it.artist) },
                "new_items_not_in_baseline_top20" to fresh.count { n -> old.none { it.id == n.id } },
                "known_overlap" to 0)
        }
        val report = GsonBuilder().setPrettyPrinting().create().toJson(mapOf("baseline" to "v1.1.4 keyword Tamil new releases", "note" to "Public example seeds, not user's likes; no human relevance or audio scoring", "rows" to rows))
        File(System.getProperty("user.home"), "live-song-radio.json").writeText(report)
    }
}
