package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {
    private val sideActions = mapOf(
        GestureType.PULL_INWARD_SHORT to GestureAction.Back,
        GestureType.PULL_INWARD_LONG to GestureAction.Home,
        GestureType.PULL_INWARD_HOLD to GestureAction.Recents,
        GestureType.PULL_DIAGONAL_UP_SHORT to GestureAction.Notifications,
        GestureType.SWIPE_UP to GestureAction.QuickSettings
    )

    @Test
    fun sideMoveOnlyPreviewsAndUpCommitsOnce() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(HotZoneSegment(100f, 200f, thicknessDp = 32f)),
            actions = sideActions
        )

        recognizer.onDown(PointerSample(8f, 180f, 0L))
        val preview = recognizer.onMove(PointerSample(55f, 180f, 30L))
        assertTrue(preview is GestureSignal.Preview)
        assertEquals(GestureAction.Back, (preview as GestureSignal.Preview).action)
        assertTrue(recognizer.onMove(PointerSample(70f, 180f, 40L)) is GestureSignal.Preview)

        val commit = recognizer.onUp(PointerSample(70f, 180f, 50L))
        assertTrue(commit is GestureSignal.Commit)
        assertEquals(GestureAction.Back, (commit as GestureSignal.Commit).action)
        assertTrue(recognizer.onUp(PointerSample(70f, 180f, 60L)) is GestureSignal.Ignored)
    }

    @Test
    fun sideCancelsOnRetractionMultiTouchAndFocusLoss() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.RIGHT, 400f, 800f,
            listOf(HotZoneSegment(0f, 800f, thicknessDp = 32f)), sideActions
        )
        recognizer.onDown(PointerSample(392f, 200f, 0L))
        assertTrue(recognizer.onMove(PointerSample(350f, 200f, 20L)) is GestureSignal.Preview)
        assertTrue(recognizer.onMove(PointerSample(389f, 200f, 30L)) is GestureSignal.Cancel)
        assertTrue(recognizer.onUp(PointerSample(340f, 200f, 50L)) is GestureSignal.Ignored)

        val second = SideGestureRecognizer(EdgeSide.LEFT, 400f, 800f, listOf(HotZoneSegment(0f, 800f, 32f)), sideActions)
        second.onDown(PointerSample(5f, 200f, 0L))
        assertTrue(second.onMove(PointerSample(40f, 200f, 10L, pointerCount = 2)) is GestureSignal.Cancel)
        assertTrue(second.onFocusLost() is GestureSignal.Ignored)
    }

    @Test
    fun sideHoldIsPreviewedButOnlyUpCommits() {
        val recognizer = SideGestureRecognizer(
            EdgeSide.LEFT, 400f, 800f,
            listOf(HotZoneSegment(0f, 800f, 32f)), sideActions,
            thresholds = SideGestureThresholds(holdDurationMs = 400L)
        )
        recognizer.onDown(PointerSample(5f, 200f, 0L))
        val preview = recognizer.onMove(PointerSample(35f, 200f, 450L))
        assertEquals(GestureType.PULL_INWARD_HOLD, (preview as GestureSignal.Preview).gesture)
        assertTrue(recognizer.onUp(PointerSample(35f, 200f, 460L)) is GestureSignal.Commit)
    }

    @Test
    fun bottomBarRequiresHitAndCommitsLongPressOnUp() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.LONG_PRESS to GestureAction.Screenshot, GestureType.SWIPE_UP to GestureAction.Home)
        )
        assertEquals(GestureSignal.Ignored, recognizer.onDown(PointerSample(10f, 790f, 0L)))
        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val preview = recognizer.onMove(PointerSample(200f, 785f, 600L))
        assertEquals(GestureType.LONG_PRESS, (preview as GestureSignal.Preview).gesture)
        assertEquals(GestureAction.Screenshot, preview.action)
        assertEquals(GestureAction.Screenshot, (recognizer.onUp(PointerSample(200f, 785f, 700L)) as GestureSignal.Commit).action)
    }
}
