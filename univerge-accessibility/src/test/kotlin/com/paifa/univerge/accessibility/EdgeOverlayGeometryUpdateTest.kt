package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeOverlayGeometryUpdateTest {
    @Test
    fun geometryChangesUpdateExistingWindowsWhenIdle() {
        assertEquals(
            EdgeOverlayGeometryUpdateDecision.UPDATE_IN_PLACE,
            decideEdgeOverlayGeometryUpdate(
                sameZoneSet = true,
                activeGestureCount = 0,
                inputOwnerChanged = false,
                windowTypeChanged = false
            )
        )
    }

    @Test
    fun geometryChangesDeferUntilTheActiveGestureFinishes() {
        assertEquals(
            EdgeOverlayGeometryUpdateDecision.DEFER,
            decideEdgeOverlayGeometryUpdate(
                sameZoneSet = true,
                activeGestureCount = 1,
                inputOwnerChanged = false,
                windowTypeChanged = false
            )
        )
    }

    @Test
    fun ownerOrZoneChangesRequireRecreation() {
        assertEquals(
            EdgeOverlayGeometryUpdateDecision.RECREATE,
            decideEdgeOverlayGeometryUpdate(
                sameZoneSet = false,
                activeGestureCount = 0,
                inputOwnerChanged = false,
                windowTypeChanged = false
            )
        )
        assertEquals(
            EdgeOverlayGeometryUpdateDecision.RECREATE,
            decideEdgeOverlayGeometryUpdate(
                sameZoneSet = true,
                activeGestureCount = 0,
                inputOwnerChanged = true,
                windowTypeChanged = false
            )
        )
    }
}
