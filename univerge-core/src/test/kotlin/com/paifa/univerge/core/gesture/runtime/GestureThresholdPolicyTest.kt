package com.paifa.univerge.core.gesture.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureThresholdPolicyTest {
    @Test
    fun sideBackendsUseTheSameEffectiveThresholdsForConfiguredDp() {
        val thresholds = configuredSideGestureThresholdsDp(
            shortPullDistanceDp = 45f,
            longPullDistanceDp = 175f
        )

        assertEquals(31.5f, thresholds.minPullDistanceDp, 0.001f)
        assertEquals(122.5f, thresholds.longPullDistanceDp, 0.001f)
        assertEquals(31.5f, thresholds.minSwipeDistanceDp, 0.001f)
    }

    @Test
    fun sidePolicyKeepsLongThresholdAboveShortAfterSanitizingInput() {
        val thresholds = configuredSideGestureThresholdsDp(
            shortPullDistanceDp = 120f,
            longPullDistanceDp = 1f
        )

        assertEquals(84f, thresholds.minPullDistanceDp, 0.001f)
        assertEquals(89.6f, thresholds.longPullDistanceDp, 0.001f)
        assertEquals(84f, thresholds.minSwipeDistanceDp, 0.001f)
    }
}
