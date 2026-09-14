package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType

/** Primitive input copied from a platform event at the adapter boundary. */
data class PointerSample(
    val xDp: Float,
    val yDp: Float,
    val timeMillis: Long,
    val pointerCount: Int = 1,
    val pointerId: Int = 0
)

/** Domain-only result. MOVE updates the reusable Preview; only UP creates Commit. */
sealed interface GestureSignal {
    data object Ignored : GestureSignal

    data class Cancel(
        val gestureId: Long,
        val activePointerId: Int,
        val zoneId: Int,
        val snapshotVersion: Long
    ) : GestureSignal

    class Preview(
        var gesture: GestureType,
        var action: GestureAction,
        var progress: Float,
        val data: GestureData,
        val gestureId: Long,
        val activePointerId: Int,
        val zoneId: Int,
        val snapshotVersion: Long
    ) : GestureSignal

    data class Commit(
        val gesture: GestureType,
        val action: GestureAction,
        val data: GestureData,
        val gestureId: Long,
        val activePointerId: Int,
        val zoneId: Int,
        val snapshotVersion: Long
    ) : GestureSignal
}

internal object GestureSessionIds {
    private var nextId = 0L

    @Synchronized
    fun next(): Long {
        nextId += 1L
        return nextId
    }
}

/** Allocates an id for adapters that cannot directly host the core recognizer. */
fun nextGestureSessionId(): Long = GestureSessionIds.next()

internal fun GestureData.updateFrom(startX: Float, startY: Float, endX: Float, endY: Float) {
    this.startX = startX
    this.startY = startY
    this.endX = endX
    this.endY = endY
}
