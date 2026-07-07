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
class EdgeOverlayView(
    context: Context,
    private val side: EdgeSide,
    private val showIndicator: Boolean,
    private val opacityPercent: Int,
    private val visibleThicknessDp: Int,
    swipeThresholdDp: Int,
    longSwipeThresholdDp: Int = swipeThresholdDp,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCommit: (BackGestureProgress, GestureData) -> Boolean = { _, _ -> false },
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {}
) : View(context) {
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val detector = EdgeGestureDetector(
        side = side,
        minSwipeDistancePx = swipeThresholdDp.coerceIn(8, 120) * resources.displayMetrics.density * SWIPE_THRESHOLD_RESPONSE_RATIO,
        longSwipeDistancePx = longSwipeThresholdDp.coerceIn(swipeThresholdDp + 8, LONG_SWIPE_THRESHOLD_MAX_DP) * resources.displayMetrics.density * SWIPE_THRESHOLD_RESPONSE_RATIO,
        minPreviewDistancePx = 0f,
        onGesture = onGesture,
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
        }
    )
    private var isTouching = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaints()
        applyGestureExclusion(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showIndicator && !isTouching) return
        val paint = if (isTouching) touchPaint else indicatorPaint
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
            paint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> hideTouchFeedback()
        }
        return true
    }

    fun cancelPendingCallbacks() {
        detector.cancel()
    }

    private fun updatePaints() {
        val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100).coerceIn(0, 255)
        val touchAlpha = ((opacityPercent.coerceIn(0, 100) * 1.6f).toInt() * 255 / 100).coerceIn(0, 255)
        indicatorPaint.color = Color.argb(alpha, 0, 122, 255)
        touchPaint.color = Color.argb(touchAlpha, 0, 122, 255)
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
    }
}
