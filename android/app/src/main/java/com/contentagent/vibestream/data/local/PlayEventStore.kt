package com.contentagent.vibestream.data.local

import android.content.Context
import com.contentagent.vibestream.data.model.Song
import com.contentagent.vibestream.data.recommendation.MoodEngine
import com.contentagent.vibestream.data.recommendation.PlayEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Play log on the device only: capped at 90 days and 3000 events, clearable. */
class PlayEventStore(context: Context) {
    private val prefs = context.getSharedPreferences("vibestream_play_events", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var cache: MutableList<PlayEvent>? = null

    @Synchronized private fun loaded(): MutableList<PlayEvent> {
        cache?.let { return it }
        val json = prefs.getString("events", null)
        val list: MutableList<PlayEvent> = try {
            if (json == null) mutableListOf()
            else (gson.fromJson<List<PlayEvent>>(json, object : TypeToken<List<PlayEvent>>() {}.type) ?: emptyList()).toMutableList()
        } catch (e: Exception) { mutableListOf() }
        cache = list
        return list
    }

    @Synchronized fun all(): List<PlayEvent> = loaded().toList()

    @Synchronized fun append(e: PlayEvent, now: Long = System.currentTimeMillis()) {
        val list = loaded()
        list.add(e)
        val cutoff = now - 90L * 86_400_000L
        list.removeAll { it.startMs < cutoff }
        while (list.size > 3000) list.removeAt(0)
        prefs.edit().putString("events", gson.toJson(list)).apply()
    }

    @Synchronized fun overrides(): Map<String, String> {
        val json = prefs.getString("overrides", null) ?: return emptyMap()
        return try {
            gson.fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    @Synchronized fun setOverride(stateKey: String, moodName: String) {
        val m = overrides().toMutableMap()
        m[stateKey] = moodName
        prefs.edit().putString("overrides", gson.toJson(m)).apply()
    }

    @Synchronized fun clear() {
        cache = mutableListOf()
        prefs.edit().clear().apply()
    }
}

data class PlaySnapshot(
    val hour: Int = 12, val weekend: Boolean = false, val route: String = "speaker",
    val charging: Boolean = false, val motion: String = "unknown"
)

/** Turns "song started / song ended" calls into one PlayEvent per play. Pure logic. */
class PlayEventTracker(
    private val sink: (PlayEvent) -> Unit,
    private val snapshot: () -> PlaySnapshot,
    private val isLiked: (String) -> Boolean = { false }
) {
    private var song: Song? = null
    private var startedAtMs = 0L
    private var snap = PlaySnapshot()
    private var source = ""

    /** [next] has started. The previous song's listened time and duration close its event. */
    fun begin(next: Song, prevListenedMs: Long, prevDurationMs: Long, auto: Boolean, source: String, nowMs: Long) {
        val cur = song
        if (cur != null && cur.id == next.id) {
            val dur = if (prevDurationMs > 0) prevDurationMs else cur.durationSec * 1000L
            // A retry or stream recovery of the same song is not a new play.
            if (!(dur > 0 && prevListenedMs >= dur / 2)) return
            close(cur, prevListenedMs, prevDurationMs, auto, replay = true)
        } else if (cur != null) {
            close(cur, prevListenedMs, prevDurationMs, auto, replay = false)
        }
        song = next
        startedAtMs = nowMs
        snap = snapshot()
        this.source = source
    }

    private fun close(cur: Song, listenedMs: Long, durationMs: Long, auto: Boolean, replay: Boolean) {
        val listened = (listenedMs / 1000L).toInt()
        if (listened <= 0 && !auto) return
        val dur = if (durationMs > 0) (durationMs / 1000L).toInt() else cur.durationSec.toInt()
        val outcome = when {
            replay -> MoodEngine.REPLAYED
            auto -> MoodEngine.FINISHED
            dur > 0 && listened >= dur * 0.9 -> MoodEngine.FINISHED
            listened < 30 -> MoodEngine.SKIP_EARLY
            else -> MoodEngine.SKIP_LATE
        }
        sink(PlayEvent(
            songId = cur.id, artist = cur.artist, title = cur.title, startMs = startedAtMs,
            listenedSec = listened, durationSec = dur, outcome = outcome, source = source,
            liked = isLiked(cur.id), hour = snap.hour, weekend = snap.weekend, route = snap.route,
            charging = snap.charging, motion = snap.motion
        ))
    }
}
