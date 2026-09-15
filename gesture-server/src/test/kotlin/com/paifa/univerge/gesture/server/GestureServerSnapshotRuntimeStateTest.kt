package com.paifa.univerge.gesture.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GestureServerSnapshotRuntimeStateTest {
    @Test
    fun inputEnabledRoundTripsAcrossTheProcessBoundary() {
        val source = snapshot().copy(inputEnabled = false)

        val restored = GestureServerSnapshot.fromBundle(source.toBundle())

        assertEquals(false, restored?.inputEnabled)
        assertTrue(restored?.isValid() == true)
    }

    private fun snapshot() = GestureServerSnapshot(
        version = 1L,
        density = 1f,
        screenWidthDp = 400f,
        screenHeightDp = 800f,
        leftZones = listOf(GestureServerZone(0, true, 0f, 800f, 24f))
    )
}
