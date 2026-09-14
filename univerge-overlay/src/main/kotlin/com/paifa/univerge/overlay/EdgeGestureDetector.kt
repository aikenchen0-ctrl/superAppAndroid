/*
 * Android input adapter for the platform-independent side recognizer.
 * This class owns no preferences, package lookups, windows, or actions. It
 * only translates MotionEvent primitives into a single recognizer session and
 * forwards domain previews/commits to the host callbacks.
 */
package com.paifa.univerge.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.paifa.univerge.core.gesture.BackGestureCommitController
import com.paifa.univerge.core.gesture.BackGestureProgress
import com.paifa.univerge.core.gesture.hitTestBackGestureOption
import com.paifa.univerge.core.gesture.runtime.GestureSignal
import com.paifa.univerge.core.gesture.runtime.HotZoneSegment
import com.paifa.univerge.core.gesture.runtime.SideGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.SideGestureThresholds
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType

/**
 * Delivers the latest domain preview to a renderer. A host may provide a
 * frame-coalescing implementation; the default is synchronous and allocation
 * free apart from the recognizer's first preview object.
 */
fun interface GesturePreviewSink {
    fun submit(preview: GestureSignal.Preview)
}

/** Coalesces high-frequency previews without changing the terminal gesture contract. */
interface GesturePreviewFrameDispatcher {
    var consumer: ((GestureSignal.Preview) -> Unit)?

    fun submit(preview: GestureSignal.Preview)

    fun cancel()
}

private object ImmediateGesturePreviewSink : GesturePreviewSink {
    override fun submit(preview: GestureSignal.Preview) = Unit
}

/**
 * Receives edge input and emits domain events. Actions remain outside this
 * adapter: only the legal UP path reaches [onGesture] or [onGestureCommit].
 */
