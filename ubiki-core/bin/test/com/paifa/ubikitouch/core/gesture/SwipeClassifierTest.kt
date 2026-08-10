package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeClassifierTest {
    private val classifier = SwipeClassifier()

    @Test
    fun classifiesVerticalSwipeWithoutRequiringInwardMotion() {
        assertEquals(GestureType.SWIPE_UP, classifier.classify(EdgeSide.LEFT, 4f, -80f))
        assertEquals(GestureType.SWIPE_DOWN, classifier.classify(EdgeSide.RIGHT, -4f, 80f))
    }

    @Test
    fun classifiesInwardPullByEdgeSide() {
        assertEquals(GestureType.PULL_INWARD_SHORT, classifier.classify(EdgeSide.LEFT, 90f, 4f))
        assertEquals(GestureType.PULL_INWARD_SHORT, classifier.classify(EdgeSide.RIGHT, -90f, 4f))
    }

    @Test
    fun classifiesAngledInwardPullsAsInwardByDefault() {
        assertEquals(GestureType.PULL_INWARD_SHORT, classifier.classify(EdgeSide.LEFT, 60f, 90f))
        assertEquals(GestureType.PULL_INWARD_SHORT, classifier.classify(EdgeSide.RIGHT, -60f, -90f))
    }

    @Test
    fun keepsMostlyVerticalSwipesSeparateFromInwardPulls() {
        assertEquals(GestureType.SWIPE_UP, classifier.classify(EdgeSide.LEFT, 20f, -80f))
        assertEquals(GestureType.SWIPE_DOWN, classifier.classify(EdgeSide.RIGHT, -20f, 80f))
    }

    @Test
    fun canClassifyDiagonalPullsWhenEnabled() {
        val diagonalClassifier = SwipeClassifier(diagonalPullsEnabled = true)

        assertEquals(GestureType.PULL_DIAGONAL_DOWN, diagonalClassifier.classify(EdgeSide.LEFT, 80f, 60f))
        assertEquals(GestureType.PULL_DIAGONAL_UP, diagonalClassifier.classify(EdgeSide.RIGHT, -80f, -60f))
    }

    @Test
    fun rejectsOutwardHorizontalMotion() {
        assertNull(classifier.classify(EdgeSide.LEFT, -90f, 4f))
        assertNull(classifier.classify(EdgeSide.RIGHT, 90f, 4f))
    }
}
