package com.paifa.univerge.gesture.server

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.paifa.univerge.core.gesture.runtime.BottomBarConfig
import com.paifa.univerge.core.gesture.runtime.BottomGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.BottomGestureThresholds
import com.paifa.univerge.core.gesture.runtime.HotZoneSegment
import com.paifa.univerge.core.gesture.runtime.SideGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.SideGestureThresholds
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.overlay.EdgeGestureDetector
import kotlin.math.roundToInt

internal class GestureServerOverlayController(
    private val service: AccessibilityService,
    private val windowManager: WindowManager,
    private val onAction: (GestureAction, GestureData) -> Unit,
    private val onInputSurfaceChanged: (Boolean) -> Unit = {},
    private val onInputSurfaceWillChange: (Boolean) -> Unit = {}
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val views = LinkedHashMap<GestureServerRegion, View>()
    private val placements = LinkedHashMap<GestureServerRegion, GestureServerOverlayRect>()
    private var pendingSnapshot: GestureServerSnapshot? = null
    private var listenerRegistration: AutoCloseable? = null
    private var started = false
    private var lastReportedSurfaceAvailability: Boolean? = null

    val hasInputSurface: Boolean
        get() = views.isNotEmpty()

    val hasActiveGesture: Boolean
        get() = views.values.any { view ->
            when (view) {
                is ServerEdgeGestureView -> view.hasActiveGesture
                is ServerBottomGestureView -> view.hasActiveGesture
                else -> false
            }
        }

    fun start(runtime: GestureServerRuntime) {
        if (started) return
        started = true
        listenerRegistration = runtime.addSnapshotListener { snapshot ->
            mainHandler.post { applySnapshotOrDefer(snapshot) }
        }
    }

    fun cancelActiveGesture() {
        views.values.forEach { view ->
            when (view) {
                is ServerEdgeGestureView -> view.cancelActiveGesture()
                is ServerBottomGestureView -> view.cancelActiveGesture()
            }
        }
        flushPendingSnapshotIfIdle()
    }

    override fun close() {
        started = false
        listenerRegistration?.close()
        listenerRegistration = null
        mainHandler.removeCallbacksAndMessages(null)
        removeViews()
        pendingSnapshot = null
        reportSurfaceAvailability(false)
    }

    private fun applySnapshotOrDefer(snapshot: GestureServerSnapshot) {
        if (!started) return
        if (!snapshot.inputEnabled || snapshot.floatingChatOwnsSurface) {
            pendingSnapshot = null
            if (hasActiveGesture) cancelActiveGesture()
            applySnapshot(snapshot)
            return
        }
        if (hasActiveGesture) {
            if (snapshot.version > (pendingSnapshot?.version ?: 0L)) {
                pendingSnapshot = snapshot
            }
            return
        }
        pendingSnapshot = null
        applySnapshot(snapshot)
    }

    private fun onGestureFinished() {
        if (!started) return
        mainHandler.post(::flushPendingSnapshotIfIdle)
    }

    private fun flushPendingSnapshotIfIdle() {
        if (!started || hasActiveGesture) return
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null
        applySnapshot(snapshot)
    }

    private fun applySnapshot(snapshot: GestureServerSnapshot) {
        removeViews()
        if (!snapshot.isValid() || !snapshot.inputEnabled || snapshot.floatingChatOwnsSurface) {
            notifyInputSurfaceWillChange(false)
            reportSurfaceAvailability(false)
            return
        }

        // Claim ownership before creating any touchable window so the main
        // process can relinquish its Native surface first.
        notifyInputSurfaceWillChange(true)
        val installed = gestureServerOverlayPlan(snapshot).all { spec ->
            when (val region = spec.region) {
                is GestureServerRegion.Side -> addSideView(snapshot, region, spec.rect)
                GestureServerRegion.Bottom -> addBottomView(snapshot, spec.rect)
            }
        }
        if (!installed) {
            removeViews()
            notifyInputSurfaceWillChange(false)
        }
        reportSurfaceAvailability(installed && views.isNotEmpty())
    }

    private fun notifyInputSurfaceWillChange(available: Boolean) {
        runCatching { onInputSurfaceWillChange(available) }
            .onFailure { error ->
                android.util.Log.w(TAG, "failed to prepare server input ownership available=$available", error)
            }
    }

    private fun addSideView(
        snapshot: GestureServerSnapshot,
        region: GestureServerRegion.Side,
        rect: GestureServerOverlayRect
    ): Boolean {
        val density = snapshot.density.coerceAtLeast(0.01f)
        val actionBindings = actionBindings(snapshot, region.side)
        val placement = rect
        val view = ServerEdgeGestureView(
            context = service,
            side = region.side,
            density = density,
            shortThresholdPx = snapshot.shortPullDistanceDp * density * SERVER_SIDE_THRESHOLD_RESPONSE_RATIO,
            longThresholdPx = snapshot.longPullDistanceDp * density * SERVER_SIDE_THRESHOLD_RESPONSE_RATIO,
            actionBindings = actionBindings,
            onGestureFinished = ::onGestureFinished,
            zone = regionZone(snapshot, region),
            snapshotVersion = snapshot.version,
            holdDurationMs = snapshot.holdDurationMs,
            onAction = { action, data ->
                onAction(action, data.withOffset(placement.left, placement.top, density))
            }
        )
        return runCatching {
            windowManager.addView(view, sideLayoutParams(rect))
            views[region] = view
            placements[region] = placement
            true
        }.onFailure { error ->
            // addView can fail after the platform has attached the view;
            // remove the unregistered instance before rolling back the batch.
            runCatching { windowManager.removeView(view) }
            android.util.Log.w(TAG, "failed to add server side view region=$region", error)
        }.getOrDefault(false)
    }

    private fun addBottomView(snapshot: GestureServerSnapshot, rect: GestureServerOverlayRect): Boolean {
        val density = snapshot.density.coerceAtLeast(0.01f)
        val actions = snapshot.bottomActions.mapKeys { (gestureId, _) -> GestureType.fromId(gestureId) }
            .mapValues { (_, actionId) -> GestureAction.fromId(actionId) }
        val view = ServerBottomGestureView(
            context = service,
            density = density,
            widthDp = snapshot.bottomWidthDp,
            heightDp = snapshot.bottomHeightDp,
            actions = actions,
            holdDurationMs = snapshot.holdDurationMs,
            snapshotVersion = snapshot.version,
            onGestureFinished = ::onGestureFinished,
            onAction = { action, data ->
                onAction(action, data.withOffset(rect.left, rect.top, density))
            }
        )
        return runCatching {
            windowManager.addView(view, bottomLayoutParams(rect))
            views[GestureServerRegion.Bottom] = view
            placements[GestureServerRegion.Bottom] = rect
            true
        }.onFailure { error ->
            runCatching { windowManager.removeView(view) }
            android.util.Log.w(TAG, "failed to add server bottom view", error)
        }.getOrDefault(false)
    }

    private fun actionBindings(
        snapshot: GestureServerSnapshot,
        side: EdgeSide
    ): Map<GestureType, GestureAction> {
        val values = when (side) {
            EdgeSide.LEFT -> snapshot.leftActions
            EdgeSide.RIGHT -> snapshot.rightActions
        }
        return values.mapKeys { (gestureId, _) -> GestureType.fromId(gestureId) }
            .mapValues { (_, actionId) -> GestureAction.fromId(actionId) }
    }

    private fun regionZone(
        snapshot: GestureServerSnapshot,
        region: GestureServerRegion.Side
    ): GestureServerZone {
        val zones = when (region.side) {
            EdgeSide.LEFT -> snapshot.leftZones
            EdgeSide.RIGHT -> snapshot.rightZones
        }
        return zones.firstOrNull { it.zoneId == region.zoneId }
            ?: error("missing zone ${region.side}:${region.zoneId}")
    }

    private fun removeViews() {
        views.values.toList().forEach { view ->
            when (view) {
                is ServerEdgeGestureView -> view.cancelActiveGesture()
                is ServerBottomGestureView -> view.cancelActiveGesture()
            }
            runCatching { windowManager.removeView(view) }
        }
        views.clear()
        placements.clear()
    }

    private fun reportSurfaceAvailability(available: Boolean) {
        if (lastReportedSurfaceAvailability == available) return
        lastReportedSurfaceAvailability = available
        runCatching { onInputSurfaceChanged(available) }
    }

    private fun sideLayoutParams(rect: GestureServerOverlayRect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.right - rect.left,
            rect.bottom - rect.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }

    private fun bottomLayoutParams(rect: GestureServerOverlayRect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.right - rect.left,
            rect.bottom - rect.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

    private fun GestureData.withOffset(left: Int, top: Int, density: Float): GestureData = GestureData(
        startX = startX * density + left,
        startY = startY * density + top,
        endX = endX * density + left,
        endY = endY * density + top,
        gestureId = gestureId,
        snapshotVersion = snapshotVersion,
        zoneId = zoneId
    )

    private companion object {
        const val TAG = "GestureServer"
    }
}

@SuppressLint("ViewConstructor")
private class ServerEdgeGestureView(
    context: android.content.Context,
    side: EdgeSide,
    density: Float,
    shortThresholdPx: Float,
    longThresholdPx: Float,
    actionBindings: Map<GestureType, GestureAction>,
    private val onGestureFinished: () -> Unit,
    zone: GestureServerZone,
    snapshotVersion: Long,
    holdDurationMs: Long,
    onAction: (GestureAction, GestureData) -> Unit
) : View(context) {
    private val detector = EdgeGestureDetector(
        side = side,
        minSwipeDistancePx = shortThresholdPx.coerceAtLeast(1f),
        longSwipeDistancePx = longThresholdPx.coerceAtLeast(shortThresholdPx),
        minVerticalSwipeDistancePx = shortThresholdPx.coerceAtLeast(1f),
        onGesture = { _, _ -> },
        actionBindings = actionBindings,
        onGestureWithAction = { _, action, data -> onAction(action, data) },
        density = density,
        viewportHeightPx = { height.toFloat() },
        viewportWidthPx = { width.toFloat() },
        holdDurationMs = holdDurationMs,
        recognizerFactory = { screenWidthDp, screenHeightDp ->
            SideGestureRecognizer(
                side = side,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                zones = listOf(
                    HotZoneSegment(
                        startDp = 0f,
                        lengthDp = screenHeightDp,
                        thicknessDp = screenWidthDp.coerceAtLeast(1f),
                        enabled = zone.enabled,
                        zoneId = zone.zoneId,
                        edgeInsetDp = 0f
                    )
                ),
                actions = actionBindings,
                thresholds = SideGestureThresholds(
                    minPullDistanceDp = (shortThresholdPx / density).coerceAtLeast(1f),
                    longPullDistanceDp = (longThresholdPx / density)
                        .coerceAtLeast(shortThresholdPx / density),
                    minSwipeDistanceDp = (shortThresholdPx / density).coerceAtLeast(1f),
                    holdDurationMs = holdDurationMs
                ),
                snapshotVersion = snapshotVersion
            )
        }
    )

    val hasActiveGesture: Boolean
        get() = detector.hasActiveSession

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        if (event.isTerminalGestureEvent()) onGestureFinished()
        return handled
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            detector.onFocusLost()
            onGestureFinished()
        }
    }

    fun cancelActiveGesture() {
        val wasActive = hasActiveGesture
        detector.cancel()
        if (wasActive) onGestureFinished()
    }

    override fun onDetachedFromWindow() {
        cancelActiveGesture()
        super.onDetachedFromWindow()
    }
}

