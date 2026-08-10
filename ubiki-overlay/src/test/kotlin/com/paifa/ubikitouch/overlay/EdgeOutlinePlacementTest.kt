package com.paifa.ubikitouch.overlay

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeOutlinePlacementTest {
    @Test
    fun outlineRespectsInsetButStaysWithinScreenBounds() {
        val left = edgeOutlinePlacement(
            side = EdgeSide.LEFT,
            screenWidthPx = 1080,
            outlineWidthPx = 48,
            edgeInsetPx = 36
        )
        val right = edgeOutlinePlacement(
            side = EdgeSide.RIGHT,
            screenWidthPx = 1080,
            outlineWidthPx = 48,
            edgeInsetPx = 36
        )

        assertEquals(36, left.x)
        assertEquals(48, left.width)
        assertEquals(996, right.x)
        assertEquals(48, right.width)
    }
}
