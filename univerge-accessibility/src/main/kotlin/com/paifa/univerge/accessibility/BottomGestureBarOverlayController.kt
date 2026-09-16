package com.paifa.univerge.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.gesture.runtime.BottomBarConfig
import com.paifa.univerge.core.gesture.runtime.BottomGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.BottomGestureThresholds
import com.paifa.univerge.core.gesture.runtime.GestureSignal
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
    val safeDensity = density.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(0.75f) ?: 1f
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

/** Converts logical bar geometry once at the platform boundary. */
internal fun bottomGestureBarLayoutSizePx(
    widthDp: Int,
    heightDp: Int,
    density: Float
): Pair<Int, Int> {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val widthPx = (sanitizeBottomGestureBarWidthDp(widthDp) * safeDensity)
        .roundToInt()
        .coerceAtLeast(1)
    val heightPx = (heightDp.coerceAtLeast(1) * safeDensity)
        .roundToInt()
        .coerceAtLeast(1)
    return widthPx to heightPx
}

internal fun bottomGestureBarScreenSizeDp(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float
): Pair<Float, Float> {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (screenWidthPx.coerceAtLeast(0) / safeDensity) to
        (screenHeightPx.coerceAtLeast(0) / safeDensity)
}

internal fun floatingChatBottomGestureExclusionRect(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    widthDp: Int,
    heightDp: Int
): Rect? {
    if (
        screenWidthPx <= 0 ||
        screenHeightPx <= 0 ||
        widthDp <= 0 ||
        heightDp <= 0 ||
        !density.isFinite() ||
        density <= 0f
    ) {
        return null
    }
    val (requestedWidthPx, requestedHeightPx) = bottomGestureBarLayoutSizePx(
        widthDp = widthDp,
        heightDp = heightDp,
        density = density
    )
    val widthPx = requestedWidthPx.coerceAtMost(screenWidthPx)
    val heightPx = requestedHeightPx.coerceAtMost(screenHeightPx)
    val left = ((screenWidthPx - widthPx) / 2f).roundToInt()
    return Rect(
        left,
        screenHeightPx - heightPx,
        left + widthPx,
        screenHeightPx
    )
}

