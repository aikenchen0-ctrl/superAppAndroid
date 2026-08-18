package com.paifa.univerge.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.EdgeZoneConfig

class EdgeConfigurationPreviewPolicyTest {
    @Test
    fun previewIsOnlyNeededWhenPersistentIndicatorsAreHidden() {
        assertFalse(shouldShowEdgeConfigAdjustmentPreview(showIndicators = true))
        assertTrue(shouldShowEdgeConfigAdjustmentPreview(showIndicators = false))
    }

    @Test
    fun visibleIndicatorsUseThePersistentOutlineForAdjustmentPreview() {
        assertTrue(shouldUpdatePersistentEdgeOutline(showIndicators = true))
        assertFalse(shouldUpdatePersistentEdgeOutline(showIndicators = false))
    }

    @Test
    fun adjustmentPreviewReusesItsWindowForTheSameEdgeZone() {
        val sameZone = EdgeZoneConfig(side = EdgeSide.LEFT, zoneId = 1)
        val differentZone = EdgeZoneConfig(side = EdgeSide.RIGHT, zoneId = 1)

        assertTrue(canReuseEdgeConfigAdjustmentPreview(EdgeSide.LEFT, 1, sameZone))
        assertFalse(canReuseEdgeConfigAdjustmentPreview(EdgeSide.LEFT, 1, differentZone))
    }
}
