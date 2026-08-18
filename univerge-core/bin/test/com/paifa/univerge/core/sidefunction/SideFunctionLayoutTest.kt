package com.paifa.univerge.core.sidefunction

import org.junit.Assert.assertEquals
import org.junit.Test

class SideFunctionLayoutTest {
    @Test
    fun layoutCreatesOneBoxForEachActionAndHitTestsItsIndex() {
        val layout = sideFunctionLayout(itemCount = 4, progress = 1f, density = 1f)

        assertEquals(4, layout.boxes.size)
        assertEquals(2, layout.hitTest(inwardDistancePx = 112f, verticalOffsetPx = 40f))
    }

    @Test
    fun emptyLayoutNeverReportsHit() {
        assertEquals(null, sideFunctionLayout(0, 1f, 1f).hitTest(112f, 0f))
    }
}
