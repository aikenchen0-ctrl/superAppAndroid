package com.paifa.univerge.overlay

import android.view.MotionEvent
import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureData
import com.paifa.univerge.core.model.GestureType
import com.paifa.univerge.core.gesture.runtime.HotZoneSegment
import com.paifa.univerge.core.gesture.runtime.SideGestureRecognizer
import com.paifa.univerge.core.gesture.runtime.GestureSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EdgeGestureDetectorTest {
    @Test
    fun moveReusesPreviewDataAcrossTheGesture() {
        val progress = mutableListOf<GestureData>()
        val detector = detector(onGestureProgress = progress::add)

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 30f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 42f, y = 200f, time = 40L))

        assertEquals(2, progress.size)
        assertSame(progress[0], progress[1])
        assertEquals(42f, progress.last().endX)
    }

    @Test
    fun previewDispatcherCoalescesMovesUntilAFrame() {
        val progress = mutableListOf<GestureData>()
        val dispatcher = RecordingPreviewDispatcher()
        val detector = EdgeGestureDetector(
            side = EdgeSide.LEFT,
            minSwipeDistancePx = 20f,
            longSwipeDistancePx = 60f,
            onGesture = { _, _ -> },
            onGestureProgress = progress::add,
            previewFrameDispatcher = dispatcher,
            viewportHeightPx = { 800f }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 30f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 44f, y = 200f, time = 40L))

        assertTrue(progress.isEmpty())
        dispatcher.flush()
        assertEquals(1, progress.size)
        assertEquals(44f, progress.single().endX)
    }

    @Test
    fun actionBindingIsCapturedBeforeTheGestureAndUsedAtUp() {
        val commits = mutableListOf<GestureAction>()
        val detector = EdgeGestureDetector(
            side = EdgeSide.LEFT,
            minSwipeDistancePx = 20f,
            longSwipeDistancePx = 60f,
            onGesture = { _, _ -> error("legacy callback must not be used by this adapter") },
            actionBindings = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back),
            onGestureWithAction = { _, action, _ -> commits += action },
            viewportHeightPx = { 800f }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))

        assertEquals(listOf(GestureAction.Back), commits)
    }

    @Test
    fun terminalActionIsDeliveredBeforePreviewCleanupAtUp() {
        val events = mutableListOf<String>()
        val detector = EdgeGestureDetector(
            side = EdgeSide.LEFT,
            minSwipeDistancePx = 20f,
            longSwipeDistancePx = 60f,
            onGesture = { _, _ -> events += "legacy-action" },
            onGestureEnd = { events += "preview-end" },
            actionBindings = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back),
            onGestureWithAction = { _, _, _ -> events += "action" },
            viewportHeightPx = { 800f }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))

        assertEquals(listOf("action", "preview-end"), events)
    }

    @Test
    fun backTerminalActionIsDeliveredBeforeBackAndPreviewCleanupAtUp() {
        val events = mutableListOf<String>()
        val detector = EdgeGestureDetector(
            side = EdgeSide.LEFT,
            minSwipeDistancePx = 20f,
            longSwipeDistancePx = 60f,
            onGesture = { _, _ -> events += "legacy-action" },
            onGestureEnd = { events += "preview-end" },
            onBackGestureEnd = { events += "back-end" },
            onBackGestureCommit = { _, _ -> events += "back-action"; true },
            actionBindings = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back),
            viewportHeightPx = { 800f }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))

        assertEquals(listOf("back-action", "back-end", "preview-end"), events)
    }

    @Test
    fun slightRetractionAfterPreviewStillCommitsThroughTheOverlayAdapter() {
        val commits = mutableListOf<GestureType>()
        val detector = detector(onGesture = { type, _ -> commits += type })

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 28f, y = 200f, time = 40L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 28f, y = 200f, time = 60L))

        assertEquals(listOf(GestureType.PULL_INWARD_SHORT), commits)
    }

    @Test
    fun holdTimerOnlyArmsCandidateUntilUp() {
        val commits = mutableListOf<GestureType>()
        val detector = detector(onGesture = { type, _ -> commits += type })

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 30f, y = 200f, time = 100L))
        shadowOf(android.os.Looper.getMainLooper()).runToEndOfTasks()

        assertTrue("timer must not commit before UP", commits.isEmpty())
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 30f, y = 200f, time = 700L))

        assertEquals(listOf(GestureType.PULL_INWARD_HOLD), commits)
    }

    @Test
    fun pointerStreamIsCancelledBeforeItCanCommit() {
        val commits = mutableListOf<GestureType>()
        val cancels = mutableListOf<Unit>()
        val detector = detector(
            onGesture = { type, _ -> commits += type },
            onBackGestureCancel = { cancels += Unit }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(
            event(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),
                x = 30f,
                y = 200f,
                time = 20L
            )
        )
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 30f, y = 200f, time = 40L))

        assertTrue(commits.isEmpty())
        assertEquals(1, cancels.size)
    }

    @Test
    fun duplicateUpCannotCommitTheSameGestureTwice() {
        val commits = mutableListOf<GestureType>()
        val detector = detector(onGesture = { type, _ -> commits += type })

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))
        detector.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 50L))

        assertEquals(1, commits.size)
    }

    @Test
    fun overlayFocusLossCancelsTheActiveGesture() {
        val commits = mutableListOf<GestureType>()
        val view = EdgeOverlayView(
            context = RuntimeEnvironment.getApplication(),
            side = EdgeSide.LEFT,
            visibleThicknessDp = 24,
            swipeThresholdDp = 20,
            onGesture = { type, _ -> commits += type }
        )
        view.layout(0, 0, 48, 800)
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        view.onWindowFocusChanged(false)
        view.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))

        assertTrue(commits.isEmpty())
    }

    @Test
    fun overlayCanInjectCoreRecognizerAndReceiveItsCommit() {
        val commits = mutableListOf<GestureAction>()
        val view = EdgeOverlayView(
            context = RuntimeEnvironment.getApplication(),
            side = EdgeSide.LEFT,
            visibleThicknessDp = 24,
            swipeThresholdDp = 20,
            onGesture = { _, _ -> },
            recognizerFactory = { width, height ->
                SideGestureRecognizer(
                    side = EdgeSide.LEFT,
                    screenWidthDp = width,
                    screenHeightDp = height,
                    zones = listOf(HotZoneSegment(0f, height, width, zoneId = 7)),
                    actions = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
                )
            },
            onGestureCommit = { commit -> commits += commit.action }
        )
        view.layout(0, 0, 48, 800)
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, x = 40f, y = 200f, time = 40L))

        assertEquals(listOf(GestureAction.Back), commits)
    }

    @Test
    fun overlayUsesTheEffectiveShortThresholdForVerticalSwipesByDefault() {
        val commits = mutableListOf<GestureType>()
        val view = EdgeOverlayView(
            context = RuntimeEnvironment.getApplication(),
            side = EdgeSide.LEFT,
            visibleThicknessDp = 24,
            swipeThresholdDp = 45,
            onGesture = { type, _ -> commits += type }
        )
        view.layout(0, 0, 48, 800)

        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 400f, time = 0L))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 0f, y = 365f, time = 20L))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, x = 0f, y = 365f, time = 40L))

        assertEquals(listOf(GestureType.SWIPE_UP), commits)
    }

    @Test
    fun legacyProgressCallbackKeepsScreenPixelCoordinatesAtNonUnitDensity() {
        val progress = mutableListOf<GestureData>()
        val detector = EdgeGestureDetector(
            side = EdgeSide.LEFT,
            minSwipeDistancePx = 20f,
            onGesture = { _, _ -> },
            onGestureProgress = progress::add,
            density = 2f,
            viewportWidthPx = { 96f },
            viewportHeightPx = { 1_600f }
        )

        detector.onTouchEvent(event(MotionEvent.ACTION_DOWN, x = 0f, y = 200f, time = 0L))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, x = 40f, y = 200f, time = 20L))

        assertEquals(40f, progress.single().endX)
    }

    private fun detector(
        onGesture: (GestureType, GestureData) -> Unit = { _, _ -> },
        onGestureProgress: (GestureData) -> Unit = {},
        onBackGestureCancel: () -> Unit = {}
    ): EdgeGestureDetector = EdgeGestureDetector(
        side = EdgeSide.LEFT,
        minSwipeDistancePx = 20f,
        longSwipeDistancePx = 60f,
        minVerticalSwipeDistancePx = 40f,
        onGesture = onGesture,
        onGestureProgress = onGestureProgress,
        onBackGestureCancel = onBackGestureCancel,
        viewportHeightPx = { 800f }
    )

    private fun event(action: Int, x: Float, y: Float, time: Long): MotionEvent =
        MotionEvent.obtain(0L, time, action, x, y, 0)

    private class RecordingPreviewDispatcher : GesturePreviewFrameDispatcher {
        override var consumer: ((GestureSignal.Preview) -> Unit)? = null
        private var pending: GestureSignal.Preview? = null

        override fun submit(preview: GestureSignal.Preview) {
            pending = preview
        }

        override fun cancel() {
            pending = null
        }

        fun flush() {
            pending?.let { consumer?.invoke(it) }
            pending = null
        }
    }
}
