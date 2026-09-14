package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.gesture.SwipeClassifier
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import kotlin.math.abs

data class SideGestureThresholds(
    val minPullDistanceDp: Float = 24f,
    val longPullDistanceDp: Float = 96f,
    val minSwipeDistanceDp: Float = 48f,
    val holdDurationMs: Long = 500L,
    val holdSlopDp: Float = 12f,
    val retractionToleranceDp: Float = 8f
)

/** Pure transaction recognizer. It never executes an action and never schedules work. */
class SideGestureRecognizer(
    private val side: EdgeSide,
    private val screenWidthDp: Float,
    private val screenHeightDp: Float,
    zones: List<HotZoneSegment>,
    actions: Map<GestureType, GestureAction>,
    private val thresholds: SideGestureThresholds = SideGestureThresholds(),
    private val snapshotVersion: Long = 0L
) {
    constructor(
        snapshot: ConfigSnapshot,
        side: EdgeSide,
        screenWidthDp: Float,
        screenHeightDp: Float,
        thresholds: SideGestureThresholds = SideGestureThresholds()
    ) : this(
        side = side,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        zones = snapshot.sideZones[side].orEmpty(),
        actions = snapshot.sideActions[side].orEmpty(),
        thresholds = thresholds,
        snapshotVersion = snapshot.revision
    )

    private val zones = zones.toList()
    private val actions = actions.toMap()
    private val classifier = SwipeClassifier(diagonalPullsEnabled = true)
    private var state = State.Idle
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var latestX = 0f
    private var latestY = 0f
    private var latestAt = 0L
    private var activePointerId = 0
    private var zoneId = -1
    private var gestureId = 0L
    private var maxInward = 0f
    private var movedBeyondHoldSlop = false
    private var holdArmed = false
    private var candidate: GestureType? = null
    private var preview: GestureSignal.Preview? = null

    val activeGestureId: Long
        get() = if (state == State.Tracking) gestureId else 0L

    fun onDown(sample: PointerSample): GestureSignal = onDown(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onDown(
        xDp: Float,
        yDp: Float,
        timeMillis: Long,
        pointerId: Int = 0,
        pointerCount: Int = 1
    ): GestureSignal {
        if (state == State.Committed || state == State.Cancelled) state = State.Idle
        if (state != State.Idle || pointerCount != 1) return GestureSignal.Ignored
        val matchedZone = matchingZoneIndex(xDp, yDp)
        if (matchedZone < 0) return GestureSignal.Ignored
        gestureId = GestureSessionIds.next()
        activePointerId = pointerId
        zoneId = zones[matchedZone].zoneId
        downX = xDp
        downY = yDp
        downAt = timeMillis
        latestX = xDp
        latestY = yDp
        latestAt = timeMillis
        maxInward = 0f
        movedBeyondHoldSlop = false
        holdArmed = false
        candidate = null
        preview = null
        state = State.Tracking
        return GestureSignal.Ignored
    }

    /** Returns whether the supplied point belongs to one of this side's configured zones. */
    fun hitTest(xDp: Float, yDp: Float): Boolean = matchingZoneIndex(xDp, yDp) >= 0

    fun onMove(sample: PointerSample): GestureSignal = onMove(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onMove(
        xDp: Float,
        yDp: Float,
        timeMillis: Long,
        pointerId: Int = activePointerId,
        pointerCount: Int = 1
    ): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        if (pointerCount != 1 || pointerId != activePointerId) return cancel()
        latestX = xDp
        latestY = yDp
        latestAt = timeMillis
        val inward = inwardDistance(xDp)
        if (inward < 0f) return cancel()
        if (inward > maxInward) maxInward = inward
        if (candidate != null && inward < maxInward - thresholds.retractionToleranceDp) return cancel()
        if (abs(yDp - downY) > thresholds.holdSlopDp) movedBeyondHoldSlop = true
        if (canArmHold(timeMillis, inward)) holdArmed = true
        val gesture = classify(inward, yDp - downY) ?: return GestureSignal.Ignored
        candidate = gesture
        return updatePreview(gesture, progress(inward), xDp, yDp)
    }

    /** Timer callbacks may arm a hold candidate, but never commit or execute it. */
    fun onHoldTimer(timeMillis: Long): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        val inward = inwardDistance(latestX)
        if (canArmHold(timeMillis, inward)) holdArmed = true
        return GestureSignal.Ignored
    }

    fun onTimer(timeMillis: Long): GestureSignal = onHoldTimer(timeMillis)

    fun onUp(sample: PointerSample): GestureSignal = onUp(
        sample.xDp, sample.yDp, sample.timeMillis, sample.pointerId, sample.pointerCount
    )

    fun onUp(
        xDp: Float,
        yDp: Float,
        timeMillis: Long,
        pointerId: Int = activePointerId,
        pointerCount: Int = 1
    ): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        if (pointerCount != 1 || pointerId != activePointerId) return cancel()
        latestX = xDp
        latestY = yDp
        latestAt = timeMillis
        val inward = inwardDistance(xDp)
        if (inward < 0f || (candidate != null && (inward < maxInward - thresholds.retractionToleranceDp || inward < thresholds.minPullDistanceDp))) return cancel()
        if (abs(yDp - downY) > thresholds.holdSlopDp) movedBeyondHoldSlop = true
        if (canArmHold(timeMillis, inward)) holdArmed = true
        val gesture = classify(inward, yDp - downY) ?: return cancel()
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
                zoneId = zoneId
            ),
            gestureId = gestureId,
            activePointerId = activePointerId,
            zoneId = zoneId,
            snapshotVersion = snapshotVersion
        )
    }

    fun onCancel(): GestureSignal = cancel()

    fun onFocusLost(): GestureSignal = if (state == State.Tracking) cancel() else GestureSignal.Ignored

    private fun classify(inward: Float, dy: Float): GestureType? {
        if (holdArmed && !movedBeyondHoldSlop && inward >= thresholds.minPullDistanceDp && abs(dy) <= thresholds.holdSlopDp) {
            return GestureType.PULL_INWARD_HOLD
        }
        val dx = when (side) {
            EdgeSide.LEFT -> inward
            EdgeSide.RIGHT -> -inward
        }
        val base = classifier.classify(side, dx, dy) ?: return null
        return when (base) {
            GestureType.PULL_INWARD_SHORT,
            GestureType.PULL_INWARD -> if (inward >= thresholds.longPullDistanceDp) {
                GestureType.PULL_INWARD_LONG
            } else if (inward >= thresholds.minPullDistanceDp) {
                GestureType.PULL_INWARD_SHORT
            } else null
            GestureType.PULL_DIAGONAL_UP -> diagonalVariant(GestureType.PULL_DIAGONAL_UP, inward)
            GestureType.PULL_DIAGONAL_DOWN -> diagonalVariant(GestureType.PULL_DIAGONAL_DOWN, inward)
            GestureType.SWIPE_UP -> if (abs(dy) >= thresholds.minSwipeDistanceDp) GestureType.SWIPE_UP else null
            GestureType.SWIPE_DOWN -> if (abs(dy) >= thresholds.minSwipeDistanceDp) GestureType.SWIPE_DOWN else null
            else -> base
        }
    }

    private fun diagonalVariant(base: GestureType, inward: Float): GestureType? {
        if (inward < thresholds.minPullDistanceDp) return null
        val long = inward >= thresholds.longPullDistanceDp
        return when (base) {
            GestureType.PULL_DIAGONAL_UP -> if (long) GestureType.PULL_DIAGONAL_UP_LONG else GestureType.PULL_DIAGONAL_UP_SHORT
            GestureType.PULL_DIAGONAL_DOWN -> if (long) GestureType.PULL_DIAGONAL_DOWN_LONG else GestureType.PULL_DIAGONAL_DOWN_SHORT
            else -> null
        }
    }

    private fun canArmHold(timeMillis: Long, inward: Float): Boolean =
        timeMillis - downAt >= thresholds.holdDurationMs &&
            inward >= thresholds.minPullDistanceDp &&
            !movedBeyondHoldSlop &&
            abs(latestY - downY) <= thresholds.holdSlopDp

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
                zoneId = zoneId
            ),
            gestureId = gestureId,
            activePointerId = activePointerId,
            zoneId = zoneId,
            snapshotVersion = snapshotVersion
        ).also { preview = it }
    }

    private fun progress(inward: Float): Float =
        (inward / thresholds.longPullDistanceDp.coerceAtLeast(1f)).coerceIn(0f, 1f)

    private fun inwardDistance(xDp: Float): Float = when (side) {
        EdgeSide.LEFT -> xDp - downX
        EdgeSide.RIGHT -> downX - xDp
    }

    private fun matchingZoneIndex(xDp: Float, yDp: Float): Int {
        if (yDp !in 0f..screenHeightDp) return -1
        return zones.indexOfFirst { zone ->
            if (!zone.enabled || zone.lengthDp <= 0f || zone.thicknessDp < 0f) return@indexOfFirst false
            val start = zone.startDp.coerceIn(0f, screenHeightDp)
            val end = (zone.startDp + zone.lengthDp).coerceIn(0f, screenHeightDp)
            if (end <= start || yDp !in start..end) return@indexOfFirst false
            when (side) {
                EdgeSide.LEFT -> {
                    val start = zone.edgeInsetDp.coerceAtLeast(0f)
                    xDp in start..(start + zone.thicknessDp)
                }
                EdgeSide.RIGHT -> {
                    val end = (screenWidthDp - zone.edgeInsetDp.coerceAtLeast(0f)).coerceAtLeast(0f)
                    xDp in (end - zone.thicknessDp).coerceAtLeast(0f)..end
                }
            }
        }
    }

    private fun cancel(): GestureSignal {
        if (state != State.Tracking) return GestureSignal.Ignored
        state = State.Cancelled
        return GestureSignal.Cancel(gestureId, activePointerId, zoneId, snapshotVersion)
    }

    private enum class State { Idle, Tracking, Committed, Cancelled }
}