class EdgeGestureDetector(
    private val side: EdgeSide,
    private val minSwipeDistancePx: Float,
    private val longSwipeDistancePx: Float = minSwipeDistancePx,
    private val minPreviewDistancePx: Float = 0f,
    private val minVerticalSwipeDistancePx: Float = minSwipeDistancePx,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onGestureProgress: (GestureData) -> Unit = {},
    private val onGestureEnd: () -> Unit = {},
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCommit: (BackGestureProgress, GestureData) -> Boolean = { _, _ -> false },
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {},
    private val density: Float = 1f,
    private val viewportHeightPx: () -> Float = { Float.POSITIVE_INFINITY },
    /** Optional server/config adapter. It is evaluated once per DOWN; its core units are dp. */
    private val recognizerFactory: ((screenWidth: Float, screenHeight: Float) -> SideGestureRecognizer)? = null,
    private val onGesturePreview: (GestureSignal.Preview) -> Unit = {},
    private val onGestureCommit: (GestureSignal.Commit) -> Unit = {},
    private val onGestureCancel: (GestureSignal.Cancel) -> Unit = {},
    private val previewSink: GesturePreviewSink = ImmediateGesturePreviewSink,
    private val previewFrameDispatcher: GesturePreviewFrameDispatcher? = null,
    private val holdDurationMs: Long = DEFAULT_HOLD_DURATION_MS,
    private val viewportWidthPx: () -> Float = { Float.POSITIVE_INFINITY },
    private val actionBindings: Map<GestureType, GestureAction> = emptyMap(),
    private val onGestureWithAction: ((GestureType, GestureAction, GestureData) -> Unit)? = null,
    private val onBackGestureCommitWithAction: ((BackGestureProgress, GestureAction, GestureData) -> Boolean)? = null
) {
    val hasActiveSession: Boolean
        get() = sessionActive
    init {
        previewFrameDispatcher?.consumer = ::deliverPreviewFrame
    }
    private val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    private val usesDpCoreCoordinates: Boolean get() = recognizerFactory != null
    private val coreToPixelScale: Float get() = if (usesDpCoreCoordinates) safeDensity else 1f
    private val handler = Handler(Looper.getMainLooper())
    private val backCommitController = BackGestureCommitController()
    private var recognizer: SideGestureRecognizer? = null
    private var sessionActive = false
    private var previewDelivered = false
    private var backProgressActive = false
    private var committedGestureConsumed = false
    private var holdTimerArmed = false
    private var lastPointerId = INVALID_POINTER_ID
    private var lastEventTime = 0L
    private var startTime = 0L
    private var previousMoveX = 0f
    private var previousMoveTimeMs = 0L
    private var activeActionBindings: Map<GestureType, GestureAction> = emptyMap()

    private val holdRunnable = Runnable {
        if (!sessionActive || recognizer == null) return@Runnable
        holdTimerArmed = false
        // The timer only changes the recognizer candidate. It must never call
        // an action callback; the terminal decision belongs to ACTION_UP.
        dispatchSignal(recognizer!!.onHoldTimer(SystemClock.uptimeMillis()))
    }

    /** Android dispatch entry point. The View remains the sole input owner. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> cancelSession()
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> cancelSession()
            else -> Unit
        }
        return true
    }

    /** Explicit focus/lifecycle cancellation for overlay hosts. */
    fun onFocusLost() {
        val current = recognizer ?: return
        dispatchSignal(current.onFocusLost())
        finishSession()
    }

    /** Explicit lifecycle cancellation; no terminal action is emitted. */
    fun cancel() {
        cancelSession()
    }

    /**
     * Adapter hook for a host timer. Calling it is safe at any point; core may
     * arm a hold candidate but never commits from this method.
     */
    fun onHoldTimer(timeMillis: Long): Boolean {
        val current = recognizer ?: return false
        val signal = current.onHoldTimer(timeMillis)
        dispatchSignal(signal)
        return signal is GestureSignal.Preview
    }

    private fun handleDown(event: MotionEvent) {
        if (sessionActive) cancelSession()
        if (event.pointerCount != 1) return

        val x = event.x
        val y = event.y
        val width = viewportWidthPx().takeIf { it.isFinite() && it > 0f } ?: fallbackWidthPx()
        val height = viewportHeightPx().takeIf { it.isFinite() && it > 0f } ?: fallbackHeightPx()
        val screenWidth = if (usesDpCoreCoordinates) width / safeDensity else width
        val screenHeight = if (usesDpCoreCoordinates) height / safeDensity else height
        val current = recognizerFactory?.invoke(screenWidth, screenHeight)
            ?: compatibilityRecognizer(screenWidth, screenHeight)

        recognizer = current
        activeActionBindings = actionBindings
        sessionActive = true
        previewDelivered = false
        backProgressActive = false
        committedGestureConsumed = false
        holdTimerArmed = false
        lastPointerId = event.getPointerId(0)
        startTime = event.eventTime
        lastEventTime = event.eventTime
        previousMoveX = x
        previousMoveTimeMs = event.eventTime
        backCommitController.reset()

        current.onDown(
            xDp = toCoreCoordinate(x),
            yDp = toCoreCoordinate(y),
            timeMillis = event.eventTime,
            pointerId = lastPointerId,
            pointerCount = event.pointerCount
        )
    }

    private fun handleMove(event: MotionEvent) {
        val current = recognizer ?: return
        if (!sessionActive) return
        if (!isSingleActivePointer(event)) {
            dispatchSignal(current.onMove(
                xDp = toCoreCoordinate(event.x),
                yDp = toCoreCoordinate(event.y),
                timeMillis = event.eventTime,
                pointerId = event.getPointerId(0),
                pointerCount = event.pointerCount
            ))
            cancelSessionAfterSignal()
            return
        }

        lastEventTime = event.eventTime
        val signal = current.onMove(
            xDp = toCoreCoordinate(event.x),
            yDp = toCoreCoordinate(event.y),
            timeMillis = event.eventTime,
            pointerId = lastPointerId,
            pointerCount = 1
        )
        dispatchSignal(signal)
        if (signal is GestureSignal.Preview) {
            scheduleHoldTimerIfNeeded(event)
        } else if (signal is GestureSignal.Cancel) {
            cancelSessionAfterSignal()
            return
        }
        previousMoveX = event.x
        previousMoveTimeMs = event.eventTime
    }

    private fun handleUp(event: MotionEvent) {
        val current = recognizer ?: return
        if (!sessionActive || !isSingleActivePointer(event)) {
            dispatchSignal(current.onCancel())
            cancelSessionAfterSignal()
            return
        }

        cancelHoldTimer()
        lastEventTime = event.eventTime
        val signal = current.onUp(
            xDp = toCoreCoordinate(event.x),
            yDp = toCoreCoordinate(event.y),
            timeMillis = event.eventTime,
            pointerId = lastPointerId,
            pointerCount = 1
        )
        if (signal is GestureSignal.Commit) {
            val committedSignal = signal.withCapturedAction()
            val finalProgress = backProgressFor(committedSignal.data)
            if (finalProgress != null) {
                onBackGestureEnd(finalProgress)
                if (finalProgress.committed) {
                    committedGestureConsumed = onBackGestureCommitWithAction?.invoke(
                        finalProgress,
                        committedSignal.action,
                        committedSignal.data
                    ) ?: onBackGestureCommit(finalProgress, committedSignal.data)
                }
            } else {
                cancelBackProgress()
            }
            if (previewDelivered) onGestureEnd()
            onGestureCommit(committedSignal)
            if (!committedGestureConsumed) {
                onGestureWithAction?.invoke(
                    committedSignal.gesture,
                    committedSignal.action,
                    committedSignal.data
                ) ?: onGesture(committedSignal.gesture, committedSignal.data)
            }
        } else {
            dispatchSignal(signal)
            if (signal is GestureSignal.Cancel) cancelSessionAfterSignal()
        }
        finishSession()
    }

    private fun dispatchSignal(signal: GestureSignal) {
        when (signal) {
            GestureSignal.Ignored -> Unit
            is GestureSignal.Preview -> {
                signal.action = activeActionBindings[signal.gesture] ?: signal.action
                if (previewFrameDispatcher == null) {
                    deliverPreviewFrame(signal)
                } else {
                    previewFrameDispatcher.submit(signal)
                }
            }
            is GestureSignal.Commit -> onGestureCommit(signal.withCapturedAction())
            is GestureSignal.Cancel -> {
                onGestureCancel(signal)
                cancelBackProgress(notifyWhenInactive = true)
                if (previewDelivered) onGestureEnd()
            }
        }
    }

    private fun deliverPreviewFrame(signal: GestureSignal.Preview) {
        previewDelivered = true
        previewSink.submit(signal)
        onGesturePreview(signal)
        deliverPreview(signal)
    }

    private fun deliverPreview(preview: GestureSignal.Preview) {
        onGestureProgress(preview.data)
        val progress = backProgressFor(preview.data)
        if (progress != null) {
            backProgressActive = true
            onBackGestureProgress(progress)
            backCommitController.update(progress, inwardVelocityPxPerSecond(preview.data.endX * coreToPixelScale))
        } else {
            cancelBackProgress()
        }
    }

    private fun backProgressFor(data: GestureData): BackGestureProgress? {
        val startXpx = data.startX * coreToPixelScale
        val startYpx = data.startY * coreToPixelScale
        val endXpx = data.endX * coreToPixelScale
        val endYpx = data.endY * coreToPixelScale
        val progress = BackGestureProgress.fromDelta(
            side = side,
            dx = endXpx - startXpx,
            dy = endYpx - startYpx,
            thresholdPx = minSwipeDistancePx,
            longThresholdPx = longSwipeDistancePx,
            touchY = endYpx,
            startY = startYpx,
            minimumDragDistancePx = minPreviewDistancePx
        ) ?: return null
        return progress.copy(
            selectedOption = hitTestBackGestureOption(
                side = side,
                startX = startXpx,
                startY = startYpx,
                touchX = endXpx,
                touchY = endYpx,
                progress = progress.progress,
                density = safeDensity,
                viewportHeightPx = viewportHeightPx()
            )
        )
    }

    private fun scheduleHoldTimerIfNeeded(event: MotionEvent) {
        if (holdTimerArmed) return
        holdTimerArmed = true
        handler.removeCallbacks(holdRunnable)
        val elapsed = (event.eventTime - startTime).coerceAtLeast(0L)
        handler.postDelayed(holdRunnable, (holdDurationMs - elapsed).coerceAtLeast(1L))
    }

    private fun cancelSession() {
        val current = recognizer
        if (current != null && sessionActive) {
            dispatchSignal(current.onCancel())
        }
        finishSession()
    }

    private fun cancelSessionAfterSignal() {
        cancelHoldTimer()
        finishSession()
    }

    private fun finishSession() {
        cancelHoldTimer()
        previewFrameDispatcher?.cancel()
        previewSink.cancelIfSupported()
        recognizer = null
        sessionActive = false
        previewDelivered = false
        backProgressActive = false
        committedGestureConsumed = false
        lastPointerId = INVALID_POINTER_ID
        activeActionBindings = emptyMap()
        backCommitController.reset()
    }

    private fun GestureSignal.Commit.withCapturedAction(): GestureSignal.Commit {
        val action = activeActionBindings[gesture] ?: action
        return if (action == this.action) this else copy(action = action)
    }

    private fun cancelBackProgress(notifyWhenInactive: Boolean = false) {
        if (!backProgressActive && !notifyWhenInactive) return
        backProgressActive = false
        onBackGestureCancel()
    }

    private fun cancelHoldTimer() {
        if (!holdTimerArmed) return
        holdTimerArmed = false
        handler.removeCallbacks(holdRunnable)
    }

    private fun isSingleActivePointer(event: MotionEvent): Boolean {
        if (event.pointerCount != 1) return false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_UP
        ) return false
        return event.getPointerId(0) == lastPointerId
    }

    private fun inwardVelocityPxPerSecond(currentXpx: Float): Float {
        val elapsedMs = lastEventTime - previousMoveTimeMs
        if (elapsedMs <= 0L) return 0f
        val dx = currentXpx - previousMoveX
        val inwardDx = when (side) {
            EdgeSide.LEFT -> dx
            EdgeSide.RIGHT -> -dx
        }
        return inwardDx.coerceAtLeast(0f) * 1000f / elapsedMs
    }

    private fun compatibilityRecognizer(screenWidthDp: Float, screenHeightDp: Float): SideGestureRecognizer {
        val coreDensity = if (usesDpCoreCoordinates) safeDensity else 1f
        val minPullDp = minSwipeDistancePx / coreDensity
        val longPullDp = longSwipeDistancePx / coreDensity
        val minVerticalDp = (minVerticalSwipeDistancePx / coreDensity).coerceAtLeast(minPullDp)
        return SideGestureRecognizer(
            side = side,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            zones = listOf(
                HotZoneSegment(
                    startDp = 0f,
                    lengthDp = screenHeightDp,
                    thicknessDp = screenWidthDp.coerceAtLeast(1f),
                    zoneId = 0
                )
            ),
            actions = emptyMap<GestureType, GestureAction>(),
            thresholds = SideGestureThresholds(
                minPullDistanceDp = minPullDp.coerceAtLeast(1f),
                longPullDistanceDp = longPullDp.coerceAtLeast(minPullDp),
                minSwipeDistanceDp = minVerticalDp.coerceAtLeast(1f),
                holdDurationMs = holdDurationMs,
                holdSlopDp = 12f,
                retractionToleranceDp = 8f
            )
        )
    }

    private fun fallbackWidthPx(): Float =
        (minSwipeDistancePx + longSwipeDistancePx).coerceAtLeast(1f)

    private fun fallbackHeightPx(): Float = Float.POSITIVE_INFINITY

    private fun toCoreCoordinate(pixel: Float): Float =
        if (usesDpCoreCoordinates) pixel / safeDensity else pixel

    private fun GesturePreviewSink.cancelIfSupported() {
        (this as? CancellableGesturePreviewSink)?.cancel()
    }

    private companion object {
        const val DEFAULT_HOLD_DURATION_MS = 420L
        const val INVALID_POINTER_ID = -1
    }
}

/** Optional extension for frame-coalescing preview sinks. */
interface CancellableGesturePreviewSink : GesturePreviewSink {
    fun cancel()
}
