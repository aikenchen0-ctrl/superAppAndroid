package com.paifa.univerge.gesture.server

import com.paifa.univerge.core.model.EdgeSide
import kotlin.math.roundToInt

internal data class GestureServerOverlayRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    init {
        require(right > left) { "right must be greater than left" }
        require(bottom > top) { "bottom must be greater than top" }
    }
}

sealed interface GestureServerRegion {
    data class Side(val side: EdgeSide, val zoneId: Int) : GestureServerRegion
    data object Bottom : GestureServerRegion
}

internal data class GestureServerOverlaySpec(
    val region: GestureServerRegion,
    val rect: GestureServerOverlayRect
)

internal fun gestureServerOverlayPlan(snapshot: GestureServerSnapshot): List<GestureServerOverlaySpec> {
    val sideSpecs = buildList {
        snapshot.leftZones.filter(GestureServerZone::enabled).forEach { zone ->
            add(
                GestureServerOverlaySpec(
                    region = GestureServerRegion.Side(EdgeSide.LEFT, zone.zoneId),
                    rect = gestureServerEdgeRect(snapshot, EdgeSide.LEFT, zone)
                )
            )
        }
        snapshot.rightZones.filter(GestureServerZone::enabled).forEach { zone ->
            add(
                GestureServerOverlaySpec(
                    region = GestureServerRegion.Side(EdgeSide.RIGHT, zone.zoneId),
                    rect = gestureServerEdgeRect(snapshot, EdgeSide.RIGHT, zone)
                )
            )
        }
    }
    return sideSpecs + GestureServerOverlaySpec(GestureServerRegion.Bottom, gestureServerBottomRect(snapshot))
}

internal fun gestureServerEdgeRect(
    snapshot: GestureServerSnapshot,
    side: EdgeSide,
    zone: GestureServerZone
): GestureServerOverlayRect {
    val density = snapshot.density.coerceAtLeast(0.01f)
    val screenWidthPx = (snapshot.screenWidthDp * density).roundToInt().coerceAtLeast(1)
    val screenHeightPx = (snapshot.screenHeightDp * density).roundToInt().coerceAtLeast(1)
    val top = (zone.startDp * density).roundToInt().coerceIn(0, screenHeightPx - 1)
    val bottom = ((zone.startDp + zone.lengthDp) * density)
        .roundToInt()
        .coerceIn(top + 1, screenHeightPx)
    val width = (zone.thicknessDp.coerceAtLeast(MIN_SERVER_TOUCH_WIDTH_DP) * density)
        .roundToInt()
        .coerceIn(1, screenWidthPx)
    val inset = (zone.edgeInsetDp.coerceAtLeast(0f) * density)
        .roundToInt()
        .coerceIn(0, (screenWidthPx - 1).coerceAtLeast(0))
    val left = when (side) {
        EdgeSide.LEFT -> inset.coerceAtMost(screenWidthPx - width)
        EdgeSide.RIGHT -> (screenWidthPx - inset - width).coerceAtLeast(0)
    }
    return GestureServerOverlayRect(left, top, left + width, bottom)
}

internal fun gestureServerBottomRect(snapshot: GestureServerSnapshot): GestureServerOverlayRect {
    val density = snapshot.density.coerceAtLeast(0.01f)
    val screenWidthPx = (snapshot.screenWidthDp * density).roundToInt().coerceAtLeast(1)
    val screenHeightPx = (snapshot.screenHeightDp * density).roundToInt().coerceAtLeast(1)
    val width = (snapshot.bottomWidthDp * density).roundToInt().coerceIn(1, screenWidthPx)
    val height = (snapshot.bottomHeightDp * density).roundToInt().coerceIn(1, screenHeightPx)
    val left = ((screenWidthPx - width) / 2f).roundToInt()
    return GestureServerOverlayRect(left, screenHeightPx - height, left + width, screenHeightPx)
}

internal const val MIN_SERVER_TOUCH_WIDTH_DP = 8f
