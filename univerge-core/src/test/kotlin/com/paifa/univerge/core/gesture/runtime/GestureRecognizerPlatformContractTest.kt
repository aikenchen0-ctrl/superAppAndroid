package com.paifa.univerge.core.gesture.runtime

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerPlatformContractTest {
    @Test
    fun sideZoneMatchesConfiguredHorizontalInset() {
        val recognizer = SideGestureRecognizer(
            side = EdgeSide.LEFT,
            screenWidthDp = 400f,
            screenHeightDp = 800f,
            zones = listOf(
                HotZoneSegment(
                    startDp = 100f,
                    lengthDp = 200f,
                    thicknessDp = 24f,
                    edgeInsetDp = 32f
                )
            ),
            actions = mapOf(GestureType.PULL_INWARD_SHORT to GestureAction.Back)
        )

        assertEquals(GestureSignal.Ignored, recognizer.onDown(PointerSample(12f, 180f, 0L)))
        assertEquals(GestureSignal.Ignored, recognizer.onDown(PointerSample(36f, 180f, 10L)))
        assertTrue(recognizer.onMove(PointerSample(72f, 180f, 20L)) is GestureSignal.Preview)
    }

    @Test
    fun bottomUpSwipeBecomesHoldOnlyAfterStationaryWindow() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.SWIPE_UP_HOLD to GestureAction.Screenshot),
            thresholds = BottomGestureThresholds(
                upwardHoldDurationMs = 500L
            )
        )
        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val moving = recognizer.onMove(PointerSample(200f, 700f, 100L))
        assertEquals(GestureType.SWIPE_UP, (moving as GestureSignal.Preview).gesture)

        val held = recognizer.onHoldTimer(600L)
        assertEquals(GestureSignal.Ignored, held)
        val preview = recognizer.onMove(PointerSample(200f, 700f, 601L))
        assertEquals(GestureType.SWIPE_UP_HOLD, (preview as GestureSignal.Preview).gesture)
        assertEquals(
            GestureType.SWIPE_UP_HOLD,
            (recognizer.onUp(PointerSample(200f, 700f, 620L)) as GestureSignal.Commit).gesture
        )
    }

    @Test
    fun bottomReleaseWithoutMovementCommitsTap() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f),
            mapOf(GestureType.TAP to GestureAction.Recents)
        )

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        val commit = recognizer.onUp(PointerSample(200f, 785f, 120L))
        assertEquals(GestureType.TAP, (commit as GestureSignal.Commit).gesture)
        assertEquals(GestureAction.Recents, commit.action)
    }

    @Test
    fun bottomGeometryCanBeUpdatedOnlyWhenTheRecognizerIsIdle() {
        val recognizer = BottomGestureRecognizer(
            BottomBarConfig(400f, 800f, widthDp = 120f, heightDp = 32f),
            mapOf(GestureType.TAP to GestureAction.Recents)
        )
        assertEquals(false, recognizer.hitTest(300f, 785f))
        assertEquals(true, recognizer.updateGeometry(BottomBarConfig(400f, 800f, widthDp = 240f, heightDp = 32f)))
        assertEquals(true, recognizer.hitTest(300f, 785f))

        recognizer.onDown(PointerSample(200f, 785f, 0L))
        assertEquals(false, recognizer.updateGeometry(BottomBarConfig(400f, 800f, widthDp = 120f, heightDp = 32f)))
        assertEquals(true, recognizer.hitTest(300f, 785f))
        recognizer.onCancel()
        assertEquals(true, recognizer.updateGeometry(BottomBarConfig(400f, 800f, widthDp = 120f, heightDp = 32f)))
    }
}
