package com.paifa.univerge.heavydrag.core

/** The semantic target type; the two modes intentionally have different commit rules. */
enum class HeavyTargetMode {
    DROP_ZONE,
    OVERLAP
}

enum class HeavyDragState {
    IDLE,
    PRESSED_PENDING,
    DRAGGING
}

enum class HeavyPointerAction {
    DOWN,
    MOVE,
    UP,
    POINTER_DOWN,
    POINTER_UP,
    CANCEL
}

/** Kept parallel to the classifier vocabulary without importing the classifier SDK. */
enum class HeavyTouchEventType {
    SAMPLE,
    PRESS,
    TAP,
    HOLD,
    DRAG,
    CANCEL,
    UNKNOWN
}

enum class HeavyTouchStrength {
    LIGHT,
    HEAVY,
    UNKNOWN
}

enum class HeavyDragCancelReason {
    MULTI_POINTER,
    POINTER_MISMATCH,
    ACTION_CANCEL,
    WINDOW_LOST_FOCUS,
    SOURCE_UNREGISTERED,
    TARGET_UNREGISTERED,
    MOVED_BEFORE_CLASSIFICATION,
    CLASSIFICATION_TIMEOUT,
    HOST_DISPOSED
}

enum class HeavyDropHitMode {
    DRAGGED_CENTER,
    POINTER
}

data class HeavyDragPolicy(
    val minHeavyConfidence: Float = 0.80f,
    val classificationTimeoutMillis: Long = 350L,
    val maxPendingMovementPx: Float = 24f,
    val dropHitMode: HeavyDropHitMode = HeavyDropHitMode.DRAGGED_CENTER
) {
    init {
        require(minHeavyConfidence in 0f..1f) { "minHeavyConfidence must be between 0 and 1" }
        require(classificationTimeoutMillis >= 0L) { "classificationTimeoutMillis must be non-negative" }
        require(maxPendingMovementPx >= 0f && maxPendingMovementPx.isFinite()) {
            "maxPendingMovementPx must be finite and non-negative"
        }
    }
}

data class HeavyTouchClassification(
    val gestureId: Long,
    val eventType: HeavyTouchEventType,
    val strength: HeavyTouchStrength,
    val confidence: Float,
    val eventTimeMillis: Long = 0L
) {
    init {
        require(gestureId > 0L) { "gestureId must be positive" }
        require(confidence.isFinite()) { "confidence must be finite" }
    }

    companion object {
        fun heavyPress(
            gestureId: Long,
            confidence: Float = 1f,
            eventTimeMillis: Long = 0L
        ): HeavyTouchClassification {
            return HeavyTouchClassification(
                gestureId = gestureId,
                eventType = HeavyTouchEventType.PRESS,
                strength = HeavyTouchStrength.HEAVY,
                confidence = confidence,
                eventTimeMillis = eventTimeMillis
            )
        }

        fun lightPress(
            gestureId: Long,
            confidence: Float = 1f,
            eventTimeMillis: Long = 0L
        ): HeavyTouchClassification {
            return HeavyTouchClassification(
                gestureId = gestureId,
                eventType = HeavyTouchEventType.PRESS,
                strength = HeavyTouchStrength.LIGHT,
                confidence = confidence,
                eventTimeMillis = eventTimeMillis
            )
        }
    }

    val isEligibleHeavyPress: Boolean
        get() = eventType == HeavyTouchEventType.PRESS && strength == HeavyTouchStrength.HEAVY
}

data class HeavyPointerEvent(
    val action: HeavyPointerAction,
    val pointerId: Int,
    val position: HeavyPoint,
    val timestampMillis: Long,
    val pointerCount: Int = 1,
    val gestureId: Long? = null
) {
    init {
        require(pointerId >= 0) { "pointerId must be non-negative" }
        require(pointerCount > 0) { "pointerCount must be positive" }
    }
}

typealias HeavyTouchEvent = HeavyPointerEvent

data class HeavyClickEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val position: HeavyPoint,
    val timestampMillis: Long
)

data class HeavyDragStartEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val pointerId: Int,
    val position: HeavyPoint,
    val initialBounds: HeavyRect,
    val currentBounds: HeavyRect,
    val timestampMillis: Long
)

data class HeavyDragMoveEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val pointerId: Int,
    val position: HeavyPoint,
    val initialBounds: HeavyRect,
    val currentBounds: HeavyRect,
    val timestampMillis: Long
)

