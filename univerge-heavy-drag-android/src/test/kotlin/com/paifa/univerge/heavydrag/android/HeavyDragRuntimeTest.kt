package com.paifa.univerge.heavydrag.android

import android.view.MotionEvent
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragPolicy
import com.paifa.univerge.heavydrag.core.HeavyDragSource
import com.paifa.univerge.heavydrag.core.HeavyPoint
import com.paifa.univerge.heavydrag.core.HeavyRect
import com.paifa.univerge.heavydrag.core.HeavyTouchClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeavyDragRuntimeTest {
    @Test
    fun forwardsOnlyCapturedGestureAndLightReleaseClicksOnce() {
        val log = mutableListOf<String>()
        val gateway = FakeGateway()
        val coordinator = coordinator(log)
        val runtime = HeavyDragRuntime(coordinator, gateway) { 50L }
        runtime.start()

        assertFalse(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 40f, 40f), 100, 100))
        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100))
        val gestureId = gateway.events.single().gestureId
        gateway.emit(HeavyTouchClassification.lightPress(gestureId, 0.99f, 10L))
        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_UP, 5f, 5f), 100, 100))

        assertEquals(listOf("click"), log)
        assertEquals(2, gateway.events.size)
    }

    @Test
    fun matchingHeavyResultUnlocksMoveAndStaleResultDoesNot() {
        val log = mutableListOf<String>()
        val gateway = FakeGateway()
        val runtime = HeavyDragRuntime(
            coordinator(log, HeavyDragPolicy(minHeavyConfidence = 0.55f)),
            gateway
        ) { 20L }
        runtime.start()

        runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100)
        val gestureId = gateway.events.single().gestureId
        gateway.emit(HeavyTouchClassification.heavyPress(gestureId + 1, 1f, 5L))
        assertTrue(log.isEmpty())
        gateway.emit(HeavyTouchClassification.heavyPress(gestureId, 0.9f, 10L))
        runtime.handleMotionEvent(event(MotionEvent.ACTION_MOVE, 8f, 5f), 100, 100)
        runtime.handleMotionEvent(event(MotionEvent.ACTION_UP, 8f, 5f), 100, 100)

        assertEquals(listOf("start", "move", "end"), log)
    }

    @Test
    fun pendingGestureReleasesEventOwnershipAfterMovementCancelsClassification() {
        val gateway = FakeGateway()
        val coordinator = coordinator(mutableListOf())
        val runtime = HeavyDragRuntime(coordinator, gateway) { 20L }
        runtime.start()

        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100))
        assertFalse(runtime.handleMotionEvent(event(MotionEvent.ACTION_MOVE, 40f, 5f), 100, 100))
        assertFalse(runtime.handleMotionEvent(event(MotionEvent.ACTION_UP, 40f, 5f), 100, 100))
        assertEquals(null, coordinator.activeSession)
        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
            gateway.events.map { it.action }
        )
        assertEquals(1L, gateway.events.last().gestureId)
    }

    @Test
    fun movementBeyondPendingThresholdDoesNotForwardTheCancellingMoveToClassifier() {
        val gateway = FakeGateway()
        val runtime = HeavyDragRuntime(coordinator(mutableListOf()), gateway) { 20L }
        runtime.start()

        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100))
        assertFalse(runtime.handleMotionEvent(event(MotionEvent.ACTION_MOVE, 40f, 5f), 100, 100))

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
            gateway.events.map { it.action }
        )
    }

    @Test
    fun pointerDownCancelsClassifierExactlyOnce() {
        val gateway = FakeGateway()
        val runtime = HeavyDragRuntime(coordinator(mutableListOf()), gateway) { 20L }
        runtime.start()

        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100))
        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_POINTER_DOWN, 5f, 5f), 100, 100))
        assertFalse(runtime.handleMotionEvent(event(MotionEvent.ACTION_POINTER_UP, 5f, 5f), 100, 100))

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
            gateway.events.map { it.action }
        )
    }

    @Test
    fun windowFocusLossCancelsClassifierExactlyOnce() {
        val gateway = FakeGateway()
        val runtime = HeavyDragRuntime(coordinator(mutableListOf()), gateway) { 20L }
        runtime.start()

        assertTrue(runtime.handleMotionEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f), 100, 100))
        runtime.onWindowLostFocus()
        runtime.onWindowLostFocus()

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
            gateway.events.map { it.action }
        )
    }

    private fun coordinator(
        log: MutableList<String>,
        policy: HeavyDragPolicy = HeavyDragPolicy()
    ) = HeavyDragCoordinator(policy).also { coordinator ->
        coordinator.registerSource(
            HeavyDragSource(
                id = "source",
                bounds = HeavyRect(0f, 0f, 10f, 10f),
                onClick = { log += "click" },
                onDragStart = { log += "start" },
                onDrag = { log += "move" },
                onDragEnd = { log += "end" }
            )
        )
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(0L, 10L, action, x, y, 0)
    }

    private class FakeGateway : HeavyTouchClassifierGateway {
        data class Event(val action: Int, val gestureId: Long)

        val events = mutableListOf<Event>()
        private var listener: HeavyTouchClassifierGateway.Listener? = null

        override fun setListener(listener: HeavyTouchClassifierGateway.Listener?) {
            this.listener = listener
        }

        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit

        override fun handleMotionEvent(
            event: MotionEvent,
            width: Int,
            height: Int,
            gestureId: Long
        ): Boolean {
            events += Event(event.actionMasked, gestureId)
            return true
        }

        fun emit(result: HeavyTouchClassification) {
            listener?.onClassification(result)
        }
    }
}
