package com.contentagent.vibestream.player

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * System Equalizer attached to the player's own audio session (no special
 * permission needed for a session-scoped effect). Attach is lazy: the audio
 * session exists only after the playback service has created its player, so
 * ensure() is retried when playback starts and when the EQ dialog opens.
 */
class EqualizerManager(context: Context) {
    private val prefs = context.getSharedPreferences("vibestream_eq", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Short>>(emptyList())
    val bandLevels: StateFlow<List<Short>> = _bandLevels.asStateFlow()

    val bandLevelRange: Pair<Short, Short>?
        get() = eq?.bandLevelRange?.let { it[0] to it[1] }

    fun bandLabel(band: Int): String {
        val hz = (eq?.getCenterFreq(band.toShort()) ?: 0) / 1000 // API returns millihertz
        return if (hz >= 1000) "${hz / 1000}.${(hz % 1000) / 100}kHz" else "${hz}Hz"
    }

    fun presets(): List<String> = eq?.let { e ->
        (0 until e.numberOfPresets).mapNotNull {
            try { e.getPresetName(it.toShort()) } catch (t: Throwable) { null }
        }
    } ?: emptyList()

    fun currentPresetIndex(): Int = try { eq?.currentPreset?.toInt() ?: -1 } catch (t: Throwable) { -1 }

    /** Attach to the player audio session when it exists. True when ready. */
    @Synchronized
    fun ensure(): Boolean {
        if (eq != null) return true
        val sid = PlaybackService.audioSessionId
        if (sid <= 0) return false
        return try {
            val e = Equalizer(0, sid)
            eq = e
            val n = e.numberOfBands.toInt()
            val saved = prefs.getString("levels", null)
                ?.split(",")?.mapNotNull { it.toShortOrNull() }
            if (saved != null && saved.size == n) {
                for (b in 0 until n) {
                    try { e.setBandLevel(b.toShort(), saved[b]) } catch (t: Throwable) {}
                }
            }
            val on = prefs.getBoolean("enabled", false)
            try { e.enabled = on } catch (t: Throwable) {}
            _enabled.value = on
            _bandLevels.value = (0 until n).map { b ->
                try { e.getBandLevel(b.toShort()) } catch (t: Throwable) { 0 }
            }
            _available.value = true
            true
        } catch (t: Throwable) {
            _available.value = false
            false
        }
    }

    fun setEnabled(on: Boolean) {
        if (!ensure()) return
        try { eq?.enabled = on } catch (t: Throwable) {}
        _enabled.value = on
        prefs.edit().putBoolean("enabled", on).apply()
    }

    fun setBandLevel(band: Int, levelMb: Short) {
        val e = eq ?: return
        try { e.setBandLevel(band.toShort(), levelMb) } catch (t: Throwable) { return }
        val cur = _bandLevels.value.toMutableList()
        if (band in cur.indices) {
            cur[band] = levelMb
            _bandLevels.value = cur
            prefs.edit().putString("levels", cur.joinToString(",")).apply()
        }
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            val n = e.numberOfBands.toInt()
            val levels = (0 until n).map { b ->
                try { e.getBandLevel(b.toShort()) } catch (t: Throwable) { 0 }
            }
            _bandLevels.value = levels
            prefs.edit().putString("levels", levels.joinToString(",")).apply()
        } catch (t: Throwable) {}
    }
}
