package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.abs
import kotlin.math.atan2

class SwipeClassifier(
    private val diagonalMinAngle: Float = 25f,
    private val diagonalMaxAngle: Float = 65f,
    private val verticalSwipeRatio: Float = 2.75f,
    private val diagonalPullsEnabled: Boolean = false
) {
    fun classify(side: EdgeSide, dx: Float, dy: Float): GestureType? {
        val absX = abs(dx)
        val absY = abs(dy)
        if (absX == 0f && absY == 0f) return null

        if (isVerticalSwipe(absX, absY)) {
            return if (dy < 0) GestureType.SWIPE_UP else GestureType.SWIPE_DOWN
        }

        if (!isInwardSwipe(side, dx)) return null

        if (!diagonalPullsEnabled) return GestureType.PULL_INWARD_SHORT

        val angle = angleFromHorizontal(absX, absY)
        return when {
            angle in diagonalMinAngle..diagonalMaxAngle -> {
                if (dy < 0) GestureType.PULL_DIAGONAL_UP else GestureType.PULL_DIAGONAL_DOWN
            }
            else -> GestureType.PULL_INWARD_SHORT
        }
    }

    private fun isVerticalSwipe(absX: Float, absY: Float): Boolean {
        return absY > absX * verticalSwipeRatio
    }

    private fun isInwardSwipe(side: EdgeSide, dx: Float): Boolean {
        return when (side) {
            EdgeSide.LEFT -> dx > 0f
            EdgeSide.RIGHT -> dx < 0f
        }
    }

    private fun angleFromHorizontal(absX: Float, absY: Float): Float {
        return Math.toDegrees(atan2(absY.toDouble(), absX.toDouble())).toFloat()
    }
}
