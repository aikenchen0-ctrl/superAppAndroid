package com.paifa.ubikitouch.overlay

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeIndicatorPlacementTest {
    @Test
    fun indicatorUsesThePhysicalScreenEdgesAndItsFullWidth() {
        val left = edgeIndicatorPlacement(
            side = EdgeSide.LEFT,
            screenWidthPx = 1080,
            indicatorWidthPx = 48
        )
        val right = edgeIndicatorPlacement(
            side = EdgeSide.RIGHT,
            screenWidthPx = 1080,
            indicatorWidthPx = 48
        )

        assertEquals(0, left.x)
        assertEquals(48, left.width)
        assertEquals(1032, right.x)
        assertEquals(48, right.width)
    }
}
