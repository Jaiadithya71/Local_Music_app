package com.contentagent.vibestream

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import com.contentagent.vibestream.data.recommendation.DiscoveryPolicy as D
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Manual, bounded public-catalog check; not a claim about private phone history. */
@RunWith(AndroidJUnit4::class)
class LiveDiscoveryCheckTest {
    @Test fun liveCatalogHasFreshCandidatesAcrossTwoRounds() = runBlocking {
        val extractor = YouTubeExtractor()
        val queries = listOf("Tamil new releases", "Tamil indie songs", "Tamil melody songs")
        val pools = queries.map { extractor.searchSongs(it, true) }
        assertTrue("public search unavailable", pools.all { it.isNotEmpty() })
        val baseline = pools.first().take(5)
        val known = baseline.flatMap { D.keys(it) }.toSet()
        val first = D.fresh(pools[0] + pools[1], known)
        val played = known + first.take(5).flatMap { D.keys(it) }
        val second = D.fresh(pools.flatten(), played)
        assertTrue(first.size >= 5)
        assertTrue(second.size >= 5)
        assertTrue(first.flatMap { D.keys(it) }.none { it in known })
        assertTrue(second.flatMap { D.keys(it) }.none { it in played })
        val report = mapOf("queries" to queries, "pool_sizes" to pools.map { it.size }, "first_count" to first.size, "second_count" to second.size,
            "known_overlap_first" to 0, "known_overlap_second" to 0,
            "first_titles" to first.take(8).map { it.title }, "second_titles" to second.take(8).map { it.title })
        File(System.getProperty("user.home"), "live-discovery.json").writeText(Gson().toJson(report))
        println(Gson().toJson(report))
    }
}
