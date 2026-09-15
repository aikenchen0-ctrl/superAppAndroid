package com.paifa.univerge.gesture.server

import com.paifa.univerge.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureServerOverlayGeometryTest {
    @Test
    fun sideRectUsesTheEffectiveMinimumTouchWidthAndMirrorsTheRightEdge() {
        val snapshot = snapshot().copy(
            leftZones = listOf(
                GestureServerZone(
                    zoneId = 0,
                    enabled = true,
                    startDp = 100f,
                    lengthDp = 200f,
                    thicknessDp = 1f,
                    edgeInsetDp = 4f
                )
            ),
            rightZones = listOf(
                GestureServerZone(
                    zoneId = 1,
                    enabled = true,
                    startDp = 50f,
                    lengthDp = 100f,
                    thicknessDp = 24f,
                    edgeInsetDp = 8f
                )
            )
        )

        val left = gestureServerEdgeRect(snapshot, EdgeSide.LEFT, snapshot.leftZones.single())
        val right = gestureServerEdgeRect(snapshot, EdgeSide.RIGHT, snapshot.rightZones.single())

        assertEquals(16, left.right - left.left)
        assertEquals(8, left.left)
        assertEquals(200, left.top)
        assertEquals(600, left.bottom)
        assertEquals(896, right.left)
        assertEquals(944, right.right)
        assertTrue(right.top < right.bottom)
    }

    @Test
    fun bottomRectIsCenteredAndUsesSnapshotWidthAndHeight() {
        val rect = gestureServerBottomRect(snapshot().copy(bottomWidthDp = 200f, bottomHeightDp = 40f))

        assertEquals(280, rect.left)
        assertEquals(680, rect.right)
        assertEquals(1520, rect.top)
        assertEquals(1600, rect.bottom)
    }

    private fun snapshot() = GestureServerSnapshot(
        version = 1L,
        density = 2f,
        screenWidthDp = 480f,
        screenHeightDp = 800f
    )
}
