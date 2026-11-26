package com.contentagent.vibestream.data.local

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Sends the play log to his private store (a free Cloudflare Pages function + KV in his own
 * Cloudflare account), keyed by a random per-install ID. No login or token on the phone.
 * Can be paused from the cloud-icon dialog. Content: song, artist, seconds listened,
 * outcome, source, time-of-day and listening context. Nothing else.
 */
object ListeningSync {
    private const val ENDPOINT = "https://vibestream-log-b85d6b.pages.dev/log/"
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var prefs: android.content.SharedPreferences? = null
    private var job: Job? = null

    fun init(ctx: Context) {
        if (prefs == null) {
            val p = ctx.applicationContext.getSharedPreferences("vibestream_listening_sync", Context.MODE_PRIVATE)
            if (p.getString("install_id", null) == null) {
                p.edit().putString("install_id", UUID.randomUUID().toString().replace("-", "")).apply()
            }
            prefs = p
        }
        schedule()
    }

    fun connected(): Boolean = prefs != null && prefs?.getBoolean("paused", false) != true
    fun setPaused(paused: Boolean) { prefs?.edit()?.putBoolean("paused", paused)?.apply(); if (!paused) schedule(500L) }
    fun installId(): String = prefs?.getString("install_id", "") ?: ""

    fun status(): String {
        val p = prefs ?: return "Not ready"
        if (p.getBoolean("paused", false)) return "Paused"
        val ok = p.getLong("last_ok", 0L)
        val err = p.getString("last_error", null)
        return when {
            err != null -> "Last sync failed: $err"
            ok > 0 -> "Synced " + ((System.currentTimeMillis() - ok) / 60000L) + " min ago"
            else -> "Waiting for first sync"
        }
    }

    /** Debounced: at most one upload per 45 s, always the latest log. */
    fun schedule(delayMs: Long = 45_000L) {
        if (!connected()) return
        synchronized(this) {
            if (job?.isActive == true) return
            job = scope.launch {
                delay(delayMs)
                pushNow()
            }
        }
    }

    private fun pushNow() {
        val p = prefs ?: return
        if (p.getBoolean("paused", false)) return
        val id = p.getString("install_id", null) ?: return
        try {
            val events = MoodRuntime.store()?.all().orEmpty().takeLast(2000)
            val body = gson.toJson(mapOf("updatedAt" to System.currentTimeMillis(), "count" to events.size, "events" to events))
            val req = Request.Builder().url(ENDPOINT + id).put(body.toRequestBody(json)).build()
            client.newCall(req).execute().use { r ->
                if (r.isSuccessful) p.edit().putLong("last_ok", System.currentTimeMillis()).remove("last_error").apply()
                else p.edit().putString("last_error", "HTTP ${r.code}").apply()
            }
        } catch (e: Exception) {
            p.edit().putString("last_error", e.javaClass.simpleName).apply()
        }
    }
}
