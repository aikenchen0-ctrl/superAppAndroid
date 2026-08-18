package com.paifa.univerge.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BOTTOM_GESTURE_BAR_OUTLINE_COLOR = 0xFFFF3B30.toInt()

internal data class BottomGestureBarPreviewBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal fun bottomGestureBarPreviewBounds(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    widthDp: Int
): BottomGestureBarPreviewBounds {
    val safeDensity = density.coerceAtLeast(0.75f)
    val width = (sanitizeBottomGestureBarWidthDp(widthDp) * safeDensity)
        .coerceAtMost(screenWidthPx.toFloat())
    val height = (bottomGestureBarTouchHeightDp() * safeDensity)
        .coerceAtMost(screenHeightPx.toFloat())
    val left = (screenWidthPx - width) / 2f
    return BottomGestureBarPreviewBounds(
        left = left,
        top = screenHeightPx - height,
        right = left + width,
        bottom = screenHeightPx.toFloat()
    )
}

internal fun bottomGestureBarOutlineScreenHeightPx(
    realDisplayHeightPx: Int,
    fallbackHeightPx: Int
): Int = realDisplayHeightPx.takeIf { it > 0 } ?: fallbackHeightPx

internal fun bottomGestureBarOutlineWindowSize(
    realDisplayWidthPx: Int,
    realDisplayHeightPx: Int,
    fallbackWidthPx: Int,
    fallbackHeightPx: Int
): Pair<Int, Int> {
    return (realDisplayWidthPx.takeIf { it > 0 } ?: fallbackWidthPx) to
        bottomGestureBarOutlineScreenHeightPx(realDisplayHeightPx, fallbackHeightPx)
}

private fun WindowManager.bottomGestureBarOutlineDisplaySize(
    fallbackMetrics: DisplayMetrics
): Pair<Int, Int> {
    val realMetrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    defaultDisplay.getRealMetrics(realMetrics)
    return bottomGestureBarOutlineWindowSize(
        realDisplayWidthPx = realMetrics.widthPixels,
        realDisplayHeightPx = realMetrics.heightPixels,
        fallbackWidthPx = fallbackMetrics.widthPixels,
        fallbackHeightPx = fallbackMetrics.heightPixels
    )
}

