package com.paifa.univerge.gesture.server

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GestureServerRuntimeTest {
    @Test
    fun acceptsOnlyIncreasingValidSnapshotsAndPersistsTheAcceptedOne() {
        val store = InMemoryGestureServerSnapshotStore()
        val runtime = GestureServerRuntime(store)
        val first = snapshot(1L)
        val second = snapshot(2L)

        assertTrue(runtime.apply(first).accepted)
        assertFalse(runtime.apply(first).accepted)
        assertFalse(runtime.apply(snapshot(0L)).accepted)
        assertTrue(runtime.apply(second).accepted)
        assertEquals(2L, store.read()?.version)
    }

    @Test
    fun restoresLastValidSnapshotBeforeBinderReconnect() {
        val store = InMemoryGestureServerSnapshotStore(snapshot(3L))
        val runtime = GestureServerRuntime(store)

        assertEquals(3L, runtime.start()?.version)
        assertEquals(3L, runtime.onBinderDisconnected()?.version)
        assertNotNull(runtime.snapshot)
    }

    @Test
    fun invalidRestoredSnapshotIsDiscarded() {
        val store = InMemoryGestureServerSnapshotStore(snapshot(4L).copy(density = 0f))
        val runtime = GestureServerRuntime(store)

        assertEquals(null, runtime.start())
        assertFalse(runtime.apply(snapshot(4L).copy(screenWidthDp = Float.NaN)).accepted)
    }

    @Test
    fun actionBindingsRoundTripAndRemainSideSpecific() {
        val source = snapshot(5L).copy(
            leftActions = mapOf(GestureType.PULL_INWARD_SHORT.id to GestureAction.Back.id),
            rightActions = mapOf(GestureType.PULL_INWARD_SHORT.id to GestureAction.Home.id),
            bottomActions = mapOf(GestureType.SWIPE_UP.id to GestureAction.Recents.id)
        )
        val restored = GestureServerSnapshot.fromBundle(source.toBundle())

        assertEquals(GestureAction.Back, restored?.actionFor(EdgeSide.LEFT, GestureType.PULL_INWARD_SHORT))
        assertEquals(GestureAction.Home, restored?.actionFor(EdgeSide.RIGHT, GestureType.PULL_INWARD_SHORT))
        assertEquals(GestureAction.Recents, restored?.bottomActionFor(GestureType.SWIPE_UP))
        assertEquals(GestureAction.None, restored?.actionFor(EdgeSide.LEFT, GestureType.SWIPE_DOWN))
    }

    @Test
    fun gestureThresholdsRoundTripWithTheServerSnapshot() {
        val source = snapshot(6L).copy(
            shortPullDistanceDp = 31f,
            longPullDistanceDp = 141f,
            holdDurationMs = 620L
        )
        val restored = GestureServerSnapshot.fromBundle(source.toBundle())

        assertEquals(31f, restored?.shortPullDistanceDp ?: 0f, 0.001f)
        assertEquals(141f, restored?.longPullDistanceDp ?: 0f, 0.001f)
        assertEquals(620L, restored?.holdDurationMs)
    }

    @Test
    fun snapshotListenersReceiveOnlyAcceptedSnapshotsAndCanBeRemoved() {
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val versions = mutableListOf<Long>()
        val registration = runtime.addSnapshotListener { versions += it.version }

        assertTrue(runtime.apply(snapshot(1L)).accepted)
        assertFalse(runtime.apply(snapshot(1L)).accepted)
        assertTrue(runtime.apply(snapshot(2L)).accepted)
        registration.close()
        assertTrue(runtime.apply(snapshot(3L)).accepted)

        assertEquals(listOf(1L, 2L), versions)
    }

    private fun snapshot(version: Long) = GestureServerSnapshot(
        version = version,
        density = 3f,
        screenWidthDp = 360f,
        screenHeightDp = 800f
    )
}
