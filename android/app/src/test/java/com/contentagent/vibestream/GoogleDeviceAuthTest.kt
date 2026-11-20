package com.contentagent.vibestream

import com.contentagent.vibestream.data.auth.GoogleDeviceAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the OAuth device-flow request parsing (no network). */
class GoogleDeviceAuthTest {

    @Test
    fun parseDeviceCode_readsAllFields() {
        val body = """
            {"device_code":"dev-123","user_code":"ABCD-EFGH","verification_url":"https://www.google.com/device","expires_in":1800,"interval":5}
        """.trimIndent()
        val dc = GoogleDeviceAuth.parseDeviceCode(body, nowMs = 1_000_000L)!!
        assertEquals("dev-123", dc.deviceCode)
        assertEquals("ABCD-EFGH", dc.userCode)
        assertEquals("https://www.google.com/device", dc.verificationUrl)
        assertEquals(5L, dc.intervalSec)
        assertEquals(1_000_000L + 1800_000L, dc.expiresAtMs)
    }

    @Test
    fun parseDeviceCode_defaultsAndRejectsMalformed() {
        val noUrl = """{"device_code":"d","user_code":"U","expires_in":10,"interval":3}"""
        assertEquals(GoogleDeviceAuth.DEFAULT_VERIFICATION_URL, GoogleDeviceAuth.parseDeviceCode(noUrl)!!.verificationUrl)
        assertNull(GoogleDeviceAuth.parseDeviceCode("not json"))
        assertNull(GoogleDeviceAuth.parseDeviceCode("""{"user_code":"U"}"""))
    }

    @Test
    fun parsePollResponse_mapsGoogleStates() {
        assertEquals(GoogleDeviceAuth.PollResult.Pending,
            GoogleDeviceAuth.parsePollResponse(428, """{"error":"authorization_pending"}"""))
        assertEquals(GoogleDeviceAuth.PollResult.SlowDown,
            GoogleDeviceAuth.parsePollResponse(403, """{"error":"slow_down"}"""))
        val failed = GoogleDeviceAuth.parsePollResponse(400, """{"error":"access_denied"}""")
        assertTrue(failed is GoogleDeviceAuth.PollResult.Failed)
        assertEquals("access_denied", (failed as GoogleDeviceAuth.PollResult.Failed).error)
        val ok = GoogleDeviceAuth.parsePollResponse(200,
            """{"access_token":"ya29.x","refresh_token":"r1","expires_in":3600,"token_type":"Bearer"}""")
        assertTrue(ok is GoogleDeviceAuth.PollResult.Success)
        ok as GoogleDeviceAuth.PollResult.Success
        assertEquals("ya29.x", ok.accessToken)
        assertEquals("r1", ok.refreshToken)
        assertEquals(3600L, ok.expiresInSec)
    }

    @Test
    fun parseRefreshResponse_readsToken() {
        val ok = GoogleDeviceAuth.parseRefreshResponse(200, """{"access_token":"ya29.y","expires_in":3599}""")
        assertEquals("ya29.y", ok!!.first)
        assertEquals(3599L, ok.second)
        assertNull(GoogleDeviceAuth.parseRefreshResponse(401, """{"error":"invalid_grant"}"""))
        assertNull(GoogleDeviceAuth.parseRefreshResponse(200, "garbage"))
    }
}