data class HeavyDragEndEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val pointerId: Int,
    val position: HeavyPoint,
    val initialBounds: HeavyRect,
    val currentBounds: HeavyRect,
    val dropTargetId: String?,
    val overlapTargetIds: Set<String>,
    val timestampMillis: Long
)

data class HeavyDragCancelEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val pointerId: Int,
    val position: HeavyPoint,
    val initialBounds: HeavyRect,
    val currentBounds: HeavyRect,
    val reason: HeavyDragCancelReason,
    val timestampMillis: Long
)

data class HeavyTargetEvent(
    val gestureId: Long,
    val sourceId: String,
    val payload: Any?,
    val targetId: String,
    val targetMode: HeavyTargetMode,
    val position: HeavyPoint,
    val draggedBounds: HeavyRect,
    val targetBounds: HeavyRect,
    val overlapRatio: Float?,
    val timestampMillis: Long
)

data class HeavyDragSource(
    val id: String,
    val bounds: HeavyRect,
    val payload: Any? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val zIndex: Float = 0f,
    val onClick: (HeavyClickEvent) -> Unit = {},
    val onDragStart: (HeavyDragStartEvent) -> Unit = {},
    val onDrag: (HeavyDragMoveEvent) -> Unit = {},
    val onDragEnd: (HeavyDragEndEvent) -> Unit = {},
    val onDragCancel: (HeavyDragCancelEvent) -> Unit = {}
) {
    init {
        require(id.isNotBlank()) { "source id must not be blank" }
        require(zIndex.isFinite()) { "source zIndex must be finite" }
    }
}

data class HeavyDragTarget(
    val id: String,
    val bounds: HeavyRect,
    val mode: HeavyTargetMode = HeavyTargetMode.DROP_ZONE,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val zIndex: Float = 0f,
    val accepts: (Any?) -> Boolean = { true },
    val overlapEnterThreshold: Float = 0.20f,
    val overlapExitThreshold: Float = 0.10f,
    val onEnter: (HeavyTargetEvent) -> Unit = {},
    val onOver: (HeavyTargetEvent) -> Unit = {},
    val onExit: (HeavyTargetEvent) -> Unit = {},
    val onDrop: (HeavyTargetEvent) -> Unit = {},
    val onOverlapEnter: (HeavyTargetEvent) -> Unit = {},
    val onOverlap: (HeavyTargetEvent) -> Unit = {},
    val onOverlapExit: (HeavyTargetEvent) -> Unit = {},
    val onOverlapCommit: (HeavyTargetEvent) -> Unit = {}
) {
    init {
        require(id.isNotBlank()) { "target id must not be blank" }
        require(zIndex.isFinite()) { "target zIndex must be finite" }
        require(overlapEnterThreshold in 0f..1f) {
            "overlapEnterThreshold must be between 0 and 1"
        }
        require(overlapExitThreshold in 0f..overlapEnterThreshold) {
            "overlapExitThreshold must be between 0 and enter threshold"
        }
    }
}

typealias HeavyDropTarget = HeavyDragTarget
typealias HeavyOverlapTarget = HeavyDragTarget

data class HeavyDragSessionSnapshot(
    val gestureId: Long,
    val sourceId: String,
    val pointerId: Int,
    val payload: Any?,
    val downPoint: HeavyPoint,
    val currentPoint: HeavyPoint,
    val initialBounds: HeavyRect,
    val currentBounds: HeavyRect,
    val state: HeavyDragState
)

data class HeavyDragDispatch(
    val gestureId: Long?,
    val consumed: Boolean,
    val state: HeavyDragState,
    val ignored: Boolean = false,
    val cancelReason: HeavyDragCancelReason? = null
)

interface HeavyRegistration {
    val isDisposed: Boolean
    fun dispose()
}

class HeavyDragSourceRegistry {
    private data class Entry(val token: Long, val source: HeavyDragSource)

    private val entries = LinkedHashMap<String, Entry>()
    private var nextToken = 1L

    @Synchronized
    fun register(source: HeavyDragSource): HeavyRegistration {
        return registerInternal(source, onDisposed = null)
    }

    internal fun registerObserved(
        source: HeavyDragSource,
        onDisposed: (String) -> Unit
    ): HeavyRegistration {
        return registerInternal(source, onDisposed)
    }

