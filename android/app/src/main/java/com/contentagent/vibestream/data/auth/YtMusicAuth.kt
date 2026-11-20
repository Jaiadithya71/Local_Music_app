package com.contentagent.vibestream.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the signed-in YouTube Music session cookie, on-device only.
 *
 * The cookie is captured from a WebView where the user signs in on Google's own
 * pages; VibeStream never sees the Google password. The cookie is stored in
 * EncryptedSharedPreferences (Android Keystore-backed) and never leaves the
 * device. Signing out wipes it completely.
 */
object YtMusicAuth {
    private const val PREFS = "ytm_auth_prefs"
    private const val KEY_COOKIE = "cookie"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var cachedCookie: String? = null

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
            // Keystore unavailable (very rare): fall back to app-private storage.
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
        cachedCookie = prefs?.getString(KEY_COOKIE, null)
    }

    fun isSignedIn(): Boolean = !cachedCookie.isNullOrBlank()

    fun cookieOrNull(): String? = cachedCookie?.takeIf { it.contains("SAPISID") }

    fun saveCookie(cookieHeader: String) {
        cachedCookie = cookieHeader
        prefs?.edit()?.putString(KEY_COOKIE, cookieHeader)?.apply()
    }

    fun signOut() {
        cachedCookie = null
        prefs?.edit()?.remove(KEY_COOKIE)?.apply()
    }
}
