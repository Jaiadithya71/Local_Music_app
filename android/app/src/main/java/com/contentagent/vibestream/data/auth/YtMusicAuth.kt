package com.contentagent.vibestream.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.contentagent.vibestream.BuildConfig

/**
 * Holds the optional YouTube Music OAuth session, on-device only.
 *
 * Sign-in uses Google's device flow (see [GoogleDeviceAuth]): the user approves
 * on google.com/device in their own browser, so VibeStream never sees the Google
 * password and never touches WebView cookies. Tokens live in
 * EncryptedSharedPreferences (Android Keystore-backed) and are only ever sent to
 * Google (token refresh) and music.youtube.com (signed-in catalog requests).
 * Signing out revokes the token with Google and wipes local storage.
 */
object YtMusicAuth {
    private const val PREFS = "ytm_auth_prefs"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_ACCESS_EXPIRES = "access_expires_at"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var refreshToken: String? = null
    @Volatile private var accessToken: String? = null
    @Volatile private var accessExpiresAtMs: Long = 0

    /** OAuth client credentials injected at build time (see app/build.gradle.kts). */
    var clientId: String = BuildConfig.YTM_CLIENT_ID
    var clientSecret: String = BuildConfig.YTM_CLIENT_SECRET

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Never downgrade OAuth token storage to plaintext.
            null
        }
        refreshToken = prefs?.getString(KEY_REFRESH, null)
        accessToken = prefs?.getString(KEY_ACCESS, null)
        accessExpiresAtMs = prefs?.getLong(KEY_ACCESS_EXPIRES, 0L) ?: 0L
    }

    fun isSignedIn(): Boolean = !refreshToken.isNullOrBlank()

    fun hasSecureStorage(): Boolean = prefs != null

    /** True when this build carries an OAuth client id, so the sign-in flow can start. */
    fun isConfigured(): Boolean = clientId.isNotBlank()

    /** Current refresh token, kept only for sign-out revocation. */
    fun refreshTokenOrNull(): String? = refreshToken

    @Synchronized
    fun saveTokens(access: String, refresh: String?, expiresInSec: Long) {
        check(prefs != null) { "Encrypted token storage is unavailable" }
        accessToken = access
        // Treat the token as stale a little early so requests never race expiry.
        accessExpiresAtMs = System.currentTimeMillis() + (expiresInSec - 60).coerceAtLeast(30) * 1000L
        if (!refresh.isNullOrBlank()) refreshToken = refresh
        prefs?.edit()
            ?.putString(KEY_ACCESS, access)
            ?.putLong(KEY_ACCESS_EXPIRES, accessExpiresAtMs)
            ?.apply()
        if (!refresh.isNullOrBlank()) {
            prefs?.edit()?.putString(KEY_REFRESH, refresh)?.apply()
        }
    }

    /**
     * A fresh access token: the cached one when still valid, otherwise a
     * synchronous refresh. Blocks on network during refresh, so call it off the
     * main thread (the extractor always does).
     */
    @Synchronized
    fun accessTokenOrRefresh(): String? {
        val cached = accessToken
        if (!cached.isNullOrBlank() && System.currentTimeMillis() < accessExpiresAtMs) return cached
        val refresh = refreshToken ?: return null
        val renewed = GoogleDeviceAuth.refreshAccessToken(clientId, clientSecret, refresh) ?: return null
        saveTokens(renewed.first, null, renewed.second)
        return renewed.first
    }

    /** Drops the cached access token after the server rejects it; the next call refreshes. */
    @Synchronized
    fun invalidateAccessToken() {
        accessToken = null
        accessExpiresAtMs = 0L
        prefs?.edit()?.remove(KEY_ACCESS)?.remove(KEY_ACCESS_EXPIRES)?.apply()
    }

    /** Wipes every trace of the session locally. Pair with [GoogleDeviceAuth.revoke]. */
    @Synchronized
    fun signOut() {
        refreshToken = null
        accessToken = null
        accessExpiresAtMs = 0L
        prefs?.edit()?.clear()?.apply()
    }
}
