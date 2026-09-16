package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BottomGestureBarReliabilityTest {
    @Test
    fun layoutUsesConfiguredWidthAndHeightAtTheSameDensityAsTheRecognizer() {
        assertEquals(
            400 to 80,
            bottomGestureBarLayoutSizePx(widthDp = 200, heightDp = 40, density = 2f)
        )
    }

    @Test
    fun recognizerGeometryUsesTheRealDisplayPixelBounds() {
        assertEquals(
            400f to 800f,
            bottomGestureBarScreenSizeDp(
                screenWidthPx = 1_080,
                screenHeightPx = 2_160,
                density = 2.7f
            )
        )
    }

    @Test
    fun overlayThresholdsKeepTheLegacyPhysicalDistanceAcrossDensities() {
        val thresholds = bottomGestureBarThresholds(density = 2f)

        assertEquals(28f, thresholds.minSwipeDistanceDp, 0.001f)
        assertEquals(3f, thresholds.slopDp, 0.001f)
    }

    @Test
    fun exclusionRectsAreEmptyBeforeAndroidQ() {
        assertTrue(bottomGestureBarExclusionRects(sdkInt = 28, width = 400, height = 80).isEmpty())
        val rects = bottomGestureBarExclusionRects(sdkInt = 29, width = 400, height = 80)
        assertEquals(1, rects.size)
        assertEquals(0, rects[0].left)
        assertEquals(0, rects[0].top)
        assertEquals(400, rects[0].right)
        assertEquals(80, rects[0].bottom)
    }

    @Test
    fun bottomGestureBarActionIsCommittedOnlyOnceOnUp() {
        val commits = mutableListOf<BottomGestureBarGestureType>()
        val session = BottomGestureBarDispatchSession<BottomGestureBarGestureType> { commits += it }

        session.onDown()
        session.onMove()
        assertTrue(commits.isEmpty())
        session.onUp(BottomGestureBarGestureType.SwipeUpHold)
        session.onUp(BottomGestureBarGestureType.SwipeUpHold)

        assertEquals(listOf(BottomGestureBarGestureType.SwipeUpHold), commits)

        session.onDown()
        session.onCancel()
        session.onUp(BottomGestureBarGestureType.SwipeUpHold)
        assertEquals(listOf(BottomGestureBarGestureType.SwipeUpHold), commits)
    }

    @Test
    fun bottomGestureBarActionCanCommitWhenRecognitionThresholdIsCrossedBeforeUp() {
        val commits = mutableListOf<BottomGestureBarGestureType>()
        val session = BottomGestureBarDispatchSession<BottomGestureBarGestureType> { commits += it }

        session.onDown()

        assertTrue(session.onCommit(BottomGestureBarGestureType.SwipeUp))
        assertFalse(session.onUp(BottomGestureBarGestureType.SwipeUp))
        assertEquals(listOf(BottomGestureBarGestureType.SwipeUp), commits)
    }

    @Test
    fun earlyCommitRemainsPendingUntilTheAdapterFinishesTheTouchTransaction() {
        val session = BottomGestureBarDispatchSession<BottomGestureBarGestureType>()

        session.onDown()
        assertTrue(session.onCommit(BottomGestureBarGestureType.SwipeUp))
        assertTrue(session.hasPendingTerminal)

        session.onFinish()

        assertFalse(session.hasPendingTerminal)
    }
}