internal class BottomGestureBarPreviewController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var previewView: BottomGestureBarPreviewView? = null
    private val dismissRunnable = Runnable { dismiss() }

    fun show(widthDp: Int) {
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        val view = previewView ?: createView() ?: return
        view.update(
            bottomGestureBarPreviewBounds(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                density = metrics.density,
                widthDp = widthDp
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
            .onFailure { Log.w(TAG, "failed to remove bottom gesture preview", it) }
    }

    private fun createView(): BottomGestureBarPreviewView? {
        val view = BottomGestureBarPreviewView(context, BOTTOM_GESTURE_BAR_OUTLINE_COLOR)
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        return runCatching {
            windowManager.addView(
                view,
                bottomGestureBarOutlineLayoutParams(screenWidthPx, screenHeightPx)
            )
            previewView = view
            view
        }.onFailure { Log.w(TAG, "failed to show bottom gesture preview", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val PREVIEW_HIDE_DELAY_MS = 1_000L
    }
}

internal class BottomGestureBarIndicatorController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var indicatorView: BottomGestureBarPreviewView? = null

    fun show(widthDp: Int) {
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        val view = indicatorView ?: createView() ?: return
        view.update(
            bottomGestureBarPreviewBounds(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                density = metrics.density,
                widthDp = widthDp
            )
        )
    }

    fun dismiss() {
        val view = indicatorView ?: return
        indicatorView = null
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "failed to remove bottom gesture indicator", it) }
    }

    private fun createView(): BottomGestureBarPreviewView? {
        val view = BottomGestureBarPreviewView(context, BOTTOM_GESTURE_BAR_OUTLINE_COLOR)
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        return runCatching {
            windowManager.addView(
                view,
                bottomGestureBarOutlineLayoutParams(screenWidthPx, screenHeightPx)
            )
            indicatorView = view
            view
        }.onFailure { Log.w(TAG, "failed to show bottom gesture indicator", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "UbikiTouch"
    }
}

private fun bottomGestureBarOutlineLayoutParams(
    screenWidthPx: Int,
    screenHeightPx: Int
): WindowManager.LayoutParams {
    return WindowManager.LayoutParams(
        screenWidthPx,
        screenHeightPx,
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
}

internal class BottomGestureBarOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val preferences: UbikiPreferences,
    private val onGesture: (GestureAction, GestureData) -> Unit
) {
    private var gestureBarView: BottomGestureBarView? = null

    fun show() {
        if (gestureBarView != null) return
        val view = BottomGestureBarView(context) { gestureType, data ->
            onGesture(preferences.bottomGestureBarActionFor(gestureType), data)
        }
        runCatching {
            windowManager.addView(view, layoutParams())
            gestureBarView = view
        }.onFailure { error ->
            Log.w(TAG, "failed to add bottom gesture bar", error)
        }
    }

    fun recreate() {
        remove()
        show()
    }

    fun remove() {
        val view = gestureBarView ?: return
        runCatching {
            windowManager.removeViewImmediate(view)
        }.onFailure { error ->
            Log.w(TAG, "failed to remove bottom gesture bar", error)
        }
        gestureBarView = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            (preferences.bottomGestureBarWidthDp * density).toInt(),
            (BottomGestureBarHeightDp * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (bottomGestureBarBottomInsetDp() * density).toInt()
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val BottomGestureBarHeightDp = 30
    }
}

enum class BottomGestureBarGestureType(val id: String) {
    Tap("tap"),
    SwipeUp("swipe_up"),
    SwipeUpHold("swipe_up_hold"),
    SwipeHorizontal("swipe_horizontal"),
    LongPress("long_press")
}

internal fun resolveBottomGestureBarGestureType(
    deltaX: Float,
    deltaY: Float,
    gestureDurationMillis: Long,
    upwardStationaryMillis: Long
): BottomGestureBarGestureType {
    val horizontalDistance = abs(deltaX)
    val verticalDistance = abs(deltaY)
    return when {
        horizontalDistance >= BottomGestureBarSwipeDistancePx && horizontalDistance > verticalDistance -> {
            BottomGestureBarGestureType.SwipeHorizontal
        }
        -deltaY >= BottomGestureBarHomeDistancePx && verticalDistance > horizontalDistance -> {
            if (upwardStationaryMillis >= BottomGestureBarUpwardPauseMillis) {
                BottomGestureBarGestureType.SwipeUpHold
            } else {
                BottomGestureBarGestureType.SwipeUp
            }
        }
        gestureDurationMillis >= BottomGestureBarLongPressMillis -> BottomGestureBarGestureType.LongPress
        else -> BottomGestureBarGestureType.Tap
    }
}

internal fun defaultBottomGestureBarAction(gestureType: BottomGestureBarGestureType): GestureAction {
    return when (gestureType) {
        BottomGestureBarGestureType.Tap -> GestureAction.Recents
        BottomGestureBarGestureType.SwipeUp -> GestureAction.Home
        BottomGestureBarGestureType.SwipeUpHold -> GestureAction.Screenshot
        BottomGestureBarGestureType.SwipeHorizontal -> GestureAction.Back
        BottomGestureBarGestureType.LongPress -> GestureAction.Notifications
    }
}

internal fun defaultBottomGestureBarWidthDp(): Int = DefaultBottomGestureBarWidthDp

internal fun sanitizeBottomGestureBarWidthDp(value: Int): Int {
    return value.coerceIn(MinBottomGestureBarWidthDp, MaxBottomGestureBarWidthDp)
}

internal fun bottomGestureBarVisibleForFloatingChat(floatingChatExpanded: Boolean): Boolean = true

internal fun bottomGestureBarExternalOverlayVisibleForFloatingChat(floatingChatExpanded: Boolean): Boolean {
    return !floatingChatExpanded
}

internal fun shouldShowBottomGestureBarIndicator(showIndicators: Boolean, barVisible: Boolean): Boolean {
    return showIndicators && barVisible
}

internal fun bottomGestureBarRecreatesAfterFloatingChatWindowUpdate(): Boolean = true

internal fun bottomGestureBarUsesNativeTouchInteractionSurface(): Boolean = true

internal data class NativeBottomGestureInterceptRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun nativeBottomGestureInterceptRect(
    config: NativeEdgeGestureConfig,
    floatingChatExpanded: Boolean
): NativeBottomGestureInterceptRect? {
    if (floatingChatExpanded) return null
    if (config.screenWidthPx <= 0 || config.screenHeightPx <= 0 || config.density <= 0f) return null
    val widthPx = (sanitizeBottomGestureBarWidthDp(config.bottomGestureWidthDp) * config.density)
        .roundToInt()
        .coerceIn(1, config.screenWidthPx)
    val heightPx = (bottomGestureBarTouchHeightDp() * config.density)
        .roundToInt()
        .coerceIn(1, config.screenHeightPx)
    val left = ((config.screenWidthPx - widthPx) / 2f).roundToInt()
    return NativeBottomGestureInterceptRect(
        left = left,
        top = config.screenHeightPx - heightPx,
        right = left + widthPx,
        bottom = config.screenHeightPx
    )
}

internal fun nativeBottomGestureHitTest(
    x: Float,
    y: Float,
    config: NativeEdgeGestureConfig,
    floatingChatExpanded: Boolean
): Boolean {
    val rect = nativeBottomGestureInterceptRect(config, floatingChatExpanded) ?: return false
    return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
}

internal enum class BottomGestureBarAction {
    Back,
    Home,
    Recents,
    Screenshot
}

internal fun resolveBottomGestureBarAction(
    deltaX: Float,
    deltaY: Float,
    upwardStationaryMillis: Long
): BottomGestureBarAction {
    return when (
        defaultBottomGestureBarAction(
            resolveBottomGestureBarGestureType(
                deltaX = deltaX,
                deltaY = deltaY,
                gestureDurationMillis = 0L,
                upwardStationaryMillis = upwardStationaryMillis
            )
        )
    ) {
        GestureAction.Home -> BottomGestureBarAction.Home
        GestureAction.Recents -> BottomGestureBarAction.Recents
        GestureAction.Screenshot -> BottomGestureBarAction.Screenshot
        else -> BottomGestureBarAction.Back
    }
}

internal fun bottomGestureBarBottomInsetDp(): Int = 0

internal fun bottomGestureBarDispatchesGestureActionAfterTouchEvent(): Boolean = true

internal fun bottomGestureBarTouchHeightDp(): Int = BottomGestureBarTouchHeightDp

@SuppressLint("ViewConstructor")
private class BottomGestureBarView(
    context: Context,
    private val onGesture: (BottomGestureBarGestureType, GestureData) -> Unit
) : View(context) {
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 25, 35, 45)
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 25, 35, 45)
    }
    private var downX = 0f
    private var downY = 0f
    private var lastMotionX = 0f
    private var lastMotionY = 0f
    private var lastMovementAtMillis = 0L
    private var downAtMillis = 0L
    private var pressed = false

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barWidth = (BottomGestureBarVisualWidthDp * density).coerceAtMost(width.toFloat())
        val barHeight = (BottomGestureBarVisualHeightDp * density).coerceAtMost(height.toFloat())
        val left = (width - barWidth) / 2f
        val top = (height - barHeight) / 2f
        canvas.drawRoundRect(
            left,
            top,
            left + barWidth,
            top + barHeight,
            barHeight,
            barHeight,
            if (pressed) pressedPaint else idlePaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastMotionX = event.rawX
                lastMotionY = event.rawY
                lastMovementAtMillis = event.eventTime
                downAtMillis = event.eventTime
                pressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (
                    abs(event.rawX - lastMotionX) >= BottomGestureBarMotionSlopPx ||
                    abs(event.rawY - lastMotionY) >= BottomGestureBarMotionSlopPx
                ) {
                    lastMotionX = event.rawX
                    lastMotionY = event.rawY
                    lastMovementAtMillis = event.eventTime
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (
                    abs(event.rawX - lastMotionX) >= BottomGestureBarMotionSlopPx ||
                    abs(event.rawY - lastMotionY) >= BottomGestureBarMotionSlopPx
                ) {
                    lastMovementAtMillis = event.eventTime
                }
                val data = GestureData(
                    startX = downX,
                    startY = downY,
                    endX = event.rawX,
                    endY = event.rawY
                )
                val gestureType = resolveBottomGestureBarGestureType(
                    deltaX = event.rawX - downX,
                    deltaY = event.rawY - downY,
                    gestureDurationMillis = event.eventTime - downAtMillis,
                    upwardStationaryMillis = event.eventTime - lastMovementAtMillis
                )
                pressed = false
                invalidate()
                performClick()
                post { onGesture(gestureType, data) }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val BottomGestureBarVisualWidthDp = 92f
        const val BottomGestureBarVisualHeightDp = 5f
    }
}

private class BottomGestureBarPreviewView(context: Context, outlineColor: Int) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.75f)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var bounds: BottomGestureBarPreviewBounds? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun update(nextBounds: BottomGestureBarPreviewBounds) {
        bounds = nextBounds
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val preview = bounds ?: return
        val inset = outlinePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            preview.left + inset,
            preview.top + inset,
            preview.right - inset,
            preview.bottom - inset,
            8f * density,
            8f * density,
            outlinePaint
        )
    }
}

private const val BottomGestureBarSwipeDistancePx = 56f
private const val BottomGestureBarHomeDistancePx = 72f
private const val BottomGestureBarMotionSlopPx = 6f
private const val BottomGestureBarUpwardPauseMillis = 500L
private const val BottomGestureBarLongPressMillis = 500L
private const val DefaultBottomGestureBarWidthDp = 156
private const val MinBottomGestureBarWidthDp = 96
private const val MaxBottomGestureBarWidthDp = 260
private const val BottomGestureBarTouchHeightDp = 30
