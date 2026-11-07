package com.contentagent.vibestream

import com.contentagent.vibestream.player.AppVolumeGain
import org.junit.Assert.*
import org.junit.Test

class AppVolumeGainTest {
    @Test fun baseGainPassesThroughWithoutFade() {
        assertEquals(0.7f, AppVolumeGain.effectiveGain(0.7f), 1e-6f)
        assertEquals(1f, AppVolumeGain.effectiveGain(1f), 1e-6f)
        assertEquals(0f, AppVolumeGain.effectiveGain(0f), 1e-6f)
    }

    @Test fun fadeMultipliesTheUsersBaseGain() {
        // Halfway through the fade window at 80% base gain must land at 40%,
        // never at the raw 50% that assumed a full-volume baseline.
        assertEquals(0.4f, AppVolumeGain.effectiveGain(0.8f, AppVolumeGain.FADE_WINDOW_MS / 2), 1e-6f)
    }

    @Test fun outOfRangeValuesAreClamped() {
        assertEquals(1f, AppVolumeGain.effectiveGain(1.4f), 1e-6f)
        assertEquals(0f, AppVolumeGain.effectiveGain(-0.2f), 1e-6f)
        assertEquals(0f, AppVolumeGain.effectiveGain(1f, -5), 1e-6f)
    }

    @Test fun fadeEndSilencesEvenFullBaseGain() {
        assertEquals(0f, AppVolumeGain.effectiveGain(1f, 0), 1e-6f)
    }

    @Test fun fadeBeyondWindowLeavesBaseGainUntouched() {
        assertEquals(0.6f, AppVolumeGain.effectiveGain(0.6f, AppVolumeGain.FADE_WINDOW_MS), 1e-6f)
        assertEquals(0.6f, AppVolumeGain.effectiveGain(0.6f, AppVolumeGain.FADE_WINDOW_MS + 4_000), 1e-6f)
    }
}
