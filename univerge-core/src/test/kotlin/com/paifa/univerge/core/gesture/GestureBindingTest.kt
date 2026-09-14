package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureBindingTest {
    @Test
    fun resolvesSideAndBottomActionsWithoutCrossSurfaceFallback() {
        val bindings = GestureBindingSnapshot(
            version = 3L,
            actions = mapOf(
                GestureBinding.side(EdgeSide.LEFT, GestureType.PULL_INWARD_SHORT) to GestureAction.Back,
                GestureBinding.bottom(GestureType.SWIPE_UP) to GestureAction.Home
            )
        )

        assertEquals(
            GestureAction.Back,
            bindings.actionFor(EdgeSide.LEFT, GestureType.PULL_INWARD_SHORT)
        )
        assertEquals(
            GestureAction.None,
            bindings.actionFor(EdgeSide.RIGHT, GestureType.PULL_INWARD_SHORT)
        )
        assertEquals(GestureAction.Home, bindings.bottomActionFor(GestureType.SWIPE_UP))
        assertEquals(GestureAction.None, bindings.bottomActionFor(GestureType.SWIPE_DOWN))
        assertEquals(3L, bindings.version)
    }

    @Test
    fun copiesInputMapAndRejectsInvalidActionIdsToNone() {
        val mutable = mutableMapOf(
            GestureBinding.side(EdgeSide.LEFT, GestureType.SWIPE_UP) to GestureAction.Back
        )
        val bindings = GestureBindingSnapshot(actions = mutable)
        mutable.clear()

        assertEquals(GestureAction.Back, bindings.actionFor(EdgeSide.LEFT, GestureType.SWIPE_UP))
        assertEquals(GestureAction.None, GestureAction.fromId("unknown-action"))
        assertEquals(GestureAction.None, GestureAction.fromId("launch_app:not a package"))
    }

    @Test
    fun terminalLedgerAcceptsOnlyOneCommitOrCancelPerGestureId() {
        val ledger = GestureEventLedger()
        val commit = GestureEvent.Commit(
            gestureId = 42L,
            snapshotVersion = 7L,
            activePointerId = 1,
            zoneId = 0,
            gesture = GestureType.SWIPE_UP,
            action = GestureAction.Home
        )
        val cancel = GestureEvent.Cancel(
            gestureId = 42L,
            snapshotVersion = 7L,
            activePointerId = 1,
            zoneId = 0
        )

        assertTrue(ledger.accept(commit))
        assertFalse(ledger.accept(cancel))
        assertFalse(ledger.accept(commit))
    }
}
