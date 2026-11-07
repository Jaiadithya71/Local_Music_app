package com.contentagent.vibestream.player

import kotlin.math.roundToInt

/**
 * Maps the Android media-stream volume (what the phone's hardware buttons and
 * the system volume panel show) onto a 0..1 slider fraction and back. The
 * in-app volume slider is this same volume, so the slider, the buttons and the
 * system panel always agree; there is no separate app-only level that could
 * drift out of sync or hide an attenuation.
 */
object DeviceVolume {
    fun sliderFraction(volume: Int, max: Int): Float =
        if (max <= 0) 0f else volume.coerceIn(0, max) / max.toFloat()

    fun volumeForFraction(fraction: Float, max: Int): Int =
        if (max <= 0) 0 else (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
}
