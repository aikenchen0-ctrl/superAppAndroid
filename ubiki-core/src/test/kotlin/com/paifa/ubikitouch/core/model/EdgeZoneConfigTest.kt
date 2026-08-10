package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeZoneConfigTest {
    @Test
    fun sanitizedTriggerBarThicknessAllowsOneDpMinimum() {
        assertEquals(1, EdgeZoneConfig(side = EdgeSide.LEFT, thicknessDp = 0).sanitized().thicknessDp)
        assertEquals(1, EdgeZoneConfig(side = EdgeSide.LEFT, thicknessDp = 1).sanitized().thicknessDp)
        assertEquals(8, EdgeZoneConfig(side = EdgeSide.LEFT, thicknessDp = 8).sanitized().thicknessDp)
        assertEquals(96, EdgeZoneConfig(side = EdgeSide.LEFT, thicknessDp = 120).sanitized().thicknessDp)
    }

    @Test
    fun edgeInsetDpIsClampedToSupportedRange() {
        assertEquals(0, sanitizeEdgeInsetDp(-4))
        assertEquals(12, sanitizeEdgeInsetDp(12))
        assertEquals(EdgeZoneConfig.MAX_EDGE_INSET_DP, sanitizeEdgeInsetDp(200))
    }

    @Test
    fun eachZoneSanitizesItsOwnEdgeInsetAndNewZonesDefaultToZero() {
        val first = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 0,
            edgeInsetDp = -4
        ).sanitized()
        val second = EdgeZoneConfig(
            side = EdgeSide.LEFT,
            zoneId = 1,
            edgeInsetDp = 200
        ).sanitized()

        assertEquals(0, first.edgeInsetDp)
        assertEquals(EdgeZoneConfig.MAX_EDGE_INSET_DP, second.edgeInsetDp)
        assertEquals(0, EdgeZoneConfig.defaultFor(EdgeSide.LEFT, 2).edgeInsetDp)
    }

    @Test
    fun firstLeftAndRightZonesDefaultToConfiguredTopAndBottomInsets() {
        listOf(EdgeSide.LEFT, EdgeSide.RIGHT).forEach { side ->
            val firstZone = EdgeZoneConfig.defaultFor(side, EdgeZoneConfig.DEFAULT_ZONE_ID)

            assertEquals(40, firstZone.topInsetPercent)
            assertEquals(20, firstZone.bottomInsetPercent)
        }
    }
}
