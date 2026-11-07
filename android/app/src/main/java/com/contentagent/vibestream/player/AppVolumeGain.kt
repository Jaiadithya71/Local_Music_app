package com.contentagent.vibestream.player

/**
 * The app keeps its own output gain (ExoPlayer's per-player volume), scaling the
 * audio samples before they reach any output. Unlike the system stream volume,
 * this always changes what a Bluetooth device plays - even when the connected
 * device ignores the phone's "absolute volume" link, which made the phone's
 * volume keys appear dead in the car. The sleep-timer fade multiplies this base
 * gain instead of assuming full volume, so the two never fight over the player.
 */
object AppVolumeGain {
    const val FADE_WINDOW_MS = 15_000L

    fun effectiveGain(appVolume: Float, fadeRemainingMs: Long? = null): Float {
        val base = appVolume.coerceIn(0f, 1f)
        if (fadeRemainingMs == null || fadeRemainingMs >= FADE_WINDOW_MS) return base
        return base * (fadeRemainingMs / FADE_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
    }
}