/** `View.systemGestureExclusionRects` exists only from API 29 onward. */
internal fun bottomGestureBarExclusionRects(
    sdkInt: Int,
    width: Int,
    height: Int
): List<Rect> {
    if (sdkInt < Build.VERSION_CODES.Q || width <= 0 || height <= 0) return emptyList()
    return listOf(Rect(0, 0, width, height))
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
    private val preferences: UniVergePreferences,
    private val onGesture: (GestureAction, GestureData) -> Unit,
    private val onGestureFinished: () -> Unit = {}
) {
    private var gestureBarView: BottomGestureBarView? = null
    private var gestureBarLayoutParams: WindowManager.LayoutParams? = null
    private var pendingRecreate = false
    private var pendingGeometry: BottomBarConfig? = null

    val hasActiveGesture: Boolean
        get() = gestureBarView?.hasActiveGesture == true

    fun cancelActiveGesture() {
        gestureBarView?.cancelActiveGesture()
    }

    fun show() {
        if (gestureBarView != null) return
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.01f)
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        val (screenWidthDp, screenHeightDp) = bottomGestureBarScreenSizeDp(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        val widthDp = sanitizeBottomGestureBarWidthDp(preferences.bottomGestureBarWidthDp).toFloat()
        val recognizer = BottomGestureRecognizer(
            bar = BottomBarConfig(
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                widthDp = widthDp,
                heightDp = bottomGestureBarTouchHeightDp().toFloat()
            ),
            actions = bottomGestureCoreActions(),
            thresholds = bottomGestureBarThresholds(density),
            snapshotVersion = 1L
        )
        val view = BottomGestureBarView(
            context = context,
            recognizer = recognizer,
            density = density,
            onGesture = { action, data -> onGesture(action, data) },
            onGestureFinished = {
                flushPendingChanges()
                onGestureFinished()
            }
        )
        val params = layoutParams()
        runCatching {
            windowManager.addView(view, params)
            gestureBarView = view
            gestureBarLayoutParams = params
        }.onFailure { error ->
            Log.w(TAG, "failed to add bottom gesture bar", error)
        }
    }

    fun recreate() {
        if (hasActiveGesture) {
            pendingRecreate = true
            return
        }
        remove()
        show()
    }

    /** Applies only bottom geometry; action bindings remain captured by the active recognizer. */
    fun updateGeometryInPlace(): Boolean {
        val view = gestureBarView ?: return false
        val next = bottomBarConfig()
        if (hasActiveGesture) {
            pendingGeometry = next
            return true
        }
        return applyGeometry(view, next)
    }

    fun remove() {
        val view = gestureBarView ?: return
        runCatching {
            windowManager.removeViewImmediate(view)
        }.onFailure { error ->
            Log.w(TAG, "failed to remove bottom gesture bar", error)
        }
        gestureBarView = null
        gestureBarLayoutParams = null
        pendingRecreate = false
        pendingGeometry = null
    }

    private fun flushPendingChanges() {
        if (hasActiveGesture) return
        if (pendingRecreate) {
            pendingRecreate = false
            remove()
            show()
            return
        }
        val next = pendingGeometry ?: return
        pendingGeometry = null
        gestureBarView?.let { applyGeometry(it, next) }
    }

    private fun applyGeometry(view: BottomGestureBarView, next: BottomBarConfig): Boolean {
        val currentParams = gestureBarLayoutParams ?: return false
        val nextParams = layoutParams()
        return runCatching {
            check(view.updateGeometry(next))
            currentParams.copyFrom(nextParams)
            windowManager.updateViewLayout(view, currentParams)
            true
        }.getOrElse { error ->
            Log.w(TAG, "failed to update bottom gesture geometry", error)
            false
        }
    }

    private fun bottomBarConfig(): BottomBarConfig {
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.01f)
        val metrics = context.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = windowManager.bottomGestureBarOutlineDisplaySize(metrics)
        val (screenWidthDp, screenHeightDp) = bottomGestureBarScreenSizeDp(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        return BottomBarConfig(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            widthDp = sanitizeBottomGestureBarWidthDp(preferences.bottomGestureBarWidthDp).toFloat(),
            heightDp = bottomGestureBarTouchHeightDp().toFloat()
        )
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val (widthPx, heightPx) = bottomGestureBarLayoutSizePx(
            widthDp = preferences.bottomGestureBarWidthDp,
            heightDp = bottomGestureBarTouchHeightDp(),
            density = density
        )
        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
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

    private fun bottomGestureCoreActions(): Map<GestureType, GestureAction> = mapOf(
        GestureType.TAP to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.Tap),
        GestureType.SWIPE_UP to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeUp),
        GestureType.SWIPE_UP_HOLD to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeUpHold),
        GestureType.SWIPE_LEFT to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeHorizontal),
        GestureType.SWIPE_RIGHT to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.SwipeHorizontal),
        GestureType.LONG_PRESS to preferences.bottomGestureBarActionFor(BottomGestureBarGestureType.LongPress)
    )

    private companion object {
        const val TAG = "UbikiTouch"
    }
}

/**
 * Adapter-side terminal gate. A platform may repeat or replay an UP after a
 * cancellation; only the first terminal event of a down/up transaction may
 * dispatch an action.
 */
internal class BottomGestureBarDispatchSession<T>(
    private val commitCallback: (T) -> Unit = {}
) {
    private var active = false
    private var committed = false

    /** True until the adapter has delivered the terminal cleanup callback. */
    val hasPendingTerminal: Boolean
        get() = active

    fun onDown() {
        active = true
        committed = false
    }

    /**
     * Commits the current transaction at the first stable recognition point.
     * This is deliberately separate from [onUp]: directional and timed
     * gestures can cross their threshold while the pointer is still down.
     */
    fun onCommit(value: T): Boolean {
        if (!active || committed) return false
        committed = true
        commitCallback(value)
        return true
    }

    fun onMove() = Unit

    fun onUp(value: T): Boolean {
        val dispatched = onCommit(value)
        onFinish()
        return dispatched
    }

    fun onCancel() {
        if (active) {
            onFinish()
        }
    }

    /** Closes the adapter transaction after the platform terminal event. */
    fun onFinish() {
        active = false
        committed = false
    }
}

enum class BottomGestureBarGestureType(val id: String) {
    Tap("tap"),
    SwipeUp("swipe_up"),
    SwipeUpHold("swipe_up_hold"),
    SwipeHorizontal("swipe_horizontal"),
    LongPress("long_press")
}

/**
 * Normalizes the two upward gestures that share the same physical prefix.
 * Keeping both actions enabled makes a normal upward swipe wait for an
 * additional MOVE in order to disambiguate a pause, which violates the
 * bottom bar's immediate-action contract. The most recently enabled action
 * therefore owns the prefix; disabling an action does not modify the other.
 */
