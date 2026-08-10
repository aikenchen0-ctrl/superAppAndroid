package com.paifa.ubikitouch.overlay

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeOverlayOutlineColorTest {
    @Test
    fun baseOverlayUsesRedOutlineOnBothSides() {
        assertEquals(0xFFFF3B30.toInt(), edgeOverlayOutlineColorArgb(EdgeSide.LEFT, zoneId = 0))
        assertEquals(0xFFFF3B30.toInt(), edgeOverlayOutlineColorArgb(EdgeSide.RIGHT, zoneId = 0))
    }

    @Test
    fun addedOverlayPaletteHasAtLeastTenDistinctHighContrastColors() {
        assertTrue(EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS.size >= 10)
        assertEquals(
            EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS.size,
            EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS.toSet().size
        )
    }

    @Test
    fun addedOverlaysReceiveDistinctColorsForEveryVisibleSideAndLayer() {
        val addedOverlayColors = listOf(
            edgeOverlayOutlineColorArgb(EdgeSide.LEFT, zoneId = 1),
            edgeOverlayOutlineColorArgb(EdgeSide.RIGHT, zoneId = 1),
            edgeOverlayOutlineColorArgb(EdgeSide.LEFT, zoneId = 2),
            edgeOverlayOutlineColorArgb(EdgeSide.RIGHT, zoneId = 2),
            edgeOverlayOutlineColorArgb(EdgeSide.LEFT, zoneId = 3),
            edgeOverlayOutlineColorArgb(EdgeSide.RIGHT, zoneId = 3)
        )

        assertEquals(addedOverlayColors.size, addedOverlayColors.toSet().size)
        assertNotEquals(0xFFFF3B30.toInt(), addedOverlayColors.first())
    }
}
