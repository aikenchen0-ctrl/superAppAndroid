package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GestureServerActionExecutorDispatchTest {
    @Test
    fun terminalServerActionIsQueuedBeforeThePlatformCall() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { command -> queued += command }
        )

        assertEquals(true, executor.execute(GestureAction.Back, GestureData(gestureId = 1L)))
        assertEquals("server action must leave the input callback before execution", 1, queued.size)
        executor.close()
    }

    @Test
    fun appLaunchAndSystemActionsShareSubmissionOrder() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { command -> queued += command },
            launchDispatchExecutor = Executor { error("launch actions must use the shared terminal lane") }
        )

        assertEquals(true, executor.execute(
            com.paifa.univerge.core.model.GestureAction.LaunchApp("com.example.target"),
            GestureData(gestureId = 1L)
        ))
        assertEquals(true, executor.execute(
            com.paifa.univerge.core.model.GestureAction.Home,
            GestureData(gestureId = 2L)
        ))

        assertEquals(2, queued.size)
        executor.close()
    }

    @Test
    fun rejectedDispatchDoesNotPermanentlyConsumeGestureId() {
        var reject = true
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { if (reject) throw RejectedExecutionException() }
        )
        val data = GestureData(gestureId = 77L)

        assertEquals(false, executor.execute(GestureAction.Home, data))
        reject = false
        assertEquals(true, executor.execute(GestureAction.Home, data))
        executor.close()
    }

    @Test
    fun failedPlatformActionReleasesGestureIdForRetry() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { command -> queued += command },
            performGlobalAction = { false }
        )
        val data = GestureData(gestureId = 88L)

        assertTrue(executor.execute(GestureAction.Back, data))
        queued.removeAt(0).run()

        assertTrue("a failed platform call must not poison the gesture ledger", executor.execute(GestureAction.Back, data))
        executor.close()
    }

    @Test
    fun rejectedForwardedActionReleasesGestureIdForRetry() {
        val queued = mutableListOf<Runnable>()
        var resultCallback: ((Boolean) -> Unit)? = null
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { command -> queued += command },
            dispatchMainProcessAction = { intent, onResult ->
                assertEquals(ACTION_MAIN_PROCESS_GESTURE, intent.action)
                resultCallback = onResult
                true
            }
        )
        val data = GestureData(gestureId = 99L)

        assertTrue(executor.execute(GestureAction.PlayVideo, data))
        queued.removeAt(0).run()
        assertNotNull(resultCallback)
        resultCallback!!.invoke(false)

        assertTrue("a rejected cross-process action must not poison the gesture ledger", executor.execute(GestureAction.PlayVideo, data))
        executor.close()
    }

    @Test
    fun acceptedForwardedActionStillUsesTerminalDeduplication() {
        val queued = mutableListOf<Runnable>()
        var resultCallback: ((Boolean) -> Unit)? = null
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = GestureServerActionExecutor(
            service = service,
            dispatchExecutor = Executor { command -> queued += command },
            dispatchMainProcessAction = { _, onResult ->
                resultCallback = onResult
                true
            }
        )
        val data = GestureData(gestureId = 100L)

        assertTrue(executor.execute(GestureAction.PlayVideo, data))
        queued.removeAt(0).run()
        assertNotNull(resultCallback)
        resultCallback!!.invoke(true)

        assertEquals(false, executor.execute(GestureAction.PlayVideo, data))
        executor.close()
    }

    private class TestAccessibilityService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
}
