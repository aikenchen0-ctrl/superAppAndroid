package com.paifa.ubikitouch.accessibility.floatingchat.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAspectRatioTest {
    @Test
    fun preservesLandscapeRatio() {
        assertEquals(16f / 9f, videoAspectRatioFromDimensions(1600, 900)!!, 0.0001f)
    }

    @Test
    fun swapsDimensionsForQuarterTurnRotation() {
        assertEquals(9f / 16f, videoAspectRatioFromDimensions(1600, 900, 90)!!, 0.0001f)
    }

    @Test
    fun clampsNonPositiveDimensionsToAValidRatio() {
        assertEquals(1f, videoAspectRatioFromDimensions(0, -1)!!, 0.0001f)
    }
}
