package com.paifa.univerge.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

internal class PullDistancePreviewController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var previewView: PullDistancePreviewView? = null
    private val dismissRunnable = Runnable { dismiss() }

    fun show(shortDistanceDp: Int, longDistanceDp: Int) {
        val display = context.resources.displayMetrics
        val view = previewView ?: createView() ?: return
        view.update(
            pullDistancePreviewLayout(
                screenWidthPx = display.widthPixels,
                screenHeightPx = display.heightPixels,
                density = display.density,
                shortDistanceDp = shortDistanceDp,
                longDistanceDp = longDistanceDp
            )
        )
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, PREVIEW_HIDE_DELAY_MS)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val view = previewView ?: return
        previewView = null
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "failed to remove pull-distance preview", it) }
    }

    private fun createView(): PullDistancePreviewView? {
        val view = PullDistancePreviewView(context)
        return runCatching {
            windowManager.addView(
                view,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
            )
            previewView = view
            view
        }.onFailure {
            Log.w(TAG, "failed to show pull-distance preview", it)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val PREVIEW_HIDE_DELAY_MS = 1_200L
    }
}

internal data class PullDistancePreviewLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
) {
    val length: Float
        get() = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
}

internal data class PullDistancePreviewLayout(
    val shortLine: PullDistancePreviewLine,
    val longLine: PullDistancePreviewLine
)

internal fun pullDistancePreviewLayout(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    shortDistanceDp: Int,
    longDistanceDp: Int
): PullDistancePreviewLayout {
    val safeDensity = density.coerceAtLeast(0.75f)
    val insetX = 32f * safeDensity
    val availableWidth = (screenWidthPx - insetX * 2f).coerceAtLeast(0f)
    val shortLength = (shortDistanceDp.coerceAtLeast(0) * safeDensity).coerceAtMost(availableWidth)
    val longLength = (longDistanceDp.coerceAtLeast(0) * safeDensity).coerceAtMost(availableWidth)
    val shortStartX = insetX
    val shortStartY = screenHeightPx * 0.46f
    val longStartX = insetX
    val longStartY = shortStartY + 48f * safeDensity

    return PullDistancePreviewLayout(
        shortLine = PullDistancePreviewLine(
            startX = shortStartX,
            startY = shortStartY,
            endX = shortStartX + shortLength,
            endY = shortStartY
        ),
        longLine = PullDistancePreviewLine(
            startX = longStartX,
            startY = longStartY,
            endX = longStartX + longLength,
            endY = longStartY
        )
    )
}

private class PullDistancePreviewView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.75f)
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val endpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var layout: PullDistancePreviewLayout? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun update(nextLayout: PullDistancePreviewLayout) {
        layout = nextLayout
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val preview = layout ?: return
        drawMeteorLine(canvas, preview.shortLine)
        drawMeteorLine(canvas, preview.longLine)
    }

    private fun drawMeteorLine(canvas: Canvas, line: PullDistancePreviewLine) {
        outerPaint.color = Color.argb(118, 107, 194, 255)
        outerPaint.strokeWidth = 14f * density
        canvas.drawLine(line.startX, line.startY, line.endX, line.endY, outerPaint)

        corePaint.color = Color.argb(224, 196, 240, 255)
        corePaint.strokeWidth = 5f * density
        canvas.drawLine(line.startX, line.startY, line.endX, line.endY, corePaint)

        endpointPaint.color = Color.argb(176, 239, 252, 255)
        canvas.drawCircle(line.endX, line.endY, 9f * density, endpointPaint)
        endpointPaint.color = Color.WHITE
        canvas.drawCircle(line.endX, line.endY, 4f * density, endpointPaint)
    }
}
