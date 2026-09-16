package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import kotlin.math.abs

private const val MOVEMENT_CLOCK_RESOLUTION_DP = 0.5f

data class BottomGestureThresholds(
    val minSwipeDistanceDp: Float = 48f,
    val longPressDurationMs: Long = 500L,
    val slopDp: Float = 12f,
    val retractionToleranceDp: Float = 8f,
    val upwardHoldDurationMs: Long = 500L,
    /** Short movement window used to distinguish a continuing swipe from an upward pause. */
    val upwardSwipeDisambiguationMs: Long = 120L
)

/** Bottom bar transaction recognizer; its action map is independent from side actions. */
class BottomGestureRecognizer(
    private var bar: BottomBarConfig,
    actions: Map<GestureType, GestureAction>,
    private val thresholds: BottomGestureThresholds = BottomGestureThresholds(),
    private val snapshotVersion: Long = 1L,
    /**
     * Gesture kinds that participate in recognition. This is intentionally
     * separate from [actions]: a caller may recognize a gesture and map it to
     * [GestureAction.None] without manufacturing a fake action binding.
     */
    private val enabledGestures: Set<GestureType> = actions
        .filterValues { it != GestureAction.None }
        .keys
        .toSet()
) {
    constructor(snapshot: ConfigSnapshot, thresholds: BottomGestureThresholds = BottomGestureThresholds()) : this(
        snapshot.bottomBar,
        snapshot.bottomActions,
        thresholds,
        snapshot.revision,
        snapshot.bottomActions
            .filterValues { it != GestureAction.None }
            .keys
            .toSet()
    )

    private val actions = actions.toMap()
    private var state = State.Idle
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var latestX = 0f
    private var latestY = 0f
    private var activePointerId = 0
    private var gestureId = 0L
    private var candidate: GestureType? = null
    private var preview: GestureSignal.Preview? = null

    val activeGestureId: Long
        get() = if (state == State.Tracking || state == State.Committed) gestureId else 0L
    private var holdArmed = false
    private var movedBeyondSlop = false
    private var peakDx = 0f
    private var peakDy = 0f
    private var lastMovementAt = 0L
    private var lastMovementX = 0f
    private var lastMovementY = 0f
    private var upwardHoldArmed = false
    private var upwardCandidateAt = Long.MIN_VALUE

    val hasActiveGesture: Boolean
        get() = state == State.Tracking || state == State.Committed

    /** Geometry may change in place only between transactions. */
    fun updateGeometry(next: BottomBarConfig): Boolean {
        if (hasActiveGesture) return false
        bar = next
        return true
    }

    fun onDown(sample: PointerSample): GestureSignal = onDown(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onDown(xDp: Float, yDp: Float, timeMillis: Long, pointerId: Int = 0, pointerCount: Int = 1): GestureSignal {
        if (state == State.Committed || state == State.Cancelled) state = State.Idle
        if (state != State.Idle || pointerCount != 1) return GestureSignal.Ignored
        if (xDp !in bar.leftDp..bar.rightDp || yDp !in bar.topDp..bar.screenHeightDp) return GestureSignal.Ignored
        gestureId = GestureSessionIds.next()
        activePointerId = pointerId
        downX = xDp
        downY = yDp
        downAt = timeMillis
        latestX = xDp
        latestY = yDp
        candidate = null
        preview = null
        holdArmed = false
        movedBeyondSlop = false
        peakDx = 0f
        peakDy = 0f
        lastMovementAt = timeMillis
        lastMovementX = xDp
        lastMovementY = yDp
        upwardHoldArmed = false
        upwardCandidateAt = Long.MIN_VALUE
        state = State.Tracking
        return GestureSignal.Ignored
    }

    /** Returns whether the supplied point belongs to the configured bottom bar. */
    fun hitTest(xDp: Float, yDp: Float): Boolean =
        xDp in bar.leftDp..bar.rightDp && yDp in bar.topDp..bar.screenHeightDp

    fun onMove(sample: PointerSample): GestureSignal = onMove(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onMove(xDp: Float, yDp: Float, timeMillis: Long, pointerId: Int = activePointerId, pointerCount: Int = 1): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        if (pointerCount != 1 || pointerId != activePointerId) return cancel()
        // A platform can deliver repeated or slightly jittery MOVE samples
        // while the finger is held still. Refresh the pause window only after
        // movement accumulates beyond the clock's noise resolution.
        val movedSinceLastSample = movementExceededResolution(xDp, yDp)
        latestX = xDp
        latestY = yDp
        val dx = xDp - downX
        val dy = yDp - downY
        if (abs(dx) > abs(peakDx)) peakDx = dx
        if (abs(dy) > abs(peakDy)) peakDy = dy
        if (movedSinceLastSample) {
            lastMovementX = xDp
            lastMovementY = yDp
            lastMovementAt = maxOf(lastMovementAt, timeMillis)
            upwardHoldArmed = false
        }
        if (candidate != null && rollback(dx, dy)) return cancel()
        if (abs(dx) > thresholds.slopDp || abs(dy) > thresholds.slopDp) movedBeyondSlop = true
        if (canArmHold(timeMillis)) holdArmed = true
        val gesture = classify(dx, dy, timeMillis) ?: return GestureSignal.Ignored
        if (gesture == GestureType.SWIPE_UP && upwardCandidateAt == Long.MIN_VALUE) {
            upwardCandidateAt = timeMillis
        }
        candidate = gesture
        val preview = updatePreview(gesture, progress(dx, dy), xDp, yDp)
        return commitWhenReady(
            gesture = gesture,
            xDp = xDp,
            yDp = yDp,
            timeMillis = timeMillis,
            movedSinceLastSample = movedSinceLastSample,
            preview = preview
        )
    }

    fun onHoldTimer(timeMillis: Long): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        if (canArmHold(timeMillis)) {
            holdArmed = true
            candidate = GestureType.LONG_PRESS
            val preview = updatePreview(GestureType.LONG_PRESS, 1f, latestX, latestY)
            return commitWhenReady(
                gesture = GestureType.LONG_PRESS,
                xDp = latestX,
                yDp = latestY,
                timeMillis = timeMillis,
                movedSinceLastSample = false,
                preview = preview
            )
        }
        upwardHoldArmed = hasAction(GestureType.SWIPE_UP_HOLD) && isUpwardHoldCandidate(timeMillis)
        if (upwardHoldArmed) {
            candidate = GestureType.SWIPE_UP_HOLD
            val preview = updatePreview(
                GestureType.SWIPE_UP_HOLD,
                progress(latestX - downX, latestY - downY),
                latestX,
                latestY
            )
            return commitWhenReady(
                gesture = GestureType.SWIPE_UP_HOLD,
                xDp = latestX,
                yDp = latestY,
                timeMillis = timeMillis,
                movedSinceLastSample = false,
                preview = preview
            )
        }
        return GestureSignal.Ignored
    }

    fun onTimer(timeMillis: Long): GestureSignal = onHoldTimer(timeMillis)

    fun onUp(sample: PointerSample): GestureSignal = onUp(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onUp(xDp: Float, yDp: Float, timeMillis: Long, pointerId: Int = activePointerId, pointerCount: Int = 1): GestureSignal {
        if (state == State.Committed) {
            state = State.Idle
            return GestureSignal.Ignored
        }
        if (state != State.Tracking) return GestureSignal.Ignored
        if (pointerCount != 1 || pointerId != activePointerId) return cancel()
        val movedSinceLastSample = movementExceededResolution(xDp, yDp)
        latestX = xDp
        latestY = yDp
        if (movedSinceLastSample) {
            lastMovementX = xDp
            lastMovementY = yDp
            lastMovementAt = maxOf(lastMovementAt, timeMillis)
            upwardHoldArmed = false
        }
        val dx = xDp - downX
        val dy = yDp - downY
        if (candidate != null && rollback(dx, dy)) return cancel()
        if (abs(dx) > thresholds.slopDp || abs(dy) > thresholds.slopDp) movedBeyondSlop = true
        if (canArmHold(timeMillis)) holdArmed = true
        val gesture = classify(dx, dy, timeMillis) ?: return cancel()
        val commit = GestureSignal.Commit(
            gesture = gesture,
            action = actions[gesture] ?: GestureAction.None,
            data = GestureData(
                startX = downX,
                startY = downY,
                endX = xDp,
                endY = yDp,
                gestureId = gestureId,
                snapshotVersion = snapshotVersion,
                zoneId = -1
            ),
            gestureId = gestureId,
            activePointerId = activePointerId,
            zoneId = -1,
            snapshotVersion = snapshotVersion
        )
        // ACTION_UP is the terminal boundary for a release-based gesture.
        // Keep the commit payload, but make the recognizer idle immediately so
        // geometry/configuration updates are not blocked after the action ran.
        state = State.Idle
        return commit
    }

    fun onCancel(): GestureSignal = cancel()

    fun onFocusLost(): GestureSignal = when (state) {
        State.Tracking -> cancel()
        State.Committed -> {
            state = State.Idle
            GestureSignal.Ignored
        }
        else -> GestureSignal.Ignored
    }

    private fun classify(dx: Float, dy: Float, timeMillis: Long): GestureType? {
        if (holdArmed && !movedBeyondSlop && abs(dx) <= thresholds.slopDp && abs(dy) <= thresholds.slopDp) {
            return GestureType.LONG_PRESS
        }
        if (abs(dx) >= abs(dy) && abs(dx) >= thresholds.minSwipeDistanceDp) {
            return if (dx < 0f) GestureType.SWIPE_LEFT else GestureType.SWIPE_RIGHT
        }
        if (dy <= -thresholds.minSwipeDistanceDp) {
            return if (
                hasAction(GestureType.SWIPE_UP_HOLD) &&
                    (upwardHoldArmed || isUpwardHoldCandidate(timeMillis))
            ) {
                GestureType.SWIPE_UP_HOLD
            } else {
                GestureType.SWIPE_UP
            }
        }
        if (dy >= thresholds.minSwipeDistanceDp) return GestureType.SWIPE_DOWN
        return GestureType.TAP
    }

    private fun canArmHold(timeMillis: Long): Boolean =
        timeMillis - downAt >= thresholds.longPressDurationMs &&
            abs(latestX - downX) <= thresholds.slopDp &&
            abs(latestY - downY) <= thresholds.slopDp &&
            !movedBeyondSlop

    private fun isUpwardHoldCandidate(timeMillis: Long): Boolean =
        peakDy <= -thresholds.minSwipeDistanceDp &&
            abs(peakDy) > abs(peakDx) &&
            timeMillis - lastMovementAt >= thresholds.upwardHoldDurationMs

    private fun movementExceededResolution(xDp: Float, yDp: Float): Boolean {
        val slop = thresholds.slopDp.coerceAtLeast(0f)
        val resolution = minOf(slop, MOVEMENT_CLOCK_RESOLUTION_DP)
        return if (resolution == 0f) {
            xDp != lastMovementX || yDp != lastMovementY
        } else {
            abs(xDp - lastMovementX) > resolution || abs(yDp - lastMovementY) > resolution
        }
    }

    private fun rollback(dx: Float, dy: Float): Boolean =
        (peakDx > thresholds.minSwipeDistanceDp && dx < peakDx - thresholds.retractionToleranceDp) ||
            (peakDx < -thresholds.minSwipeDistanceDp && dx > peakDx + thresholds.retractionToleranceDp) ||
            (peakDy > thresholds.minSwipeDistanceDp && dy < peakDy - thresholds.retractionToleranceDp) ||
            (peakDy < -thresholds.minSwipeDistanceDp && dy > peakDy + thresholds.retractionToleranceDp)

    private fun progress(dx: Float, dy: Float): Float =
        (maxOf(abs(dx), abs(dy)) / thresholds.minSwipeDistanceDp.coerceAtLeast(1f)).coerceIn(0f, 1f)

    private fun updatePreview(gesture: GestureType, progress: Float, xDp: Float, yDp: Float): GestureSignal.Preview {
        val existing = preview
        if (existing != null) {
            existing.gesture = gesture
            existing.action = actions[gesture] ?: GestureAction.None
            existing.progress = progress
            existing.data.updateFrom(downX, downY, xDp, yDp)
            return existing
        }
        return GestureSignal.Preview(
            gesture = gesture,
            action = actions[gesture] ?: GestureAction.None,
            progress = progress,
            data = GestureData(
                startX = downX,
                startY = downY,
                endX = xDp,
                endY = yDp,
                gestureId = gestureId,
                snapshotVersion = snapshotVersion,
                zoneId = -1
            ),
            gestureId = gestureId,
            activePointerId = activePointerId,
            zoneId = -1,
            snapshotVersion = snapshotVersion
        ).also { preview = it }
    }

    private fun commitWhenReady(
        gesture: GestureType,
        xDp: Float,
        yDp: Float,
        timeMillis: Long,
        movedSinceLastSample: Boolean,
        preview: GestureSignal.Preview
    ): GestureSignal {
        val upwardHoldConfigured = enabledGestures.contains(GestureType.SWIPE_UP_HOLD)
        val ready = when (gesture) {
            GestureType.TAP -> false
            // Both gestures share the same upward prefix. A configured normal
            // swipe is the latency-sensitive action, so it commits at the
            // first threshold-crossing MOVE. Hold remains available when the
            // normal swipe has no bound action.
            GestureType.SWIPE_UP -> !upwardHoldConfigured || hasBoundAction(GestureType.SWIPE_UP)
            GestureType.SWIPE_UP_HOLD,
            GestureType.SWIPE_DOWN,
            GestureType.SWIPE_LEFT,
            GestureType.SWIPE_RIGHT,
            GestureType.LONG_PRESS -> true
            else -> false
        }
        return if (ready) commit(gesture, xDp, yDp) else preview
    }

    private fun commit(gesture: GestureType, xDp: Float, yDp: Float): GestureSignal.Commit {
        state = State.Committed
        return GestureSignal.Commit(
            gesture = gesture,
            action = actions[gesture] ?: GestureAction.None,
            data = GestureData(
                startX = downX,
                startY = downY,
                endX = xDp,
                endY = yDp,
                gestureId = gestureId,
                snapshotVersion = snapshotVersion,
                zoneId = -1
            ),
            gestureId = gestureId,
            activePointerId = activePointerId,
            zoneId = -1,
            snapshotVersion = snapshotVersion
        )
    }

    private fun hasAction(gesture: GestureType): Boolean = enabledGestures.contains(gesture)

    private fun hasBoundAction(gesture: GestureType): Boolean =
        actions[gesture]?.let { it != GestureAction.None } == true

    private fun cancel(): GestureSignal {
        if (state == State.Committed) {
            state = State.Idle
            return GestureSignal.Ignored
        }
        if (state != State.Tracking) return GestureSignal.Ignored
        state = State.Cancelled
        return GestureSignal.Cancel(gestureId, activePointerId, -1, snapshotVersion)
    }

    private enum class State { Idle, Tracking, Committed, Cancelled }
}
