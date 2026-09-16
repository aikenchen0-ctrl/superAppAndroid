package com.paifa.univerge.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UniVergeActionExecutorDispatchTest {
    @Test
    fun systemActionIsQueuedBeforeThePlatformCall() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command }
        )

        executor.execute(GestureAction.Home, GestureData(gestureId = 1L))

        assertEquals("system action must leave the input callback before execution", 1, queued.size)
        executor.close()
    }

    @Test
    fun completionRunsAfterTheQueuedSystemAction() {
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command }
        )

        executor.execute(
            GestureAction.Home,
            GestureData(gestureId = 3L),
            onDispatched = { events += "completed" }
        )

        assertEquals(emptyList<String>(), events)
        queued.single().run()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(listOf("completed"), events)
        executor.close()
    }

    @Test
    fun hapticFailureDoesNotEscapeTheQueuedAction() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { error("haptic state unavailable") },
            dispatchExecutor = Executor { command -> queued += command }
        )

        executor.execute(GestureAction.Home, GestureData(gestureId = 4L))

        queued.single().run()
        executor.close()
    }

    @Test
    fun failedPlatformGlobalActionReportsRejectionInsteadOfCompletion() {
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command },
            performGlobalAction = { false }
        )

        executor.execute(
            GestureAction.Home,
            GestureData(gestureId = 41L),
            onDispatched = { events += "dispatched" },
            onRejected = { events += "rejected" }
        )

        queued.single().run()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(listOf("rejected"), events)
        executor.close()
    }

    @Test
    fun closeCancelsQueuedWorkAtTheServiceLifecycleBoundary() {
        val queued = mutableListOf<Runnable>()
        val completed = mutableListOf<Boolean>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command }
        )

        executor.execute(
            GestureAction.Home,
            GestureData(gestureId = 5L),
            onDispatched = { completed += true }
        )
        executor.close()
        queued.single().run()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(emptyList<Boolean>(), completed)
    }

    @Test
    fun backOverrideIsResolvedAfterItLeavesTheInputCallback() {
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command },
            backOverrideAvailable = { true },
            dismissBackOverride = {
                events += "dismiss"
                true
            }
        )

        executor.execute(GestureAction.Back, GestureData(gestureId = 6L))

        assertEquals(emptyList<String>(), events)
        queued.single().run()
        assertEquals(emptyList<String>(), events)
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(listOf("dismiss"), events)
        executor.close()
    }

    @Test
    fun backWithoutOverrideRunsOnTheMainResolutionHopWithoutASecondQueueRoundTrip() {
        val queued = mutableListOf<Runnable>()
        val platformCalls = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command },
            backOverrideAvailable = { false },
            dismissBackOverride = { error("dismiss must not run without an override") },
            performGlobalAction = {
                platformCalls += Thread.currentThread().name
                true
            }
        )

        executor.execute(GestureAction.Back, GestureData(gestureId = 7L))

        queued.single().run()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals("system back should not re-enter the worker lane", 1, queued.size)
        assertEquals(1, platformCalls.size)
        executor.close()
    }

    @Test
    fun rejectedBackFallbackReportsRejectionInsteadOfSuccessfulDispatch() {
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command },
            backOverrideAvailable = { false },
            dismissBackOverride = { error("dismiss must not run without an override") },
            performGlobalAction = { false }
        )

        executor.execute(
            GestureAction.Back,
            GestureData(gestureId = 8L),
            onDispatched = { events += "dispatched" },
            onRejected = { events += "rejected" }
        )

        queued.single().run()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(listOf("rejected"), events)
        executor.close()
    }

    @Test
    fun appLaunchAndSystemActionsShareSubmissionOrder() {
        val queued = mutableListOf<Runnable>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            dispatchExecutor = Executor { command -> queued += command },
            launchDispatchExecutor = Executor { error("launch actions must use the shared terminal lane") }
        )

        executor.execute(GestureAction.LaunchApp("com.example.target"), GestureData(gestureId = 1L))
        executor.execute(GestureAction.Home, GestureData(gestureId = 2L))

        assertEquals("all terminal system actions must preserve submission order", 2, queued.size)
        executor.close()
    }

    @Test
    fun localUiActionLeavesTheInputCallbackBeforeCompletion() {
        val localQueued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false },
            localDispatchExecutor = Executor { command -> localQueued += command }
        )

        executor.execute(
            GestureAction.PlayVideo,
            GestureData(gestureId = 9L),
            onDispatched = { events += "completed" }
        )

        assertEquals("local UI action must leave the input callback first", 1, localQueued.size)
        assertEquals(emptyList<String>(), events)
        localQueued.single().run()
        assertEquals(listOf("completed"), events)
        executor.close()
    }

    @Test
    fun closeRejectsNoOpCompletionInsteadOfReportingSuccess() {
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        val executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = { false }
        )

        executor.close()
        val accepted = executor.execute(
            GestureAction.None,
            GestureData(gestureId = 10L),
            onDispatched = { events += "dispatched" },
            onRejected = { events += "rejected" }
        )

        assertEquals(false, accepted)
        assertEquals(listOf("rejected"), events)
    }

    @Test
    fun localActionClosedDuringPreparationReportsRejection() {
        val events = mutableListOf<String>()
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        lateinit var executor: UniVergeActionExecutor
        executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = {
                executor.close()
                false
            },
            localDispatchExecutor = Executor { command -> command.run() }
        )

        val accepted = executor.execute(
            GestureAction.PlayVideo,
            GestureData(gestureId = 11L),
            onDispatched = { events += "dispatched" },
            onRejected = { events += "rejected" }
        )

        assertEquals(true, accepted)
        assertEquals(listOf("rejected"), events)
    }

    @Test
    fun systemActionClosedDuringPreparationDoesNotReachPlatformCall() {
        val queued = mutableListOf<Runnable>()
        val events = mutableListOf<String>()
        var platformCalls = 0
        lateinit var executor: UniVergeActionExecutor
        val service = Robolectric.buildService(TestAccessibilityService::class.java)
            .create()
            .get()
        executor = UniVergeActionExecutor(
            service = service,
            isHapticFeedbackEnabled = {
                executor.close()
                false
            },
            dispatchExecutor = Executor { command -> queued += command },
            performGlobalAction = {
                platformCalls += 1
                true
            }
        )

        val accepted = executor.execute(
            GestureAction.Home,
            GestureData(gestureId = 12L),
            onDispatched = { events += "dispatched" },
            onRejected = { events += "rejected" }
        )

        assertEquals(true, accepted)
        queued.single().run()

        assertEquals("closing during preparation must reject before the platform call", 0, platformCalls)
        assertEquals(listOf("rejected"), events)
    }

    private class TestAccessibilityService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
}