internal fun normalizeBottomGestureBarActionBindings(
    actions: Map<BottomGestureBarGestureType, GestureAction>,
    changedGesture: BottomGestureBarGestureType,
    changedAction: GestureAction
): Map<BottomGestureBarGestureType, GestureAction> {
    val normalized = actions.toMutableMap()
    normalized[changedGesture] = changedAction
    if (changedAction != GestureAction.None) {
        when (changedGesture) {
            BottomGestureBarGestureType.SwipeUp ->
                normalized[BottomGestureBarGestureType.SwipeUpHold] = GestureAction.None
            BottomGestureBarGestureType.SwipeUpHold ->
                normalized[BottomGestureBarGestureType.SwipeUp] = GestureAction.None
            else -> Unit
        }
    }
    return normalized
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
        // SwipeUpHold shares the same prefix as SwipeUp. Keeping it disabled
        // by default lets the common upward swipe commit at the threshold;
        // users who want the pause gesture can opt into it explicitly.
        BottomGestureBarGestureType.SwipeUpHold -> GestureAction.None
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
    None,
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
        GestureAction.None -> BottomGestureBarAction.None
        else -> BottomGestureBarAction.Back
    }
}

internal fun bottomGestureBarBottomInsetDp(): Int = 0

/** Directional/timed gestures may dispatch before the platform UP event. */
internal fun bottomGestureBarDispatchesGestureActionAfterTouchEvent(): Boolean = false

internal fun bottomGestureBarTouchHeightDp(): Int = BottomGestureBarTouchHeightDp

/**
 * The legacy View and Native adapters measured movement in physical pixels.
 * The core recognizer consumes dp, so convert that contract at the boundary.
 */
internal fun bottomGestureBarThresholds(density: Float): BottomGestureThresholds {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return BottomGestureThresholds(
        minSwipeDistanceDp = BottomGestureBarSwipeDistancePx / safeDensity,
        slopDp = BottomGestureBarMotionSlopPx / safeDensity,
        upwardHoldDurationMs = BottomGestureBarUpwardPauseMillis
    )
}

