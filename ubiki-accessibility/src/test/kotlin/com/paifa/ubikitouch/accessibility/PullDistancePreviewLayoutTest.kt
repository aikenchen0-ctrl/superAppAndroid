package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class PullDistancePreviewLayoutTest {
    @Test
    fun previewStacksHorizontalLinesWithASharedLeftAnchor() {
        val layout = pullDistancePreviewLayout(
            screenWidthPx = 1_080,
            screenHeightPx = 2_400,
            density = 1f,
            shortDistanceDp = 40,
            longDistanceDp = 160
        )

        assertEquals(32f, layout.shortLine.startX, 0.001f)
        assertEquals(1_104f, layout.shortLine.startY, 0.001f)
        assertEquals(72f, layout.shortLine.endX, 0.001f)
        assertEquals(1_104f, layout.shortLine.endY, 0.001f)
        assertEquals(32f, layout.longLine.startX, 0.001f)
        assertEquals(1_152f, layout.longLine.startY, 0.001f)
        assertEquals(192f, layout.longLine.endX, 0.001f)
        assertEquals(1_152f, layout.longLine.endY, 0.001f)
        assertEquals(40f, layout.shortLine.length, 0.001f)
        assertEquals(160f, layout.longLine.length, 0.001f)
    }
}
