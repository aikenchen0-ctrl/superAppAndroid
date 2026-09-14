package com.paifa.univerge.accessibility

internal enum class EdgeOverlayGeometryUpdateDecision {
    UPDATE_IN_PLACE,
    DEFER,
    RECREATE
}

internal fun decideEdgeOverlayGeometryUpdate(
    sameZoneSet: Boolean,
    activeGestureCount: Int,
    inputOwnerChanged: Boolean,
    windowTypeChanged: Boolean
): EdgeOverlayGeometryUpdateDecision {
    if (!sameZoneSet || inputOwnerChanged || windowTypeChanged) {
        return EdgeOverlayGeometryUpdateDecision.RECREATE
    }
    if (activeGestureCount > 0) return EdgeOverlayGeometryUpdateDecision.DEFER
    return EdgeOverlayGeometryUpdateDecision.UPDATE_IN_PLACE
}
