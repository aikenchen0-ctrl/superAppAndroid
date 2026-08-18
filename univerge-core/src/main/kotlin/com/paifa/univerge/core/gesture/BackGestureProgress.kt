/*
 * 功能概览：保存边缘返回手势的几何进度，并负责从位移推导短/长/斜向类型。
 * Kotlin 语法提示：`data class` 会自动生成 `copy`、`equals` 和 `toString`；末尾的 `?` 表示函数可能返回 null。
 */
package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureType
import kotlin.math.abs
import kotlin.math.hypot

data class BackGestureProgress(
    val side: EdgeSide,
    val dragDistancePx: Float,
    val thresholdPx: Float,
    val longThresholdPx: Float = thresholdPx * 3f,
    val touchY: Float = 0f,
    val startY: Float = touchY,
    val gestureType: GestureType = GestureType.PULL_INWARD_SHORT,
    val selectedOption: BackGestureOption = BackGestureOption.None
) {
    // 普通阈值对应的提交进度，限制在 0..1 便于 UI 显示。
    val progress: Float =
        if (thresholdPx <= 0f) 1f else (dragDistancePx / thresholdPx).coerceIn(0f, 1f)

    // 长手势专用的视觉进度，用于展开双框提示。
    val visualProgress: Float =
        if (longThresholdPx <= 0f) 1f else (dragDistancePx / longThresholdPx).coerceIn(0f, 1f)

    val committed: Boolean = dragDistancePx > 0f

    val longCommitted: Boolean = dragDistancePx >= longThresholdPx

    companion object {
        // 根据起点到当前位置的 dx/dy 创建进度；不符合内收方向时返回 null。
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

        // 斜率决定方向，距离决定 short/long；两者组合成最终手势类型。
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
