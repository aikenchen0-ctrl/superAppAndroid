package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinderPublishOverlayPresentationTest {
    @Test
    fun finderPublishUsesAnInteractiveFullscreenAccessibilityOverlay() {
        val presentation = finderPublishOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, finderPublishStatusBarHeightDp())
    }

    @Test
    fun overlayMotionSlidesUpOnEntryAndDownOnExit() {
        assertEquals(1_000f, finderPublishEntryTranslationY(1_000))
        assertEquals(0f, finderPublishEntryTranslationY(0))
        assertEquals(-1_000f, finderPublishExitTranslationY(1_000))
        assertEquals(0f, finderPublishExitTranslationY(0))
    }
}