    @Synchronized
    private fun registerInternal(
        source: HeavyDragSource,
        onDisposed: ((String) -> Unit)?
    ): HeavyRegistration {
        val token = nextToken++
        entries[source.id] = Entry(token, source)
        var disposed = false
        return object : HeavyRegistration {
            override val isDisposed: Boolean get() = disposed

            override fun dispose() {
                var removed = false
                synchronized(this@HeavyDragSourceRegistry) {
                    if (disposed) return
                    disposed = true
                    if (entries[source.id]?.token == token) {
                        entries.remove(source.id)
                        removed = true
                    }
                }
                if (removed) {
                    try {
                        onDisposed?.invoke(source.id)
                    } catch (_: Throwable) {
                        // Registry disposal must remain idempotent even when a host callback fails.
                    }
                }
            }
        }
    }

    fun upsert(source: HeavyDragSource): HeavyRegistration = register(source)

    @Synchronized
    fun unregister(sourceId: String): Boolean = entries.remove(sourceId) != null

    @Synchronized
    fun updateBounds(sourceId: String, bounds: HeavyRect): Boolean {
        val entry = entries[sourceId] ?: return false
        entries[sourceId] = entry.copy(source = entry.source.copy(bounds = bounds))
        return true
    }

    @Synchronized
    fun get(sourceId: String): HeavyDragSource? = entries[sourceId]?.source

    @Synchronized
    fun snapshot(): List<HeavyDragSource> = entries.values.map { it.source }

    @Synchronized
    internal fun findAt(point: HeavyPoint): HeavyDragSource? {
        return entries.values
            .asSequence()
            .filter { it.source.enabled && it.source.bounds.area > 0f && it.source.bounds.contains(point) }
            .sortedWith(
                compareByDescending<Entry> { it.source.priority }
                    .thenByDescending { it.source.zIndex }
                    .thenBy { it.token }
            )
            .map { it.source }
            .firstOrNull()
    }
}

class HeavyDragTargetRegistry {
    private data class Entry(val token: Long, val target: HeavyDragTarget)

    private val entries = LinkedHashMap<String, Entry>()
    private var nextToken = 1L

    @Synchronized
    fun register(target: HeavyDragTarget): HeavyRegistration {
        return registerInternal(target, onDisposed = null)
    }

    internal fun registerObserved(
        target: HeavyDragTarget,
        onDisposed: (String) -> Unit
    ): HeavyRegistration {
        return registerInternal(target, onDisposed)
    }

    @Synchronized
    private fun registerInternal(
        target: HeavyDragTarget,
        onDisposed: ((String) -> Unit)?
    ): HeavyRegistration {
        val token = nextToken++
        entries[target.id] = Entry(token, target)
        var disposed = false
        return object : HeavyRegistration {
            override val isDisposed: Boolean get() = disposed

            override fun dispose() {
                var removed = false
                synchronized(this@HeavyDragTargetRegistry) {
                    if (disposed) return
                    disposed = true
                    if (entries[target.id]?.token == token) {
                        entries.remove(target.id)
                        removed = true
                    }
                }
                if (removed) {
                    try {
                        onDisposed?.invoke(target.id)
                    } catch (_: Throwable) {
                        // Registry disposal must remain idempotent even when a host callback fails.
                    }
                }
            }
        }
    }

    fun upsert(target: HeavyDragTarget): HeavyRegistration = register(target)

    @Synchronized
    fun unregister(targetId: String): Boolean = entries.remove(targetId) != null

    @Synchronized
    fun updateBounds(targetId: String, bounds: HeavyRect): Boolean {
        val entry = entries[targetId] ?: return false
        entries[targetId] = entry.copy(target = entry.target.copy(bounds = bounds))
        return true
    }

    @Synchronized
    fun get(targetId: String): HeavyDragTarget? = entries[targetId]?.target

    @Synchronized
    fun snapshot(): List<HeavyDragTarget> = entries.values.map { it.target }

    @Synchronized
    internal fun snapshot(mode: HeavyTargetMode): List<HeavyDragTarget> {
        return entries.values
            .asSequence()
            .filter { it.target.mode == mode }
            .sortedWith(
                compareByDescending<Entry> { it.target.priority }
                    .thenByDescending { it.target.zIndex }
                    .thenBy { it.token }
            )
            .map { it.target }
            .toList()
    }
}
