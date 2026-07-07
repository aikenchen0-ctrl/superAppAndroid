package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureType
import kotlin.math.abs
import kotlin.math.hypot

data class BackGestureProgress(
    val side: EdgeSide,
    val dragDistancePx: Float,
    val thresholdPx: Float,
    val longThresholdPx: Float = thresholdPx * 3f,
    val touchY: Float = 0f,
    val startY: Float = touchY,
    val gestureType: GestureType = GestureType.PULL_INWARD_SHORT
) {
    val progress: Float =
        if (thresholdPx <= 0f) 1f else (dragDistancePx / thresholdPx).coerceIn(0f, 1f)

    val visualProgress: Float =
        if (longThresholdPx <= 0f) 1f else (dragDistancePx / longThresholdPx).coerceIn(0f, 1f)

    val committed: Boolean = dragDistancePx > 0f

    val longCommitted: Boolean = dragDistancePx >= longThresholdPx

    companion object {
        fun fromDelta(
            side: EdgeSide,
            dx: Float,
            dy: Float,
            thresholdPx: Float,
            longThresholdPx: Float = thresholdPx * DEFAULT_LONG_THRESHOLD_RATIO,
            touchY: Float = 0f,
            startY: Float = touchY,
            verticalSwipeRatio: Float = DEFAULT_VERTICAL_SWIPE_RATIO,
            minimumDragDistancePx: Float = 0f
        ): BackGestureProgress? {
            if (!isInward(side, dx)) return null
            val absX = abs(dx)
            val absY = abs(dy)
            if (absX < minimumDragDistancePx.coerceAtLeast(0f)) return null
            if (absX == 0f || absY > absX * verticalSwipeRatio) return null
            val sanitizedThresholdPx = thresholdPx.coerceAtLeast(1f)
            val sanitizedLongThresholdPx = longThresholdPx.coerceAtLeast(sanitizedThresholdPx)
            val dragDistancePx = hypot(absX, absY)
            return BackGestureProgress(
                side = side,
                dragDistancePx = dragDistancePx,
                thresholdPx = sanitizedThresholdPx,
                longThresholdPx = sanitizedLongThresholdPx,
                touchY = touchY,
                startY = startY,
                gestureType = gestureTypeForSlope(
                    dy = dy,
                    absX = absX,
                    dragDistancePx = dragDistancePx,
                    longThresholdPx = sanitizedLongThresholdPx
                )
            )
        }

        private fun isInward(side: EdgeSide, dx: Float): Boolean {
            return when (side) {
                EdgeSide.LEFT -> dx > 0f
                EdgeSide.RIGHT -> dx < 0f
            }
        }

        private fun gestureTypeForSlope(
            dy: Float,
            absX: Float,
            dragDistancePx: Float,
            longThresholdPx: Float
        ): GestureType {
            val slope = dy / absX
            return when {
                slope < -SLOPE_SECTOR_THRESHOLD -> if (dragDistancePx >= longThresholdPx) {
                    GestureType.PULL_DIAGONAL_UP_LONG
                } else {
                    GestureType.PULL_DIAGONAL_UP_SHORT
                }
                slope > SLOPE_SECTOR_THRESHOLD -> if (dragDistancePx >= longThresholdPx) {
                    GestureType.PULL_DIAGONAL_DOWN_LONG
                } else {
                    GestureType.PULL_DIAGONAL_DOWN_SHORT
                }
                dragDistancePx >= longThresholdPx -> GestureType.PULL_INWARD_LONG
                else -> GestureType.PULL_INWARD_SHORT
            }
        }

        private const val DEFAULT_VERTICAL_SWIPE_RATIO = 2.75f
        private const val SLOPE_SECTOR_THRESHOLD = 0.5f
        private const val DEFAULT_LONG_THRESHOLD_RATIO = 3f
    }
}
