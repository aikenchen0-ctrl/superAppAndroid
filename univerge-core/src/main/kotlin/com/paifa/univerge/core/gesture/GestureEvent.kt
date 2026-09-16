package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType

/** Platform-neutral event emitted by a gesture transaction. */
sealed interface GestureEvent {
    val gestureId: Long
    val snapshotVersion: Long
    val activePointerId: Int
    val zoneId: Int

    data class Preview(
        override val gestureId: Long,
        override val snapshotVersion: Long,
        override val activePointerId: Int,
        override val zoneId: Int,
        val gesture: GestureType,
        val action: GestureAction,
        val progress: Float,
        val data: GestureData
    ) : GestureEvent

    data class Commit(
        override val gestureId: Long,
        override val snapshotVersion: Long,
        override val activePointerId: Int,
        override val zoneId: Int,
        val gesture: GestureType,
        val action: GestureAction,
        val data: GestureData? = null
    ) : GestureEvent

    data class Cancel(
        override val gestureId: Long,
        override val snapshotVersion: Long,
        override val activePointerId: Int,
        override val zoneId: Int
    ) : GestureEvent
}

/** Guards the terminal side effect so a transaction can only finish once. */
class GestureEventLedger(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val terminalGestureIds = java.util.LinkedHashSet<Long>(maxEntries)

    @Synchronized
    fun accept(event: GestureEvent): Boolean {
        if (event is GestureEvent.Preview) return true
        return acceptGestureIdLocked(event.gestureId)
    }

    @Synchronized
    fun acceptGestureId(gestureId: Long): Boolean = acceptGestureIdLocked(gestureId)

    /** Returns an id to the available set when downstream dispatch rejects it. */
    @Synchronized
    fun releaseGestureId(gestureId: Long) {
        if (gestureId > 0L) terminalGestureIds.remove(gestureId)
    }

    private fun acceptGestureIdLocked(gestureId: Long): Boolean {
        if (gestureId <= 0L) return true
        if (!terminalGestureIds.add(gestureId)) return false
        while (terminalGestureIds.size > maxEntries) {
            terminalGestureIds.iterator().apply {
                next()
                remove()
            }
        }
        return true
    }

    @Synchronized
    fun clear() {
        terminalGestureIds.clear()
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 128
    }
}
