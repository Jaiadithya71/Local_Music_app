package com.contentagent.vibestream

import com.contentagent.vibestream.data.extractor.YouTubeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Pure-JVM checks for the YouTube Music signed-in request auth. */
class YtMusicAuthTest {

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun sapisidHash_matchesWebClientFormat() {
        val ex = YouTubeExtractor()
        val hash = ex.sapisidHash("test-sapisid-value", timestampSec = 1700000000L)
        val expected = "SAPISIDHASH 1700000000_" + sha1("1700000000 test-sapisid-value https://music.youtube.com")
        assertEquals(expected, hash)
    }

    @Test
    fun sapisidHash_isSha1Hex() {
        val ex = YouTubeExtractor()
        val hash = ex.sapisidHash("abc123")
        assertTrue(hash.startsWith("SAPISIDHASH "))
        val body = hash.removePrefix("SAPISIDHASH ")
        val (ts, hex) = body.split("_", limit = 2)
        assertTrue(ts.all { it.isDigit() })
        assertEquals(40, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
