package com.paifa.univerge.gesture.server

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerOverlayPlanTest {
    @Test
    fun planContainsOnlyEnabledSideZonesAndOneIndependentBottomRegion() {
        val snapshot = GestureServerSnapshot(
            version = 1L,
            density = 1f,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            leftZones = listOf(
                GestureServerZone(0, true, 0f, 200f, 24f),
                GestureServerZone(1, false, 200f, 200f, 24f)
            ),
            rightZones = listOf(GestureServerZone(2, true, 300f, 100f, 24f))
        )

        val plan = gestureServerOverlayPlan(snapshot)

        assertEquals(
            listOf(
                GestureServerRegion.Side(EdgeSide.LEFT, 0),
                GestureServerRegion.Side(EdgeSide.RIGHT, 2),
                GestureServerRegion.Bottom
            ),
            plan.map { it.region }
        )
        assertTrue(plan.all { it.rect.right > it.rect.left && it.rect.bottom > it.rect.top })
    }
}