@SuppressLint("ViewConstructor")
private class BottomGestureBarView(
    context: Context,
    private val recognizer: BottomGestureRecognizer,
    private val density: Float,
    private val onGesture: (GestureAction, GestureData) -> Unit,
    private val onGestureFinished: () -> Unit
) : View(context) {
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 25, 35, 45)
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 25, 35, 45)
    }
    private var pressed = false
    private var touchActive = false
    private var finishDelivered = false
    private var commitDispatched = false
    private val dispatchSession = BottomGestureBarDispatchSession<GestureSignal.Commit>()
    private val holdHandler = Handler(Looper.getMainLooper())
    private val holdPollRunnable = object : Runnable {
        override fun run() {
            if (!touchActive || !recognizer.hasActiveGesture) return
            val signal = recognizer.onHoldTimer(SystemClock.uptimeMillis())
            if (signal is GestureSignal.Commit) {
                touchActive = false
                dispatchCommit(signal)
                return
            }
            holdHandler.postDelayed(this, HOLD_POLL_INTERVAL_MS)
        }
    }
    private val terminalWatchdog = object : Runnable {
        override fun run() {
            if (!commitDispatched || !recognizer.hasActiveGesture) return
            touchActive = false
            dispatchSession.onCancel()
            recognizer.onCancel()
            pressed = false
            invalidate()
            commitDispatched = false
            finishGesture()
        }
    }

    val hasActiveGesture: Boolean
        get() = recognizer.hasActiveGesture

    fun cancelActiveGesture() {
        holdHandler.removeCallbacks(holdPollRunnable)
        holdHandler.removeCallbacks(terminalWatchdog)
        touchActive = false
        dispatchSession.onCancel()
        recognizer.onCancel()
        pressed = false
        invalidate()
        commitDispatched = false
        finishGesture()
    }

    fun updateGeometry(next: BottomBarConfig): Boolean = recognizer.updateGeometry(next)

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = bottomGestureBarExclusionRects(
                sdkInt = Build.VERSION.SDK_INT,
                width = width,
                height = height
            )
        }
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
        val xDp = event.rawX / density
        val yDp = event.rawY / density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abandonPreviousTransactionIfNeeded()
                dispatchSession.onDown()
                finishDelivered = false
                commitDispatched = false
                recognizer.onDown(
                    xDp = xDp,
                    yDp = yDp,
                    timeMillis = event.eventTime,
                    pointerId = event.getPointerId(0),
                    pointerCount = event.pointerCount
                )
                touchActive = recognizer.hasActiveGesture
                holdHandler.removeCallbacks(holdPollRunnable)
                if (touchActive) holdHandler.postDelayed(holdPollRunnable, HOLD_POLL_INTERVAL_MS)
                pressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val signal = recognizer.onMove(
                    xDp = xDp,
                    yDp = yDp,
                    timeMillis = event.eventTime,
                    pointerId = event.getPointerId(0),
                    pointerCount = event.pointerCount
                )
                if (signal is GestureSignal.Preview) {
                    dispatchSession.onMove()
                } else if (signal is GestureSignal.Commit) {
                    holdHandler.removeCallbacks(holdPollRunnable)
                    touchActive = false
                    dispatchCommit(signal)
                } else if (signal is GestureSignal.Cancel) {
                    holdHandler.removeCallbacks(holdPollRunnable)
                    touchActive = false
                    dispatchSession.onCancel()
                    pressed = false
                    invalidate()
                    finishGesture()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                holdHandler.removeCallbacks(holdPollRunnable)
                val signal = recognizer.onUp(
                    xDp = xDp,
                    yDp = yDp,
                    timeMillis = event.eventTime,
                    pointerId = event.getPointerId(0),
                    pointerCount = event.pointerCount
                )
                touchActive = false
                pressed = false
                invalidate()
                if (signal is GestureSignal.Commit) {
                    dispatchCommit(signal)
                }
                if (!commitDispatched) {
                    dispatchSession.onCancel()
                }
                finishGesture()
                commitDispatched = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                holdHandler.removeCallbacks(holdPollRunnable)
                touchActive = false
                dispatchSession.onCancel()
                recognizer.onCancel()
                pressed = false
                invalidate()
                commitDispatched = false
                finishGesture()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                holdHandler.removeCallbacks(holdPollRunnable)
                touchActive = false
                dispatchSession.onCancel()
                recognizer.onCancel()
                pressed = false
                invalidate()
                commitDispatched = false
                finishGesture()
                return true
            }
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            holdHandler.removeCallbacks(holdPollRunnable)
            holdHandler.removeCallbacks(terminalWatchdog)
            touchActive = false
            dispatchSession.onCancel()
            recognizer.onFocusLost()
            pressed = false
            invalidate()
            commitDispatched = false
            finishGesture()
        }
    }

    override fun onDetachedFromWindow() {
        holdHandler.removeCallbacks(holdPollRunnable)
        holdHandler.removeCallbacks(terminalWatchdog)
        touchActive = false
        dispatchSession.onCancel()
        recognizer.onCancel()
        pressed = false
        commitDispatched = false
        finishGesture()
        super.onDetachedFromWindow()
    }

    private fun dispatchCommit(signal: GestureSignal.Commit): Boolean {
        if (!dispatchSession.onCommit(signal)) return false
        commitDispatched = true
        holdHandler.removeCallbacks(terminalWatchdog)
        holdHandler.postDelayed(terminalWatchdog, TERMINAL_WATCHDOG_TIMEOUT_MS)
        performClick()
        onGesture(signal.action, signal.data.toScreenData(density))
        return true
    }

    private fun finishGesture() {
        holdHandler.removeCallbacks(terminalWatchdog)
        if (finishDelivered) return
        finishDelivered = true
        dispatchSession.onFinish()
        onGestureFinished()
    }

    private fun abandonPreviousTransactionIfNeeded() {
        if (!recognizer.hasActiveGesture && !touchActive && !commitDispatched &&
            !dispatchSession.hasPendingTerminal
        ) {
            return
        }
        holdHandler.removeCallbacks(holdPollRunnable)
        touchActive = false
        dispatchSession.onCancel()
        recognizer.onCancel()
        pressed = false
        invalidate()
        commitDispatched = false
        finishGesture()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val BottomGestureBarVisualWidthDp = 92f
        const val BottomGestureBarVisualHeightDp = 5f
        const val HOLD_POLL_INTERVAL_MS = 50L
        const val TERMINAL_WATCHDOG_TIMEOUT_MS = 1_500L
    }

    private fun GestureData.toScreenData(density: Float): GestureData = GestureData(
        startX = startX * density,
        startY = startY * density,
        endX = endX * density,
        endY = endY * density,
        gestureId = gestureId,
        snapshotVersion = snapshotVersion,
        zoneId = zoneId
    )
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
