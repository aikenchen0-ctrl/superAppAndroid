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
}
