package com.paifa.univerge.heavydrag.core

import kotlin.math.max

/**
 * Coordinates one root's pointer stream. The class deliberately knows nothing
 * about Android views, Compose nodes, or the classifier implementation.
 */
class HeavyDragCoordinator(
    val policy: HeavyDragPolicy = HeavyDragPolicy(),
    private val onCallbackError: (Throwable) -> Unit = {}
) {
    val sources = HeavyDragSourceRegistry()
    val targets = HeavyDragTargetRegistry()

    @Volatile
    var state: HeavyDragState = HeavyDragState.IDLE
        private set

    private var active: Session? = null
    private var nextGestureId = 1L

    @get:Synchronized
    val activeSession: HeavyDragSessionSnapshot?
        get() = active?.snapshot(state)

    @Synchronized
    fun registerSource(source: HeavyDragSource): HeavyRegistration {
        return sources.registerObserved(source) { sourceId ->
            synchronized(this@HeavyDragCoordinator) {
                val session = active
                if (session != null && session.source.id == sourceId) {
                    cancelSession(session, HeavyDragCancelReason.SOURCE_UNREGISTERED, session.lastTime)
                }
            }
        }
    }

    @Synchronized
    fun updateSource(source: HeavyDragSource): HeavyRegistration = registerSource(source)

    @Synchronized
    fun updateSourceBounds(sourceId: String, bounds: HeavyRect): Boolean {
        return sources.updateBounds(sourceId, bounds)
    }

    @Synchronized
    fun unregisterSource(sourceId: String): Boolean {
        val session = active
        if (session != null && session.source.id == sourceId) {
            cancelSession(session, HeavyDragCancelReason.SOURCE_UNREGISTERED, session.lastTime)
        }
        return sources.unregister(sourceId)
    }

    @Synchronized
    fun registerTarget(target: HeavyDragTarget): HeavyRegistration {
        return targets.registerObserved(target) { targetId ->
            synchronized(this@HeavyDragCoordinator) {
                val session = active
                if (session != null && session.dragStarted) {
                    removeActiveTarget(session, targetId, session.lastTime)
                }
            }
        }
    }

    @Synchronized
    fun updateTarget(target: HeavyDragTarget): HeavyRegistration = registerTarget(target)

    @Synchronized
    fun updateTargetBounds(targetId: String, bounds: HeavyRect): Boolean {
        return targets.updateBounds(targetId, bounds)
    }

    @Synchronized
    fun unregisterTarget(targetId: String): Boolean {
        val session = active
        if (session != null && session.dragStarted) {
            removeActiveTarget(session, targetId, session.lastTime)
        }
        return targets.unregister(targetId)
    }

    /** Starts a candidate session if [position] is inside a registered source. */
    @Synchronized
    fun onPointerDown(
        pointerId: Int,
        position: HeavyPoint,
        timestampMillis: Long,
        gestureId: Long? = null,
        pointerCount: Int = 1
    ): Long? {
        require(pointerId >= 0) { "pointerId must be non-negative" }
        require(pointerCount > 0) { "pointerCount must be positive" }

        val existing = active
        if (existing != null) {
            cancelSession(existing, HeavyDragCancelReason.MULTI_POINTER, timestampMillis)
            return null
        }
        if (pointerCount != 1) return null

        val source = sources.findAt(position) ?: return null
        val id = allocateGestureId(gestureId)
        active = Session(
            id = id,
            source = source,
            pointerId = pointerId,
            downPoint = position,
            downTime = timestampMillis,
            lastPoint = position,
            lastTime = timestampMillis,
            currentBounds = source.bounds
        )
        state = HeavyDragState.PRESSED_PENDING
        return id
    }

    @Synchronized
    fun onPointerMove(
        pointerId: Int,
        position: HeavyPoint,
        timestampMillis: Long,
        gestureId: Long? = null,
        pointerCount: Int = 1
    ): HeavyDragDispatch {
        if (pointerCount != 1) {
            return cancelForMultiPointer(
                pointerId = pointerId,
                gestureId = gestureId,
                timestampMillis = timestampMillis
            )
        }
        val session = sessionFor(pointerId, gestureId)
            ?: return ignoredDispatch(gestureId)
        advance(session, position, timestampMillis)

        if (!session.dragStarted) {
            if (session.downPoint.distanceTo(position) > policy.maxPendingMovementPx) {
                cancelSession(
                    session,
                    HeavyDragCancelReason.MOVED_BEFORE_CLASSIFICATION,
                    timestampMillis
                )
                return dispatch(session.id, consumed = false, cancelReason = HeavyDragCancelReason.MOVED_BEFORE_CLASSIFICATION)
            }
            return dispatch(session.id, consumed = true)
        }

        updateDraggedBounds(session)
        updateTargets(session, emitOver = true)
        if (session.terminated) return dispatch(session.id, consumed = true)
        emitDragMove(session)
        return dispatch(session.id, consumed = true)
    }

    @Synchronized
    fun onPointerUp(
        pointerId: Int,
        position: HeavyPoint,
        timestampMillis: Long,
        gestureId: Long? = null,
        pointerCount: Int = 1
    ): HeavyDragDispatch {
        if (pointerCount != 1) {
            return cancelForMultiPointer(
                pointerId = pointerId,
                gestureId = gestureId,
                timestampMillis = timestampMillis
            )
        }
        val session = sessionFor(pointerId, gestureId)
            ?: return ignoredDispatch(gestureId)
        advance(session, position, timestampMillis)

        if (!session.dragStarted) {
            if (session.downPoint.distanceTo(position) > policy.maxPendingMovementPx) {
                cancelSession(
                    session,
                    HeavyDragCancelReason.MOVED_BEFORE_CLASSIFICATION,
                    timestampMillis
                )
                return dispatch(session.id, consumed = false, cancelReason = HeavyDragCancelReason.MOVED_BEFORE_CLASSIFICATION)
            }
            completeClick(session)
            return dispatch(session.id, consumed = true)
        }

        updateDraggedBounds(session)
        // The final UP resolves enter/exit before commit; it is not another
        // sustained MOVE and therefore must not emit a duplicate over event.
        updateTargets(session, emitOver = false)
        completeDrag(session)
        return dispatch(session.id, consumed = true)
    }

    @Synchronized
    fun onPointerCancel(
        pointerId: Int,
        timestampMillis: Long,
        gestureId: Long? = null,
        pointerCount: Int = 1
    ): HeavyDragDispatch {
        val session = active ?: return ignoredDispatch(gestureId)
        if (gestureId != null && gestureId != session.id) return ignoredDispatch(gestureId)
        if (pointerId != session.pointerId) return ignoredDispatch(gestureId)
        if (pointerCount != 1) {
            cancelSession(session, HeavyDragCancelReason.MULTI_POINTER, timestampMillis)
            return dispatch(session.id, consumed = true, cancelReason = HeavyDragCancelReason.MULTI_POINTER)
        }
        cancelSession(session, HeavyDragCancelReason.ACTION_CANCEL, timestampMillis)
        return dispatch(session.id, consumed = true, cancelReason = HeavyDragCancelReason.ACTION_CANCEL)
    }

    /** Accepts the platform-neutral event form used by Android and Compose adapters. */
    @Synchronized
    fun handlePointerEvent(event: HeavyPointerEvent): HeavyDragDispatch {
        return when (event.action) {
            HeavyPointerAction.DOWN -> {
                val id = onPointerDown(
                    pointerId = event.pointerId,
                    position = event.position,
                    timestampMillis = event.timestampMillis,
                    gestureId = event.gestureId,
                    pointerCount = event.pointerCount
                )
                HeavyDragDispatch(
                    gestureId = id,
                    consumed = id != null,
                    state = state,
                    ignored = id == null
                )
            }

            HeavyPointerAction.MOVE -> onPointerMove(
                pointerId = event.pointerId,
                position = event.position,
                timestampMillis = event.timestampMillis,
                gestureId = event.gestureId,
                pointerCount = event.pointerCount
            )

            HeavyPointerAction.UP -> onPointerUp(
                pointerId = event.pointerId,
                position = event.position,
                timestampMillis = event.timestampMillis,
                gestureId = event.gestureId,
                pointerCount = event.pointerCount
            )

            HeavyPointerAction.CANCEL -> onPointerCancel(
                pointerId = event.pointerId,
                timestampMillis = event.timestampMillis,
                gestureId = event.gestureId,
                pointerCount = event.pointerCount
            )

            HeavyPointerAction.POINTER_DOWN,
            HeavyPointerAction.POINTER_UP -> {
                val session = active
                if (session == null) {
                    ignoredDispatch(event.gestureId)
                } else {
                    cancelSession(session, HeavyDragCancelReason.MULTI_POINTER, event.timestampMillis)
                    dispatch(
                        gestureId = session.id,
                        consumed = true,
                        cancelReason = HeavyDragCancelReason.MULTI_POINTER
                    )
                }
            }
        }
    }

    /**
     * Consumes a classifier result. A result can affect only the session with
     * the same id, and only while that session is still awaiting classification.
     */
    @Synchronized
    fun onClassification(
        result: HeavyTouchClassification,
        receivedAtMillis: Long = result.eventTimeMillis
    ): Boolean {
        val session = active ?: return false
        if (session.id != result.gestureId || session.dragStarted || session.classificationResolved) {
            return false
        }
        if (session.classificationExpired) return false
        if (result.eventTimeMillis > 0L && result.eventTimeMillis < session.downTime) {
            return false
        }

        // A classifier may report the original touch timestamp after newer
        // pointer events have arrived. The session clock is the lower bound
        // for arrival validation, so an old result cannot resurrect a timeout.
        val observedAt = maxOf(
            session.lastTime,
            receivedAtMillis.coerceAtLeast(0L),
            result.eventTimeMillis.coerceAtLeast(0L)
        )
        if (observedAt < session.downTime || isPastClassificationWindow(session, observedAt)) {
            session.classificationExpired = true
            return false
        }
        if (result.eventType != HeavyTouchEventType.PRESS) return false

        if (result.strength == HeavyTouchStrength.HEAVY) {
            if (result.confidence < policy.minHeavyConfidence) return false
            session.classificationResolved = true
            startDrag(session, observedAt)
            return true
        }

        if (result.strength == HeavyTouchStrength.LIGHT) {
            session.classificationResolved = true
        }
        return false
    }

    @Synchronized
    fun onClassifierResult(
        result: HeavyTouchClassification,
        receivedAtMillis: Long = result.eventTimeMillis
    ): Boolean = onClassification(result, receivedAtMillis)

    @Synchronized
    fun cancel(
        reason: HeavyDragCancelReason = HeavyDragCancelReason.ACTION_CANCEL,
        timestampMillis: Long = active?.lastTime ?: 0L
    ): HeavyDragDispatch {
        val session = active ?: return ignoredDispatch(null)
        cancelSession(session, reason, timestampMillis)
        return dispatch(session.id, consumed = true, cancelReason = reason)
    }

    @Synchronized
    fun onWindowLostFocus(timestampMillis: Long = active?.lastTime ?: 0L): HeavyDragDispatch {
        return cancel(HeavyDragCancelReason.WINDOW_LOST_FOCUS, timestampMillis)
    }

    @Synchronized
    fun dispose(timestampMillis: Long = active?.lastTime ?: 0L): HeavyDragDispatch {
        return cancel(HeavyDragCancelReason.HOST_DISPOSED, timestampMillis)
    }

    private fun startDrag(session: Session, timestampMillis: Long) {
        if (session.terminated || session.dragStarted) return
        session.dragStarted = true
        state = HeavyDragState.DRAGGING
        session.lastTime = max(session.lastTime, timestampMillis)
        updateDraggedBounds(session)
        emitSafely {
            session.source.onDragStart(
                HeavyDragStartEvent(
                    gestureId = session.id,
                    sourceId = session.source.id,
                    payload = session.source.payload,
                    pointerId = session.pointerId,
                    position = session.lastPoint,
                    initialBounds = session.initialBounds,
                    currentBounds = session.currentBounds,
                    timestampMillis = session.lastTime
                )
            )
        }
        if (session.terminated) return
        updateTargets(session, emitOver = false)
    }

    private fun emitDragMove(session: Session) {
        emitSafely {
            session.source.onDrag(
                HeavyDragMoveEvent(
                    gestureId = session.id,
                    sourceId = session.source.id,
                    payload = session.source.payload,
                    pointerId = session.pointerId,
                    position = session.lastPoint,
                    initialBounds = session.initialBounds,
                    currentBounds = session.currentBounds,
                    timestampMillis = session.lastTime
                )
            )
        }
    }

    private fun completeClick(session: Session) {
        if (!detach(session)) return
        emitSafely {
            session.source.onClick(
                HeavyClickEvent(
                    gestureId = session.id,
                    sourceId = session.source.id,
                    payload = session.source.payload,
                    position = session.lastPoint,
                    timestampMillis = session.lastTime
                )
            )
        }
    }

    private fun completeDrag(session: Session) {
        if (session.terminated) return
        val dropTarget = session.activeDropZone
        val overlapTargets = session.activeOverlaps.values.toList()
        val dropTargetId = dropTarget?.id
        val overlapTargetIds = overlapTargets.mapTo(LinkedHashSet()) { it.id }
        val endEvent = HeavyDragEndEvent(
            gestureId = session.id,
            sourceId = session.source.id,
            payload = session.source.payload,
            pointerId = session.pointerId,
            position = session.lastPoint,
            initialBounds = session.initialBounds,
            currentBounds = session.currentBounds,
            dropTargetId = dropTargetId,
            overlapTargetIds = overlapTargetIds,
            timestampMillis = session.lastTime
        )
        if (!detach(session)) return

        if (dropTarget != null) {
            emitSafely {
                dropTarget.onDrop(targetEvent(session, dropTarget, overlapRatio = null))
            }
        }
        overlapTargets.forEach { target ->
            emitSafely {
                target.onOverlapCommit(targetEvent(session, target, session.currentBounds.overlapRatio(target.bounds)))
            }
        }
        emitSafely { session.source.onDragEnd(endEvent) }
        session.activeDropZone = null
        session.activeOverlaps.clear()
    }

    private fun cancelSession(
        session: Session,
        reason: HeavyDragCancelReason,
        timestampMillis: Long
    ) {
        if (!detach(session)) return
        val eventTimestamp = max(session.lastTime, timestampMillis)
        if (session.activeDropZone != null) {
            val target = session.activeDropZone!!
            emitSafely { target.onExit(targetEvent(session, target, overlapRatio = null, timestampMillis = eventTimestamp)) }
            session.activeDropZone = null
        }
        session.activeOverlaps.values.toList().forEach { target ->
            emitSafely {
                target.onOverlapExit(
                    targetEvent(
                        session,
                        target,
                        session.currentBounds.overlapRatio(target.bounds),
                        eventTimestamp
                    )
                )
            }
        }
        session.activeOverlaps.clear()
        emitSafely {
            session.source.onDragCancel(
                HeavyDragCancelEvent(
                    gestureId = session.id,
                    sourceId = session.source.id,
                    payload = session.source.payload,
                    pointerId = session.pointerId,
                    position = session.lastPoint,
                    initialBounds = session.initialBounds,
                    currentBounds = session.currentBounds,
                    reason = reason,
                    timestampMillis = eventTimestamp
                )
            )
        }
    }

    private fun updateTargets(session: Session, emitOver: Boolean) {
        updateDropZone(session, emitOver)
        if (session.terminated) return
        updateOverlapTargets(session, emitOver)
    }

    private fun updateDropZone(session: Session, emitOver: Boolean) {
        val candidate = targets.snapshot(HeavyTargetMode.DROP_ZONE).firstOrNull { target ->
            target.enabled && target.bounds.area > 0f && accepts(target, session.source.payload) && dropHit(target, session)
        }
        val previous = session.activeDropZone
        if (previous?.id != candidate?.id) {
            if (previous != null) {
                emitSafely { previous.onExit(targetEvent(session, previous, overlapRatio = null)) }
                if (session.terminated) return
            }
            session.activeDropZone = candidate
            if (candidate != null) {
                emitSafely { candidate.onEnter(targetEvent(session, candidate, overlapRatio = null)) }
                if (session.terminated) return
            }
        } else if (candidate != null) {
            // Keep callbacks and commit data aligned with the latest layout snapshot.
            session.activeDropZone = candidate
            if (emitOver) {
                emitSafely { candidate.onOver(targetEvent(session, candidate, overlapRatio = null)) }
                if (session.terminated) return
            }
        }
    }

    private fun updateOverlapTargets(session: Session, emitOver: Boolean) {
        val candidates = targets.snapshot(HeavyTargetMode.OVERLAP)
            .filter { it.enabled && it.bounds.area > 0f && accepts(it, session.source.payload) }
        val byId = candidates.associateBy { it.id }

        session.activeOverlaps.keys.toList().forEach { targetId ->
            if (session.terminated) return
            var activeTarget = session.activeOverlaps[targetId] ?: return@forEach
            val currentTarget = byId[targetId]
            if (currentTarget != null) {
                activeTarget = currentTarget
                session.activeOverlaps[targetId] = currentTarget
            }
            val ratio = session.currentBounds.overlapRatio(currentTarget?.bounds ?: activeTarget.bounds)
            val exitThreshold = currentTarget?.overlapExitThreshold ?: activeTarget.overlapExitThreshold
            if (currentTarget == null || ratio < exitThreshold) {
                emitSafely {
                    activeTarget.onOverlapExit(targetEvent(session, activeTarget, ratio))
                }
                if (session.terminated) return
                session.activeOverlaps.remove(targetId)
            } else if (emitOver) {
                emitSafely {
                    activeTarget.onOverlap(targetEvent(session, activeTarget, ratio))
                }
                if (session.terminated) return
            }
        }

        candidates.forEach { target ->
            if (session.terminated) return
            if (session.activeOverlaps.containsKey(target.id)) return@forEach
            val ratio = session.currentBounds.overlapRatio(target.bounds)
            if (ratio >= target.overlapEnterThreshold) {
                session.activeOverlaps[target.id] = target
                emitSafely { target.onOverlapEnter(targetEvent(session, target, ratio)) }
                if (session.terminated) return
            }
        }
    }

    private fun removeActiveTarget(session: Session, targetId: String, timestampMillis: Long) {
        val dropTarget = session.activeDropZone
        if (dropTarget?.id == targetId) {
            session.activeDropZone = null
            emitSafely {
                dropTarget.onExit(targetEvent(session, dropTarget, overlapRatio = null, timestampMillis = timestampMillis))
            }
        }
        val overlapTarget = session.activeOverlaps.remove(targetId)
        if (overlapTarget != null) {
            emitSafely {
                overlapTarget.onOverlapExit(
                    targetEvent(
                        session,
                        overlapTarget,
                        session.currentBounds.overlapRatio(overlapTarget.bounds),
                        timestampMillis
                    )
                )
            }
        }
    }

    private fun dropHit(target: HeavyDragTarget, session: Session): Boolean {
        val point = when (policy.dropHitMode) {
            HeavyDropHitMode.DRAGGED_CENTER -> session.currentBounds.center
            HeavyDropHitMode.POINTER -> session.lastPoint
        }
        return target.bounds.contains(point)
    }

    private fun targetEvent(
        session: Session,
        target: HeavyDragTarget,
        overlapRatio: Float?,
        timestampMillis: Long = session.lastTime
    ): HeavyTargetEvent {
        return HeavyTargetEvent(
            gestureId = session.id,
            sourceId = session.source.id,
            payload = session.source.payload,
            targetId = target.id,
            targetMode = target.mode,
            position = session.lastPoint,
            draggedBounds = session.currentBounds,
            targetBounds = target.bounds,
            overlapRatio = overlapRatio,
            timestampMillis = timestampMillis
        )
    }

    private fun accepts(target: HeavyDragTarget, payload: Any?): Boolean {
        return try {
            target.accepts(payload)
        } catch (error: Throwable) {
            reportCallbackError(error)
            false
        }
    }

    private fun advance(session: Session, position: HeavyPoint, timestampMillis: Long) {
        session.lastPoint = position
        session.lastTime = max(session.lastTime, timestampMillis)
        if (!session.dragStarted && isPastClassificationWindow(session, session.lastTime)) {
            session.classificationExpired = true
        }
    }

    private fun updateDraggedBounds(session: Session) {
        session.currentBounds = session.initialBounds.translatedBy(
            session.lastPoint.x - session.downPoint.x,
            session.lastPoint.y - session.downPoint.y
        )
    }

    private fun isPastClassificationWindow(session: Session, timestampMillis: Long): Boolean {
        return timestampMillis - session.downTime > policy.classificationTimeoutMillis
    }

    private fun sessionFor(
        pointerId: Int,
        gestureId: Long?
    ): Session? {
        val session = active ?: return null
        if (gestureId != null && gestureId != session.id) return null
        if (pointerId != session.pointerId) return null
        return session
    }

    private fun cancelForMultiPointer(
        pointerId: Int,
        gestureId: Long?,
        timestampMillis: Long
    ): HeavyDragDispatch {
        val session = active ?: return ignoredDispatch(gestureId)
        if (gestureId != null && gestureId != session.id) return ignoredDispatch(gestureId)
        if (pointerId != session.pointerId) return ignoredDispatch(gestureId)
        cancelSession(session, HeavyDragCancelReason.MULTI_POINTER, timestampMillis)
        return dispatch(session.id, consumed = true, cancelReason = HeavyDragCancelReason.MULTI_POINTER)
    }

    private fun completeSessionId(session: Session): Long = session.id

    private fun detach(session: Session): Boolean {
        if (session.terminated) return false
        session.terminated = true
        if (active === session) {
            active = null
            state = HeavyDragState.IDLE
        }
        return true
    }

    private fun allocateGestureId(preferred: Long?): Long {
        if (preferred != null && preferred > 0L) {
            if (preferred >= nextGestureId && preferred < Long.MAX_VALUE) {
                nextGestureId = preferred + 1L
            }
            return preferred
        }
        val id = if (nextGestureId > 0L) nextGestureId else 1L
        nextGestureId = if (id == Long.MAX_VALUE) 1L else id + 1L
        return id
    }

    private fun dispatch(
        gestureId: Long?,
        consumed: Boolean,
        cancelReason: HeavyDragCancelReason? = null
    ): HeavyDragDispatch {
        return HeavyDragDispatch(
            gestureId = gestureId,
            consumed = consumed,
            state = state,
            cancelReason = cancelReason
        )
    }

    private fun ignoredDispatch(gestureId: Long?): HeavyDragDispatch {
        return HeavyDragDispatch(
            gestureId = gestureId,
            consumed = false,
            state = state,
            ignored = true
        )
    }

    private fun emitSafely(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            reportCallbackError(error)
        }
    }

    private fun reportCallbackError(error: Throwable) {
        try {
            onCallbackError(error)
        } catch (_: Throwable) {
            // Error reporting must not break the active gesture cleanup path.
        }
    }

    private data class Session(
        val id: Long,
        val source: HeavyDragSource,
        val pointerId: Int,
        val downPoint: HeavyPoint,
        val downTime: Long,
        var lastPoint: HeavyPoint,
        var lastTime: Long,
        val initialBounds: HeavyRect = source.bounds,
        var currentBounds: HeavyRect,
        var dragStarted: Boolean = false,
        var classificationResolved: Boolean = false,
        var classificationExpired: Boolean = false,
        var terminated: Boolean = false,
        var activeDropZone: HeavyDragTarget? = null,
        val activeOverlaps: LinkedHashMap<String, HeavyDragTarget> = LinkedHashMap()
    ) {
        fun snapshot(state: HeavyDragState): HeavyDragSessionSnapshot {
            return HeavyDragSessionSnapshot(
                gestureId = id,
                sourceId = source.id,
                pointerId = pointerId,
                payload = source.payload,
                downPoint = downPoint,
                currentPoint = lastPoint,
                initialBounds = initialBounds,
                currentBounds = currentBounds,
                state = state
            )
        }
    }
}
