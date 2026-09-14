package com.paifa.univerge.accessibility

import com.paifa.univerge.core.gesture.GestureEventLedger

/**
 * Small bounded terminal gate at the service action boundary.
 *
 * A gesture id is allocated by the core recognizer and remains stable while
 * the adapter is finishing the transaction. Zero is used by non-gesture
 * callers, which must retain their existing repeatable semantics.
 */
internal class GestureActionCommitGate(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val ledger = GestureEventLedger(maxEntries)

    fun accept(gestureId: Long): Boolean = ledger.acceptGestureId(gestureId)

    fun clear() = ledger.clear()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 128
    }
}
