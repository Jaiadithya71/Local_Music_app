package com.contentagent.vibestream.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Google OAuth 2.0 device flow ("TVs and limited input devices"), used for the
 * optional YouTube Music sign-in. The app asks Google for a short user code; the
 * user enters that code on google.com/device in their own browser and approves;
 * the app polls until Google hands over tokens. No WebView, no cookies, and the
 * app never sees the Google password.
 *
 * Pure request/parse functions are kept internal and network-free so unit tests
 * can cover them.
 */
object GoogleDeviceAuth {
    const val SCOPE = "https://www.googleapis.com/auth/youtube"
    const val DEFAULT_VERIFICATION_URL = "https://www.google.com/device"

    private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
    private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Long,
        val expiresAtMs: Long
    )

    sealed interface PollResult {
        data class Success(val accessToken: String, val refreshToken: String?, val expiresInSec: Long) : PollResult
        /** User has not approved yet; keep polling. */
        data object Pending : PollResult
        /** Google asked us to slow down; add 5s to the interval. */
        data object SlowDown : PollResult
        data class Failed(val error: String) : PollResult
    }

    /** Parses the device-code endpoint response. Null on malformed payloads. */
    internal fun parseDeviceCode(body: String, nowMs: Long = System.currentTimeMillis()): DeviceCode? = try {
        val o = JsonParser.parseString(body).asJsonObject
        val deviceCode = o.get("device_code")?.asString
        val userCode = o.get("user_code")?.asString
        if (deviceCode.isNullOrBlank() || userCode.isNullOrBlank()) null
        else DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUrl = o.get("verification_url")?.asString?.takeIf { it.isNotBlank() } ?: DEFAULT_VERIFICATION_URL,
            intervalSec = o.get("interval")?.asLong?.coerceIn(1, 60) ?: 5L,
            expiresAtMs = nowMs + (o.get("expires_in")?.asLong ?: 1800L) * 1000L
        )
    } catch (e: Exception) { null }

    /** Parses a token-endpoint response from a device-flow poll. */
    internal fun parsePollResponse(httpCode: Int, body: String?): PollResult {
        if (httpCode in 200..299 && body != null) {
            return try {
                val o = JsonParser.parseString(body).asJsonObject
                val access = o.get("access_token")?.asString
                if (access.isNullOrBlank()) PollResult.Failed("no_access_token")
                else PollResult.Success(
                    accessToken = access,
                    refreshToken = o.get("refresh_token")?.asString?.takeIf { it.isNotBlank() },
                    expiresInSec = o.get("expires_in")?.asLong ?: 3600L
                )
            } catch (e: Exception) { PollResult.Failed("bad_response") }
        }
        val error = try {
            body?.let { JsonParser.parseString(it).asJsonObject.get("error")?.asString }
        } catch (e: Exception) { null } ?: "http_$httpCode"
        return when (error) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            else -> PollResult.Failed(error)
        }
    }

    /** Parses a refresh-token response into (accessToken, expiresInSec). */
    internal fun parseRefreshResponse(httpCode: Int, body: String?): Pair<String, Long>? {
        if (httpCode !in 200..299 || body == null) return null
        return try {
            val o: JsonObject = JsonParser.parseString(body).asJsonObject
            val access = o.get("access_token")?.asString?.takeIf { it.isNotBlank() } ?: return null
            access to (o.get("expires_in")?.asLong ?: 3600L)
        } catch (e: Exception) { null }
    }

    /** Step 1: ask Google for a device code + user code. Null on network failure. */
    fun requestDeviceCode(clientId: String): DeviceCode? {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", SCOPE)
            .build()
        val req = Request.Builder().url(DEVICE_CODE_URL).post(body).build()
        return try {
            client.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: return null
                if (!r.isSuccessful) null else parseDeviceCode(text)
            }
        } catch (e: Exception) { null }
    }

    /** Step 2 (repeated): poll the token endpoint until the user approves or the code expires. */
    fun pollToken(clientId: String, clientSecret: String, dc: DeviceCode): PollResult {
        if (System.currentTimeMillis() > dc.expiresAtMs) return PollResult.Failed("expired_token")
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("device_code", dc.deviceCode)
            .add("grant_type", DEVICE_GRANT)
            .build()
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        return try {
            client.newCall(req).execute().use { r -> parsePollResponse(r.code, r.body?.string()) }
        } catch (e: Exception) { PollResult.Failed("network") }
    }

    /** Exchanges a refresh token for a fresh access token. */
    fun refreshAccessToken(clientId: String, clientSecret: String, refreshToken: String): Pair<String, Long>? {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val req = Request.Builder().url(TOKEN_URL).post(body).build()
        return try {
            client.newCall(req).execute().use { r -> parseRefreshResponse(r.code, r.body?.string()) }
        } catch (e: Exception) { null }
    }

    /** Best-effort remote revocation; local wipe happens regardless. */
    fun revoke(token: String) {
        val body = FormBody.Builder().add("token", token).build()
        val req = Request.Builder().url(REVOKE_URL).post(body).build()
        try {
            client.newCall(req).execute().close()
        } catch (e: Exception) { /* best effort */ }
    }
}
