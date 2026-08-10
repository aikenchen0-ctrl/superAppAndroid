/*
 * 功能概览：创建屏幕边缘透明触摸条，绘制可选指示条并把事件交给 EdgeGestureDetector。
 * Kotlin 语法提示：`override` 表示重写父类方法；`apply {}` 在对象初始化时返回对象本身。
 */
package com.paifa.ubikitouch.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType

@SuppressLint("ViewConstructor")
// 该 View 不执行系统动作，只负责绘制和把手势结果向上传递。
class EdgeOverlayView(
    context: Context,
    private val side: EdgeSide,
    private val showTouchFeedback: Boolean = true,
    private val visibleThicknessDp: Int,
    swipeThresholdDp: Int,
    longSwipeThresholdDp: Int = swipeThresholdDp,
    minVerticalSwipeDistancePx: Float = 0f,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onGestureProgress: (GestureData) -> Unit = {},
    private val onGestureEnd: () -> Unit = {},
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCommit: (BackGestureProgress, GestureData) -> Boolean = { _, _ -> false },
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {}
) : View(context) {
    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val detector = EdgeGestureDetector(
        side = side,
        minSwipeDistancePx = swipeThresholdDp.coerceIn(8, 120) * resources.displayMetrics.density * SWIPE_THRESHOLD_RESPONSE_RATIO,
        longSwipeDistancePx = longSwipeThresholdDp.coerceIn(swipeThresholdDp + 8, LONG_SWIPE_THRESHOLD_MAX_DP) * resources.displayMetrics.density * SWIPE_THRESHOLD_RESPONSE_RATIO,
        minPreviewDistancePx = 0f,
        minVerticalSwipeDistancePx = minVerticalSwipeDistancePx.takeIf { it > 0f }
            ?: swipeThresholdDp * resources.displayMetrics.density,
        onGesture = onGesture,
        onGestureProgress = onGestureProgress,
        onGestureEnd = onGestureEnd,
        onBackGestureProgress = { progress ->
            if (!isTouching) {
                isTouching = true
                invalidate()
            }
            onBackGestureProgress(progress)
        },
        onBackGestureCommit = onBackGestureCommit,
        onBackGestureEnd = onBackGestureEnd,
        onBackGestureCancel = {
            hideTouchFeedback()
            onBackGestureCancel()
        },
        density = resources.displayMetrics.density,
        viewportHeightPx = { height.toFloat() }
    )
    private var isTouching = false

    // 尺寸变化后重新计算画笔和系统手势排除区域。
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaints()
        applyGestureExclusion(w, h)
    }

    // 只在“显示指示器”或“正在触摸”时绘制，减少不必要的重绘。
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showTouchFeedback && isTouching) {
            val handleWidth = visibleHandleWidthPx(isTouching)
            val verticalInset = HANDLE_VERTICAL_INSET_DP * resources.displayMetrics.density
            val left = when (side) {
                EdgeSide.LEFT -> 0f
                EdgeSide.RIGHT -> width - handleWidth
            }
            canvas.drawRoundRect(
                left,
                verticalInset,
                left + handleWidth,
                height - verticalInset,
                handleWidth,
                handleWidth,
                touchPaint
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    // 触摸事件统一交给 detector；这里只处理触摸反馈的收尾。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> hideTouchFeedback()
        }
        return true
    }

    // 宿主暂停服务或销毁窗口时调用，取消长按计时器等回调。
    fun cancelPendingCallbacks() {
        detector.cancel()
    }

    private fun updatePaints() {
        touchPaint.color = edgeOverlayIndicatorColorArgb(
            (DEFAULT_TOUCH_OPACITY_PERCENT * TOUCH_ALPHA_MULTIPLIER).toInt()
        )
    }

    private fun applyGestureExclusion(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        }
    }

    private fun visibleHandleWidthPx(touching: Boolean): Float {
        val density = resources.displayMetrics.density
        val configuredWidth = visibleThicknessDp.coerceIn(HANDLE_MIN_WIDTH_DP, HANDLE_MAX_WIDTH_DP)
        val touchBoost = if (touching && configuredWidth < TOUCH_HANDLE_BOOST_LIMIT_DP) TOUCH_HANDLE_BOOST_DP else 0
        return ((configuredWidth + touchBoost) * density)
            .coerceAtMost(width.toFloat())
    }

    private fun hideTouchFeedback() {
        if (!isTouching) return
        isTouching = false
        invalidate()
    }

    private companion object {
        const val SWIPE_THRESHOLD_RESPONSE_RATIO = 0.70f
        const val LONG_SWIPE_THRESHOLD_MAX_DP = 320
        const val HANDLE_MIN_WIDTH_DP = 1
        const val HANDLE_MAX_WIDTH_DP = 96
        const val TOUCH_HANDLE_BOOST_DP = 2
        const val TOUCH_HANDLE_BOOST_LIMIT_DP = 8
        const val HANDLE_VERTICAL_INSET_DP = 8f
        const val TOUCH_ALPHA_MULTIPLIER = 1.6f
        const val DEFAULT_TOUCH_OPACITY_PERCENT = 88
    }
}

internal fun edgeOverlayIndicatorColorArgb(opacityPercent: Int): Int {
    val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
    return Color.argb(alpha, EDGE_OVERLAY_COLOR_RED, EDGE_OVERLAY_COLOR_GREEN, EDGE_OVERLAY_COLOR_BLUE)
}

private const val EDGE_OVERLAY_COLOR_RED = 0xAD
private const val EDGE_OVERLAY_COLOR_GREEN = 0xD8
private const val EDGE_OVERLAY_COLOR_BLUE = 0xE6

internal val EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS = listOf(
    0xFFFF9500.toInt(),
    0xFFFFCC00.toInt(),
    0xFF34C759.toInt(),
    0xFF00C7BE.toInt(),
    0xFF30B0C7.toInt(),
    0xFF32ADE6.toInt(),
    0xFF007AFF.toInt(),
    0xFF5856D6.toInt(),
    0xFFAF52DE.toInt(),
    0xFFFF2D55.toInt(),
    0xFFA8D600.toInt(),
    0xFFFF6B6B.toInt()
)

fun edgeOverlayOutlineColorArgb(side: EdgeSide, zoneId: Int): Int {
    if (zoneId <= 0) return 0xFFFF3B30.toInt()
    val sideOffset = when (side) {
        EdgeSide.LEFT -> 0
        EdgeSide.RIGHT -> 1
    }
    val colorIndex = ((zoneId - 1) * 2 + sideOffset) % EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS.size
    return EDGE_OVERLAY_ADDED_ZONE_OUTLINE_COLORS[colorIndex]
}