@SuppressLint("ViewConstructor")
private class ServerBottomGestureView(
    context: android.content.Context,
    private val density: Float,
    widthDp: Float,
    heightDp: Float,
    actions: Map<GestureType, GestureAction>,
    holdDurationMs: Long,
    snapshotVersion: Long,
    private val onGestureFinished: () -> Unit,
    private val onAction: (GestureAction, GestureData) -> Unit
) : View(context) {
    private val recognizer = BottomGestureRecognizer(
        bar = BottomBarConfig(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            widthDp = widthDp,
            heightDp = heightDp
        ),
        actions = actions,
        thresholds = BottomGestureThresholds(
            minSwipeDistanceDp = SERVER_BOTTOM_SWIPE_DISTANCE_PX / density,
            slopDp = SERVER_BOTTOM_MOTION_SLOP_PX / density,
            longPressDurationMs = holdDurationMs,
            upwardHoldDurationMs = holdDurationMs
        ),
        snapshotVersion = snapshotVersion
    )
    private val holdHandler = Handler(Looper.getMainLooper())
    private var touchActive = false
    private val holdPoll = object : Runnable {
        override fun run() {
            if (!touchActive || !recognizer.hasActiveGesture) return
            recognizer.onHoldTimer(SystemClock.uptimeMillis())
            holdHandler.postDelayed(this, HOLD_POLL_INTERVAL_MS)
        }
    }

    val hasActiveGesture: Boolean
        get() = recognizer.hasActiveGesture

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            cancelActiveGesture()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val xDp = event.x / density
        val yDp = event.y / density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchActive = recognizer.onDown(xDp, yDp, event.eventTime, event.getPointerId(0), event.pointerCount) == com.paifa.univerge.core.gesture.runtime.GestureSignal.Ignored && recognizer.hasActiveGesture
                holdHandler.removeCallbacks(holdPoll)
                if (touchActive) holdHandler.postDelayed(holdPoll, HOLD_POLL_INTERVAL_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                recognizer.onMove(xDp, yDp, event.eventTime, event.getPointerId(0), event.pointerCount)
                return true
            }
            MotionEvent.ACTION_UP -> {
                holdHandler.removeCallbacks(holdPoll)
                val signal = recognizer.onUp(xDp, yDp, event.eventTime, event.getPointerId(0), event.pointerCount)
                touchActive = false
                if (signal is com.paifa.univerge.core.gesture.runtime.GestureSignal.Commit) {
                    performClick()
                    onAction(signal.action, signal.data)
                }
                onGestureFinished()
                return true
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                cancelActiveGesture()
                return true
            }
        }
        return true
    }

    fun cancelActiveGesture() {
        val wasActive = hasActiveGesture
        holdHandler.removeCallbacks(holdPoll)
        touchActive = false
        recognizer.onCancel()
        if (wasActive) onGestureFinished()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelActiveGesture()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val HOLD_POLL_INTERVAL_MS = 50L
    }
}

private fun MotionEvent.isTerminalGestureEvent(): Boolean = when (actionMasked) {
    MotionEvent.ACTION_UP,
    MotionEvent.ACTION_CANCEL,
    MotionEvent.ACTION_POINTER_DOWN,
    MotionEvent.ACTION_POINTER_UP -> true
    else -> false
}

private const val SERVER_SIDE_THRESHOLD_RESPONSE_RATIO = 0.70f
private const val SERVER_BOTTOM_SWIPE_DISTANCE_PX = 56f
private const val SERVER_BOTTOM_MOTION_SLOP_PX = 6f
