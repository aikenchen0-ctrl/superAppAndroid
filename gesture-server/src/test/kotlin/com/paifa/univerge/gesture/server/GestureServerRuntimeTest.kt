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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.collections.AbstractMap
import kotlin.collections.AbstractSet

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

    @Test
    fun concurrentAppliesNeverNotifyAnOlderVersionAfterANewerVersion() {
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val versions = Collections.synchronizedList(mutableListOf<Long>())
        runtime.addSnapshotListener { versions += it.version }
        val ready = CountDownLatch(1)
        val workers = (0 until 8).map { worker ->
            thread(start = true, name = "runtime-race-$worker") {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                repeat(2_000) {
                    runtime.apply(snapshot(if (worker % 2 == 0) 2L else 1L))
                }
            }
        }

        ready.countDown()
        workers.forEach { it.join(5_000) }

        val decreases = versions.zipWithNext().filter { (previous, current) -> current < previous }
        assertTrue("accepted snapshot versions must be monotonic, got $decreases", decreases.isEmpty())
    }

    @Test
    fun anOlderApplyCannotOverwriteANewerApplyThatPassedTheVersionCheck() {
        val bothCopiesReady = CountDownLatch(2)
        val highCopyFinished = CountDownLatch(1)
        val releaseLowCopy = CountDownLatch(1)
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val high = snapshot(2L).copy(
            bottomActions = HookedActionMap {
                bothCopiesReady.countDown()
                assertTrue(bothCopiesReady.await(5, TimeUnit.SECONDS))
                highCopyFinished.countDown()
            }
        )
        val low = snapshot(1L).copy(
            bottomActions = HookedActionMap {
                bothCopiesReady.countDown()
                assertTrue(bothCopiesReady.await(5, TimeUnit.SECONDS))
                assertTrue(highCopyFinished.await(5, TimeUnit.SECONDS))
                assertTrue(releaseLowCopy.await(5, TimeUnit.SECONDS))
            }
        )
        val highThread = thread(start = true) { assertTrue(runtime.apply(high).accepted) }
        val lowThread = thread(start = true) { assertFalse(runtime.apply(low).accepted) }

        waitUntil { runtime.snapshot?.version == 2L }
        releaseLowCopy.countDown()
        highThread.join(5_000)
        lowThread.join(5_000)

        assertEquals("the late low version must be rejected", 2L, runtime.snapshot?.version)
    }

    @Test
    fun concurrentStartReadsTheStoreOnlyOnce() {
        val firstReadStarted = CountDownLatch(1)
        val allowFirstRead = CountDownLatch(1)
        val store = object : GestureServerSnapshotStore {
            var reads = 0

            override fun read(): GestureServerSnapshot? {
                reads += 1
                val readNumber = reads
                if (readNumber == 1) {
                    firstReadStarted.countDown()
                    assertTrue(allowFirstRead.await(5, TimeUnit.SECONDS))
                }
                return snapshot(readNumber.toLong())
            }

            override fun write(snapshot: GestureServerSnapshot) = Unit
        }
        val runtime = GestureServerRuntime(store)
        val first = thread(start = true) { runtime.start() }
        assertTrue(firstReadStarted.await(5, TimeUnit.SECONDS))
        val secondStarted = CountDownLatch(1)
        val second = thread(start = true) {
            secondStarted.countDown()
            runtime.start()
        }
        assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
        allowFirstRead.countDown()
        first.join(5_000)
        second.join(5_000)

        assertEquals("start must perform one initialization read", 1, store.reads)
        assertEquals(1L, runtime.snapshot?.version)
    }

    private fun snapshot(version: Long) = GestureServerSnapshot(
        version = version,
        density = 3f,
        screenWidthDp = 360f,
        screenHeightDp = 800f
    )

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertTrue("condition was not reached before timeout", condition())
    }

    private class HookedActionMap(
        private val onCopy: () -> Unit
    ) : AbstractMap<String, String>() {
        private val iterations = AtomicInteger(0)
        private val entry = java.util.AbstractMap.SimpleImmutableEntry(
            GestureType.SWIPE_UP.id,
            GestureAction.Back.id
        )

        override val entries: Set<Map.Entry<String, String>> = object : AbstractSet<Map.Entry<String, String>>() {
            override val size: Int = 1

            override fun iterator(): Iterator<Map.Entry<String, String>> {
                val iteration = iterations.incrementAndGet()
                if (iteration == 2) onCopy()
                return listOf(entry).iterator()
            }
        }
    }
}
