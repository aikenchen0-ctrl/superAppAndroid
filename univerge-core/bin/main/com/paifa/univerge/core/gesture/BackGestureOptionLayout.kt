/*
 * 功能概览：计算返回手势双选项扇形框的位置，并提供触摸命中测试。
 * Kotlin 语法提示：`List<T>` 是只读列表接口；`firstOrNull` 找不到元素时返回 null。
 */
package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.EdgeSide
import kotlin.math.abs

// 一个选项框的几何信息，坐标统一使用像素，避免 UI 层重复换算。
data class BackGestureOptionBox(
    val option: BackGestureOption,
    val centerInwardPx: Float,
    val centerYOffsetPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val cornerRadiusPx: Float
) {
    // 判断触点是否落在当前框内；边界异常时直接返回 false。
    fun contains(
        inwardDistancePx: Float,
        verticalOffsetPx: Float,
        startYPx: Float = 0f,
        viewportHeightPx: Float = Float.POSITIVE_INFINITY
    ): Boolean {
        if (widthPx <= 0f || heightPx <= 0f) return false
        val maxTop = (viewportHeightPx - heightPx).coerceAtLeast(0f)
        val top = (startYPx + centerYOffsetPx - heightPx / 2f)
            .coerceIn(0f, maxTop)
        val effectiveVerticalOffsetPx = top + heightPx / 2f - startYPx
        return abs(inwardDistancePx - centerInwardPx) <= widthPx / 2f &&
            abs(verticalOffsetPx - effectiveVerticalOffsetPx) <= heightPx / 2f
    }
}

// 某一时刻的布局快照，progress 控制框的缩放和展开程度。
data class BackGestureOptionLayout(
    val progress: Float,
    val boxes: List<BackGestureOptionBox>
) {
    fun box(option: BackGestureOption): BackGestureOptionBox {
        return boxes.first { it.option == option }
    }
}

// 根据动画进度生成上下两个候选框。
fun backGestureOptionLayout(
    progress: Float,
    density: Float
): BackGestureOptionLayout {
    val safeDensity = density.takeIf { it > 0f } ?: return BackGestureOptionLayout(0f, emptyList())
    val clampedProgress = progress.coerceIn(0f, 1f)
    val easedProgress = easeOutCubic(clampedProgress)
    val widthPx = OPTION_BOX_WIDTH_DP * safeDensity * easedProgress
    val heightPx = OPTION_BOX_HEIGHT_DP * safeDensity * easedProgress
    val centerInwardPx = OPTION_CENTER_INWARD_DP * safeDensity *
        (OPTION_MIN_CENTER_RATIO + OPTION_EXTRA_CENTER_RATIO * easedProgress)
    val verticalSpreadPx = OPTION_VERTICAL_SPREAD_DP * safeDensity * easedProgress
    val cornerRadiusPx = OPTION_CORNER_RADIUS_DP * safeDensity * easedProgress

    return BackGestureOptionLayout(
        progress = clampedProgress,
        boxes = listOf(
            BackGestureOptionBox(
                option = BackGestureOption.FunctionOne,
                centerInwardPx = centerInwardPx,
                centerYOffsetPx = -verticalSpreadPx,
                widthPx = widthPx,
                heightPx = heightPx,
                cornerRadiusPx = cornerRadiusPx
            ),
            BackGestureOptionBox(
                option = BackGestureOption.FunctionTwo,
                centerInwardPx = centerInwardPx,
                centerYOffsetPx = verticalSpreadPx,
                widthPx = widthPx,
                heightPx = heightPx,
                cornerRadiusPx = cornerRadiusPx
            )
        )
    )
}

// 把屏幕坐标转换为相对边缘的坐标后进行命中测试。
fun hitTestBackGestureOption(
    side: EdgeSide,
    startX: Float,
    startY: Float,
    touchX: Float,
    touchY: Float,
    progress: Float,
    density: Float,
    viewportHeightPx: Float = Float.POSITIVE_INFINITY
): BackGestureOption {
    val inwardDistancePx = when (side) {
        EdgeSide.LEFT -> touchX - startX
        EdgeSide.RIGHT -> startX - touchX
    }
    if (inwardDistancePx <= 0f) return BackGestureOption.None

    val verticalOffsetPx = touchY - startY
    return backGestureOptionLayout(progress, density)
        .boxes
        .firstOrNull { box ->
            box.contains(
                inwardDistancePx = inwardDistancePx,
                verticalOffsetPx = verticalOffsetPx,
                startYPx = startY,
                viewportHeightPx = viewportHeightPx
            )
        }
        ?.option
        ?: BackGestureOption.None
}

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private const val OPTION_BOX_WIDTH_DP = 72f
private const val OPTION_BOX_HEIGHT_DP = 44f
private const val OPTION_CORNER_RADIUS_DP = 12f
private const val OPTION_CENTER_INWARD_DP = 112f
private const val OPTION_VERTICAL_SPREAD_DP = 40f
private const val OPTION_MIN_CENTER_RATIO = 0.35f
private const val OPTION_EXTRA_CENTER_RATIO = 0.65f
