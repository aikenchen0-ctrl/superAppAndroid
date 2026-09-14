package com.paifa.univerge.heavydrag.core

import kotlin.math.hypot

/** A point in the coordinate space owned by one drag coordinator. */
data class HeavyPoint(
    val x: Float,
    val y: Float
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }

    fun distanceTo(other: HeavyPoint): Float = hypot(x - other.x, y - other.y)
}

/** An axis-aligned rectangle. Coordinates are inclusive for containment checks. */
data class HeavyRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left.isFinite()) { "left must be finite" }
        require(top.isFinite()) { "top must be finite" }
        require(right.isFinite()) { "right must be finite" }
        require(bottom.isFinite()) { "bottom must be finite" }
        require(right >= left) { "right must be >= left" }
        require(bottom >= top) { "bottom must be >= top" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val center: HeavyPoint get() = HeavyPoint((left + right) / 2f, (top + bottom) / 2f)

    fun contains(point: HeavyPoint): Boolean {
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
    }

    fun translatedBy(dx: Float, dy: Float): HeavyRect {
        return HeavyRect(left + dx, top + dy, right + dx, bottom + dy)
    }

    fun intersection(other: HeavyRect): HeavyRect? {
        val intersectionLeft = maxOf(left, other.left)
        val intersectionTop = maxOf(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return null
        }
        return HeavyRect(
            left = intersectionLeft,
            top = intersectionTop,
            right = intersectionRight,
            bottom = intersectionBottom
        )
    }

    fun intersectionArea(other: HeavyRect): Float {
        val overlapWidth = minOf(right, other.right) - maxOf(left, other.left)
        val overlapHeight = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (overlapWidth <= 0f || overlapHeight <= 0f) return 0f
        return overlapWidth * overlapHeight
    }

    /** The portion of this rectangle covered by [other], constrained to 0..1. */
    fun overlapRatio(other: HeavyRect): Float {
        if (area <= 0f) return 0f
        return (intersectionArea(other) / area).coerceIn(0f, 1f)
    }
}
