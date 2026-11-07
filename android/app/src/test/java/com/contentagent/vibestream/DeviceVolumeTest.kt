package com.contentagent.vibestream

import com.contentagent.vibestream.player.DeviceVolume
import org.junit.Assert.*
import org.junit.Test

class DeviceVolumeTest {
    @Test fun fractionMapsFullRange() {
        assertEquals(0f, DeviceVolume.sliderFraction(0, 15), 1e-6f)
        assertEquals(1f, DeviceVolume.sliderFraction(15, 15), 1e-6f)
        assertEquals(0.5f, DeviceVolume.sliderFraction(7, 14), 1e-6f)
    }

    @Test fun fractionClampsOutOfRange() {
        assertEquals(1f, DeviceVolume.sliderFraction(20, 15), 1e-6f)
        assertEquals(0f, DeviceVolume.sliderFraction(-3, 15), 1e-6f)
        assertEquals(0f, DeviceVolume.sliderFraction(3, 0), 1e-6f)
    }

    @Test fun volumeForFractionRoundsToNearestStep() {
        assertEquals(0, DeviceVolume.volumeForFraction(0f, 15))
        assertEquals(15, DeviceVolume.volumeForFraction(1f, 15))
        assertEquals(8, DeviceVolume.volumeForFraction(0.5f, 15))
    }

    @Test fun volumeForFractionClamps() {
        assertEquals(15, DeviceVolume.volumeForFraction(1.5f, 15))
        assertEquals(0, DeviceVolume.volumeForFraction(-0.5f, 15))
        assertEquals(0, DeviceVolume.volumeForFraction(0.5f, 0))
    }

    @Test fun roundTripThroughSliderKeepsEveryStep() {
        for (v in 0..25) {
            assertEquals(v, DeviceVolume.volumeForFraction(DeviceVolume.sliderFraction(v, 25), 25))
        }
    }
}
