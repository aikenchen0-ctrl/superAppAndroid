package com.paifa.univerge.core.sidefunction

import kotlin.math.abs

data class SideFunctionBox(
    val index: Int,
    val centerInwardPx: Float,
    val centerYOffsetPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val cornerRadiusPx: Float
) {
    fun contains(inwardDistancePx: Float, verticalOffsetPx: Float): Boolean {
        return abs(inwardDistancePx - centerInwardPx) <= widthPx / 2f &&
            abs(verticalOffsetPx - centerYOffsetPx) <= heightPx / 2f
    }
}

data class SideFunctionLayout(
    val boxes: List<SideFunctionBox>
) {
    fun hitTest(inwardDistancePx: Float, verticalOffsetPx: Float): Int? {
        return boxes.firstOrNull { it.contains(inwardDistancePx, verticalOffsetPx) }?.index
    }
}

const val DEFAULT_SIDE_FUNCTION_VERTICAL_SAFE_INSET_DP = 40f

fun sideFunctionGroupShiftY(
    layout: SideFunctionLayout,
    startY: Float,
    viewportHeightPx: Float,
    verticalSafeInsetPx: Float
): Float {
    val first = layout.boxes.firstOrNull() ?: return 0f
    val last = layout.boxes.last()
    val minTop = verticalSafeInsetPx.coerceAtLeast(0f)
    val maxBottom = (viewportHeightPx - minTop).coerceAtLeast(minTop)
    val groupTop = startY + first.centerYOffsetPx - first.heightPx / 2f
    val groupBottom = startY + last.centerYOffsetPx + last.heightPx / 2f
    return when {
        groupTop < minTop -> minTop - groupTop
        groupBottom > maxBottom -> maxBottom - groupBottom
        else -> 0f
    }
}

fun sideFunctionLayout(
    itemCount: Int,
    progress: Float,
    density: Float
): SideFunctionLayout {
    val safeDensity = density.takeIf { it > 0f } ?: return SideFunctionLayout(emptyList())
    val count = itemCount.coerceIn(0, MAX_VISIBLE_SIDE_FUNCTIONS)
    if (count == 0) return SideFunctionLayout(emptyList())

    val expanded = easeOutCubic(progress.coerceIn(0f, 1f))
    val width = ITEM_WIDTH_DP * safeDensity * expanded
    val height = ITEM_HEIGHT_DP * safeDensity * expanded
    val step = (ITEM_HEIGHT_DP + ITEM_SPACING_DP) * safeDensity * expanded
    val firstCenterY = -step * (count - 1) / 2f
    val centerInward = ITEM_CENTER_INWARD_DP * safeDensity * expanded
    val radius = ITEM_CORNER_RADIUS_DP * safeDensity * expanded

    return SideFunctionLayout(
        boxes = List(count) { index ->
            SideFunctionBox(
                index = index,
                centerInwardPx = centerInward,
                centerYOffsetPx = firstCenterY + index * step,
                widthPx = width,
                heightPx = height,
                cornerRadiusPx = radius
            )
        }
    )
}

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value
    return 1f - inverse * inverse * inverse
}

private const val MAX_VISIBLE_SIDE_FUNCTIONS = 7
private const val ITEM_WIDTH_DP = 88f
private const val ITEM_HEIGHT_DP = 44f
private const val ITEM_SPACING_DP = 12f
private const val ITEM_CENTER_INWARD_DP = 112f
private const val ITEM_CORNER_RADIUS_DP = 12f
