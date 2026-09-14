package com.paifa.univerge.heavydrag.android

import android.view.MotionEvent
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragSource
import com.paifa.univerge.heavydrag.core.HeavyRect
import com.paifa.univerge.heavydrag.core.HeavyTouchClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeavyDragHostViewTest {
    @Test
    fun interceptsOnlyRegisteredSourceAndForwardsDownExactlyOnce() {
        val gateway = CountingGateway()
        val coordinator = HeavyDragCoordinator().apply {
            registerSource(HeavyDragSource("source", HeavyRect(0f, 0f, 20f, 20f)))
        }
        val host = HeavyDragHostView(RuntimeEnvironment.getApplication()).apply {
            bind(HeavyDragRuntime(coordinator, gateway).also { it.start() })
            layout(0, 0, 100, 100)
        }

        val outside = event(MotionEvent.ACTION_DOWN, 50f, 50f)
        assertFalse(host.onInterceptTouchEvent(outside))
        val inside = event(MotionEvent.ACTION_DOWN, 10f, 10f)
        assertTrue(host.onInterceptTouchEvent(inside))
        assertTrue(host.onTouchEvent(inside))

        assertEquals(1, gateway.forwardedActions.count { it == MotionEvent.ACTION_DOWN })
    }

    @Test
    fun stopsInterceptingWhenPendingGestureIsCancelledByMovement() {
        val gateway = CountingGateway()
        val coordinator = HeavyDragCoordinator().apply {
            registerSource(HeavyDragSource("source", HeavyRect(0f, 0f, 20f, 20f)))
        }
        val runtime = HeavyDragRuntime(coordinator, gateway).also { it.start() }
        val host = HeavyDragHostView(RuntimeEnvironment.getApplication()).apply {
            bind(runtime)
            layout(0, 0, 100, 100)
        }

        assertTrue(host.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertTrue(host.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertFalse(host.onTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 10f)))

        assertFalse(host.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 10f)))
    }

    @Test
    fun stopsInterceptingImmediatelyWhenAdditionalPointerCancelsGesture() {
        val gateway = CountingGateway()
        val coordinator = HeavyDragCoordinator().apply {
            registerSource(HeavyDragSource("source", HeavyRect(0f, 0f, 20f, 20f)))
        }
        val host = HeavyDragHostView(RuntimeEnvironment.getApplication()).apply {
            bind(HeavyDragRuntime(coordinator, gateway).also { it.start() })
            layout(0, 0, 100, 100)
        }

        assertTrue(host.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertTrue(host.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertTrue(host.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 10f, 10f)))

        assertFalse(host.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 10f)))
    }

    @Test
    fun stopsInterceptingImmediatelyWhenASecondPointerCancelsGesture() {
        val gateway = CountingGateway()
        val coordinator = HeavyDragCoordinator().apply {
            registerSource(HeavyDragSource("source", HeavyRect(0f, 0f, 20f, 20f)))
        }
        val runtime = HeavyDragRuntime(coordinator, gateway).also { it.start() }
        val host = HeavyDragHostView(RuntimeEnvironment.getApplication()).apply {
            bind(runtime)
            layout(0, 0, 100, 100)
        }

        assertTrue(host.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertTrue(host.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertTrue(host.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 10f, 10f)))

        assertFalse(host.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 10f)))
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0L, 10L, action, x, y, 0)

    private class CountingGateway : HeavyTouchClassifierGateway {
        val forwardedActions = mutableListOf<Int>()
        override fun setListener(listener: HeavyTouchClassifierGateway.Listener?) = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
        override fun handleMotionEvent(event: MotionEvent, width: Int, height: Int, gestureId: Long): Boolean {
            forwardedActions += event.actionMasked
            return true
        }
    }
}
