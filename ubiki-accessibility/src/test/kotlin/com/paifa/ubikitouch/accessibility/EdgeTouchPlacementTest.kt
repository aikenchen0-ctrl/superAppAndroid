package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeTouchPlacementTest {
    @Test
    fun touchPlacementAlwaysUsesPhysicalScreenEdge() {
        assertEquals(0, edgeTouchX(EdgeSide.LEFT, screenWidthPx = 1080, touchWidthPx = 32))
        assertEquals(1048, edgeTouchX(EdgeSide.RIGHT, screenWidthPx = 1080, touchWidthPx = 32))
    }
}
