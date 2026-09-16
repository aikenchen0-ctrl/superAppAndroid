package com.paifa.univerge.gesture.server

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class GestureServerOverlayControllerTest {
    @Test
    fun latestSnapshotIsAppliedAfterAnActiveGestureFinishes() {
        val windows = RecordingWindowManager()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> }
        )
        runtime.apply(snapshot(1L))
        controller.start(runtime)
        idleMain()

        val oldSide = windows.edgeViews().single()
        oldSide.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        oldSide.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 100f, 20L))
        runtime.apply(snapshot(2L))
        idleMain()

        oldSide.onTouchEvent(event(MotionEvent.ACTION_UP, 40f, 100f, 40L))
        idleMain()

        val newSide = windows.edgeViews().single()
        assertNotSame("deferred snapshot must be installed after UP", oldSide, newSide)
        controller.close()
    }

    @Test
    fun latestSnapshotIsAppliedAfterWindowFocusCancelsTheGesture() {
        val windows = RecordingWindowManager()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> }
        )
        runtime.apply(snapshot(1L))
        controller.start(runtime)
        idleMain()

        val oldSide = windows.edgeViews().single()
        oldSide.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        oldSide.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 100f, 20L))
        runtime.apply(snapshot(2L))
        idleMain()

        oldSide.onWindowFocusChanged(false)
        idleMain()

        assertNotSame(oldSide, windows.edgeViews().single())
        controller.close()
    }

    @Test
    fun sideCommitCarriesConfiguredZoneAndSnapshotIdentity() {
        val windows = RecordingWindowManager()
        val commits = mutableListOf<com.paifa.univerge.core.model.GestureData>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, data -> commits += data }
        )
        runtime.apply(snapshot(42L).copy(
            leftZones = listOf(GestureServerZone(7, true, 0f, 800f, 24f))
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 40f, 100f, 40L))

        assertEquals(1, commits.size)
        assertEquals(7, commits.single().zoneId)
        assertEquals(42L, commits.single().snapshotVersion)
        controller.close()
    }

    @Test
    fun slightRetractionAfterPreviewStillCommitsInTheServerSurface() {
        val windows = RecordingWindowManager()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, _ -> actions += action }
        )
        runtime.apply(snapshot(43L).copy(
            leftActions = mapOf(
                com.paifa.univerge.core.model.GestureType.PULL_INWARD_SHORT.id to
                    com.paifa.univerge.core.model.GestureAction.Back.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 28f, 100f, 40L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 28f, 100f, 60L))

        assertEquals(listOf(com.paifa.univerge.core.model.GestureAction.Back), actions)
        controller.close()
    }

    @Test
    fun sideThresholdUsesTheSameResponseRatioAtNonUnitDensity() {
        val windows = RecordingWindowManager()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, _ -> actions += action }
        )
        runtime.apply(snapshot(44L).copy(
            density = 2f,
            leftActions = mapOf(
                com.paifa.univerge.core.model.GestureType.PULL_INWARD_SHORT.id to
                    com.paifa.univerge.core.model.GestureAction.Back.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        // 46px = 23dp. The configured 32dp threshold uses the shared 0.70
        // response ratio, so this is intentionally just over 22.4dp.
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 46f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 46f, 100f, 40L))

        assertEquals(listOf(com.paifa.univerge.core.model.GestureAction.Back), actions)
        controller.close()
    }

    @Test
    fun sideThresholdSanitizationKeepsTheConfiguredLongGestureDistinct() {
        val windows = RecordingWindowManager()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, _ -> actions += action }
        )
        runtime.apply(snapshot(45L).copy(
            shortPullDistanceDp = 120f,
            longPullDistanceDp = 120f,
            leftActions = mapOf(
                com.paifa.univerge.core.model.GestureType.PULL_INWARD_SHORT.id to
                    com.paifa.univerge.core.model.GestureAction.Home.id,
                com.paifa.univerge.core.model.GestureType.PULL_INWARD_LONG.id to
                    com.paifa.univerge.core.model.GestureAction.Back.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        // 86dp is above the effective short threshold (84dp), but below the
        // sanitized long threshold (89.6dp).
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 86f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 86f, 100f, 40L))

        assertEquals(listOf(com.paifa.univerge.core.model.GestureAction.Home), actions)
        controller.close()
    }

    @Test
    fun sideCommitCoordinatesAreReportedInScreenPixelsAtNonUnitDensity() {
        val windows = RecordingWindowManager()
        val commits = mutableListOf<com.paifa.univerge.core.model.GestureData>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, data -> commits += data }
        )
        runtime.apply(snapshot(11L).copy(
            density = 2f,
            leftZones = listOf(GestureServerZone(7, true, 100f, 200f, 64f, edgeInsetDp = 5f))
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 80f, 100f, 40L))

        val data = commits.single()
        assertEquals(10f, data.startX, 0.001f)
        assertEquals(90f, data.endX, 0.001f)
        assertEquals(300f, data.startY, 0.001f)
        assertEquals(300f, data.endY, 0.001f)
        controller.close()
    }

    @Test
    fun rightSideCommitUsesTheConfiguredZoneIdentityAndInwardDirection() {
        val windows = RecordingWindowManager()
        val commits = mutableListOf<com.paifa.univerge.core.model.GestureData>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, data -> commits += data }
        )
        runtime.apply(snapshot(13L).copy(
            leftZones = emptyList(),
            rightZones = listOf(GestureServerZone(5, true, 0f, 800f, 24f, edgeInsetDp = 4f))
        ))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, side.width.toFloat(), 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, -20f, 100f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, -20f, 100f, 40L))

        assertEquals(1, commits.size)
        assertEquals(5, commits.single().zoneId)
        assertEquals(13L, commits.single().snapshotVersion)
        controller.close()
    }

    @Test
    fun multipleSideZonesKeepIndependentTerminalZoneIds() {
        val windows = RecordingWindowManager()
        val zoneIds = mutableListOf<Int>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, data -> zoneIds += data.zoneId }
        )
        runtime.apply(snapshot(15L).copy(
            leftZones = listOf(
                GestureServerZone(1, true, 0f, 300f, 24f),
                GestureServerZone(2, true, 400f, 300f, 24f)
            )
        ))
        controller.start(runtime)
        idleMain()

        windows.edgeViews().forEach { side ->
            side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
            side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 100f, 20L))
            side.onTouchEvent(event(MotionEvent.ACTION_UP, 80f, 100f, 40L))
        }

        assertEquals(listOf(1, 2), zoneIds)
        controller.close()
    }

    @Test
    fun sideCommitConvertsLocalDpCoordinatesToScreenPixelsBeforeApplyingWindowOffset() {
        val windows = RecordingWindowManager()
        val commits = mutableListOf<com.paifa.univerge.core.model.GestureData>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, data -> commits += data }
        )
        runtime.apply(
            snapshot(50L).copy(
                density = 2f,
                screenWidthDp = 400f,
                screenHeightDp = 800f,
                leftZones = listOf(GestureServerZone(9, true, 100f, 300f, 24f, edgeInsetDp = 4f))
            )
        )
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 20f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 20f, 20L))
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 80f, 20f, 40L))

        assertEquals(1, commits.size)
        val data = commits.single()
        // The view starts at 4dp = 8px and 100dp = 200px. Local event
        // coordinates are converted from px -> dp for recognition and back
        // to screen px at the process boundary.
        assertEquals(8f, data.startX, 0.001f)
        assertEquals(88f, data.endX, 0.001f)
        assertEquals(220f, data.startY, 0.001f)
        assertEquals(220f, data.endY, 0.001f)
        controller.close()
    }

    @Test
    fun bottomCommitCarriesTheSnapshotVersion() {
        val windows = RecordingWindowManager()
        val commits = mutableListOf<com.paifa.univerge.core.model.GestureData>()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, data ->
                actions += action
                commits += data
            }
        )
        runtime.apply(snapshot(23L).copy(
            bottomActions = mapOf(
                com.paifa.univerge.core.model.GestureType.SWIPE_UP.id to
                    com.paifa.univerge.core.model.GestureAction.Home.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val bottom = windows.bottomViews().single()
        val x = bottom.width / 2f
        bottom.onTouchEvent(event(MotionEvent.ACTION_DOWN, x, bottom.height - 2f, 0L))
        bottom.onTouchEvent(event(MotionEvent.ACTION_MOVE, x, -60f, 20L))
        assertEquals("directional bottom action commits before release", listOf(com.paifa.univerge.core.model.GestureAction.Home), actions)
        bottom.onTouchEvent(event(MotionEvent.ACTION_UP, x, -60f, 40L))

        assertEquals(1, commits.size)
        assertEquals(listOf(com.paifa.univerge.core.model.GestureAction.Home), actions)
        assertEquals(23L, commits.single().snapshotVersion)
        controller.close()
    }

    @Test
    fun lostBottomUpIsRecoveredByTheTerminalWatchdog() {
        val windows = RecordingWindowManager()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> }
        )
        runtime.apply(snapshot(60L).copy(
            bottomActions = mapOf(
                com.paifa.univerge.core.model.GestureType.SWIPE_UP.id to
                    com.paifa.univerge.core.model.GestureAction.Home.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val oldBottom = windows.bottomViews().single()
        val x = oldBottom.width / 2f
        oldBottom.onTouchEvent(event(MotionEvent.ACTION_DOWN, x, oldBottom.height - 2f, 0L))
        oldBottom.onTouchEvent(event(MotionEvent.ACTION_MOVE, x, -60f, 20L))

        runtime.apply(snapshot(61L).copy(
            bottomActions = mapOf(
                com.paifa.univerge.core.model.GestureType.SWIPE_UP.id to
                    com.paifa.univerge.core.model.GestureAction.Home.id
            )
        ))
        idleMain()
        assertEquals("the committed touch keeps the old surface until it is finished", oldBottom, windows.bottomViews().single())

        // The previous UP is intentionally missing. The watchdog must abandon
        // the committed transaction so the pending snapshot can be installed.
        shadowOf(Looper.getMainLooper()).idleFor(2L, TimeUnit.SECONDS)

        assertTrue(windows.bottomViews().single() !== oldBottom)
        controller.close()
    }

    @Test
    fun bottomSwipeUsesTheSamePhysicalThresholdAtNonUnitDensity() {
        val windows = RecordingWindowManager()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, _ -> actions += action }
        )
        runtime.apply(snapshot(24L).copy(
            density = 2f,
            bottomActions = mapOf(
                com.paifa.univerge.core.model.GestureType.SWIPE_UP.id to
                    com.paifa.univerge.core.model.GestureAction.Home.id
            )
        ))
        controller.start(runtime)
        idleMain()

        val bottom = windows.bottomViews().single()
        val x = bottom.width / 2f
        bottom.onTouchEvent(event(MotionEvent.ACTION_DOWN, x, bottom.height - 2f, 0L))
        bottom.onTouchEvent(event(MotionEvent.ACTION_MOVE, x, -1f, 20L))
        bottom.onTouchEvent(event(MotionEvent.ACTION_UP, x, -1f, 40L))

        assertEquals(listOf(com.paifa.univerge.core.model.GestureAction.Home), actions)
        controller.close()
    }

    @Test
    fun failedWindowInstallationIsAtomicAndReportsNoInputSurface() {
        val windows = RecordingWindowManager(failAdds = true)
        val availability = mutableListOf<Boolean>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceChanged = { availability += it }
        )
        runtime.apply(snapshot(1L))
        controller.start(runtime)
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertEquals(false, controller.hasInputSurface)
        assertEquals(listOf(false), availability)
        controller.close()
    }

    @Test
    fun inputOwnershipIsPreparedBeforeTheFirstServerWindowIsAdded() {
        val order = mutableListOf<String>()
        val windows = RecordingWindowManager(onAdd = { order += "add" })
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceWillChange = { available ->
                if (available) order += "prepare"
            }
        )
        runtime.apply(snapshot(7L))
        controller.start(runtime)
        idleMain()

        assertTrue(order.indexOf("prepare") >= 0)
        assertTrue(order.indexOf("add") > order.indexOf("prepare"))
        controller.close()
    }

    @Test
    fun partialWindowInstallationRollsBackAlreadyAddedRegions() {
        val windows = RecordingWindowManager(failAtAdd = 2)
        val availability = mutableListOf<Boolean>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceChanged = { availability += it }
        )
        runtime.apply(snapshot(1L).copy(
            rightZones = listOf(GestureServerZone(2, true, 0f, 800f, 24f))
        ))
        controller.start(runtime)
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertEquals(listOf(false), availability)
        controller.close()
    }

    @Test
    fun addViewThatThrowsAfterAttachingIsAlsoRemoved() {
        val windows = RecordingWindowManager(failAfterAddAt = 1)
        val availability = mutableListOf<Boolean>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceChanged = { availability += it }
        )
        runtime.apply(snapshot(1L))
        controller.start(runtime)
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertEquals(listOf(false), availability)
        controller.close()
    }

    @Test
    fun floatingChatOwnedSurfaceSuspendsServerInput() {
        val windows = RecordingWindowManager()
        val availability = mutableListOf<Boolean>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceChanged = { availability += it }
        )
        runtime.apply(snapshot(1L).copy(floatingChatOwnsSurface = true))
        controller.start(runtime)
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertEquals(false, controller.hasInputSurface)
        assertEquals(listOf(false), availability)
        controller.close()
    }

    @Test
    fun runtimeDisabledSnapshotSuspendsServerInputWithoutChangingConfiguration() {
        val windows = RecordingWindowManager()
        val availability = mutableListOf<Boolean>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> },
            onInputSurfaceChanged = { availability += it }
        )
        runtime.apply(snapshot(1L).copy(inputEnabled = true))
        controller.start(runtime)
        idleMain()
        assertTrue(controller.hasInputSurface)

        runtime.apply(snapshot(2L).copy(inputEnabled = false))
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertEquals(false, controller.hasInputSurface)
        assertEquals(listOf(true, false), availability)
        controller.close()
    }

    @Test
    fun runtimeDisabledSnapshotCancelsAnActiveGestureImmediately() {
        val windows = RecordingWindowManager()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { _, _ -> }
        )
        runtime.apply(snapshot(1L).copy(inputEnabled = true))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 100f, 20L))
        assertTrue(controller.hasActiveGesture)

        runtime.apply(snapshot(2L).copy(inputEnabled = false))
        idleMain()

        assertTrue(windows.added.isEmpty())
        assertTrue(!controller.hasActiveGesture)
        controller.close()
    }

    @Test
    fun disablingRuntimeInputCancelsAnInFlightGestureBeforeItsUp() {
        val windows = RecordingWindowManager()
        val actions = mutableListOf<com.paifa.univerge.core.model.GestureAction>()
        val runtime = GestureServerRuntime(InMemoryGestureServerSnapshotStore())
        val controller = GestureServerOverlayController(
            service = service(),
            windowManager = windows.proxy,
            onAction = { action, _ -> actions += action }
        )
        runtime.apply(snapshot(1L).copy(inputEnabled = true))
        controller.start(runtime)
        idleMain()

        val side = windows.edgeViews().single()
        side.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 100f, 0L))
        side.onTouchEvent(event(MotionEvent.ACTION_MOVE, 80f, 100f, 20L))
        runtime.apply(snapshot(2L).copy(inputEnabled = false))
        idleMain()
        side.onTouchEvent(event(MotionEvent.ACTION_UP, 80f, 100f, 40L))

        assertTrue(actions.isEmpty())
        assertTrue(windows.added.isEmpty())
        controller.close()
    }

    @Test
    fun floatingChatOwnershipSurvivesTheProcessBoundary() {
        val source = snapshot(9L).copy(floatingChatOwnsSurface = true)

        val restored = GestureServerSnapshot.fromBundle(source.toBundle())

        assertEquals(true, restored?.floatingChatOwnsSurface)
    }

    private fun snapshot(version: Long) = GestureServerSnapshot(
        version = version,
        density = 1f,
        screenWidthDp = 400f,
        screenHeightDp = 800f,
        leftZones = listOf(GestureServerZone(0, true, 0f, 800f, 24f)),
        rightZones = emptyList(),
        bottomWidthDp = 156f,
        bottomHeightDp = 30f
    )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun service(): TestAccessibilityService =
        Robolectric.buildService(TestAccessibilityService::class.java).create().get()

    private fun event(action: Int, x: Float, y: Float, time: Long): MotionEvent =
        MotionEvent.obtain(0L, time, action, x, y, 0)

    private class TestAccessibilityService : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }

    private class RecordingWindowManager(
        private val failAdds: Boolean = false,
        private val failAtAdd: Int? = null,
        private val failAfterAddAt: Int? = null,
        private val onAdd: (() -> Unit)? = null
    ) {
        val added = mutableListOf<View>()
        private var addCount = 0
        val proxy: WindowManager = Proxy.newProxyInstance(
            WindowManager::class.java.classLoader,
            arrayOf(WindowManager::class.java)
        ) { _, method, args ->
            when (method.name) {
                "addView" -> {
                    addCount += 1
                    onAdd?.invoke()
                    if (failAdds || addCount == failAtAdd) error("simulated add failure")
                    val view = args!![0] as View
                    val params = args[1] as WindowManager.LayoutParams
                    view.layout(0, 0, params.width, params.height)
                    added += view
                    if (addCount == failAfterAddAt) error("simulated post-attach failure")
                    null
                }
                "removeView", "removeViewImmediate" -> {
                    added.remove(args!![0] as View)
                    null
                }
                else -> defaultValue(method.returnType)
            }
        } as WindowManager

        fun edgeViews(): List<View> = added.filter { it.javaClass.simpleName.contains("EdgeGestureView") }
        fun bottomViews(): List<View> = added.filter { it.javaClass.simpleName.contains("BottomGestureView") }

        private fun defaultValue(type: Class<*>): Any? = when {
            type == Boolean::class.javaPrimitiveType -> false
            type == Int::class.javaPrimitiveType -> 0
            type == Long::class.javaPrimitiveType -> 0L
            type == Float::class.javaPrimitiveType -> 0f
            type == Double::class.javaPrimitiveType -> 0.0
            type == Short::class.javaPrimitiveType -> 0.toShort()
            type == Byte::class.javaPrimitiveType -> 0.toByte()
            type == Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }
    }
}
