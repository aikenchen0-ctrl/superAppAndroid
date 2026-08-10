package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.GestureData
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeOverlayCoordinateTest {
    @Test
    fun localOverlayGestureCoordinatesIncludeTheSideInsetWindowOrigin() {
        val screenData = GestureData(
            startX = 3f,
            startY = 12f,
            endX = 80f,
            endY = 72f
        ).toScreenCoordinates(originX = 48, originY = 360)

        assertEquals(51f, screenData.startX, 0.001f)
        assertEquals(372f, screenData.startY, 0.001f)
        assertEquals(128f, screenData.endX, 0.001f)
        assertEquals(432f, screenData.endY, 0.001f)
    }

    @Test
    fun visualBackWaveStaysAtThePhysicalEdgeWhenTheTouchWindowIsInset() {
        val left = physicalEdgeBackWaveAnchor(
            side = com.paifa.ubikitouch.core.model.EdgeSide.LEFT,
            screenWidthPx = 1080,
            y = 360,
            height = 1200
        )
        val right = physicalEdgeBackWaveAnchor(
            side = com.paifa.ubikitouch.core.model.EdgeSide.RIGHT,
            screenWidthPx = 1080,
            y = 840,
            height = 1200
        )

        assertEquals(0, left.edgeX)
        assertEquals(1080, right.edgeX)
        assertEquals(360, left.y)
        assertEquals(1200, right.height)
    }
}
