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
import java.util.concurrent.TimeUnit

/**
 * Optional listening-log sync to a secret GitHub Gist in HIS account. The token lives
 * only on the phone (never in the APK). Without a token nothing is sent anywhere.
 * The gist holds the play log only: song, artist, seconds listened, outcome, source, context.
 */
object ListeningSync {
    private const val FILE = "vibestream_play_log.json"
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var prefs: android.content.SharedPreferences? = null
    private var job: Job? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("vibestream_listening_sync", Context.MODE_PRIVATE)
        schedule()
    }

    fun connected(): Boolean = !prefs?.getString("token", null).isNullOrBlank()
    fun gistUrl(): String? = prefs?.getString("gist_url", null)
    fun status(): String {
        val p = prefs ?: return "Not set up"
        if (p.getString("token", null).isNullOrBlank()) return "Not connected"
        val ok = p.getLong("last_ok", 0L)
        val err = p.getString("last_error", null)
        return when {
            err != null -> "Last sync failed: $err"
            ok > 0 -> "Synced " + ((System.currentTimeMillis() - ok) / 60000L) + " min ago"
            else -> "Connected, waiting for first sync"
        }
    }

    fun connect(token: String) {
        prefs?.edit()?.putString("token", token.trim())?.remove("last_error")?.apply()
        schedule(delayMs = 500L)
    }

    fun disconnect() { prefs?.edit()?.clear()?.apply() }

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
        val token = p.getString("token", null)?.takeIf { it.isNotBlank() } ?: return
        try {
            val events = MoodRuntime.store()?.all().orEmpty().takeLast(2000)
            val payload = mapOf(
                "updatedAt" to System.currentTimeMillis(),
                "count" to events.size,
                "events" to events
            )
            val content = gson.toJson(payload)
            val files = mapOf(FILE to mapOf("content" to content))
            val id = p.getString("gist_id", null)
            val body = if (id == null)
                gson.toJson(mapOf("description" to "VibeStream listening log", "public" to false, "files" to files))
            else gson.toJson(mapOf("files" to files))
            val url = if (id == null) "https://api.github.com/gists" else "https://api.github.com/gists/$id"
            val b = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            val req = (if (id == null) b.post(body.toRequestBody(json)) else b.patch(body.toRequestBody(json))).build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    val m = gson.fromJson(text, Map::class.java)
                    val e = p.edit()
                    (m["id"] as? String)?.let { e.putString("gist_id", it) }
                    (m["html_url"] as? String)?.let { e.putString("gist_url", it) }
                    e.putLong("last_ok", System.currentTimeMillis()).remove("last_error").apply()
                } else {
                    p.edit().putString("last_error", "HTTP ${r.code}" + if (r.code == 401 || r.code == 404) " (check the token has the gist permission)" else "").apply()
                }
            }
        } catch (e: Exception) {
            p.edit().putString("last_error", e.javaClass.simpleName).apply()
        }
    }
}
