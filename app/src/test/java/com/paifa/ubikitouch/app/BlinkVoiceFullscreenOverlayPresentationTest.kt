package com.paifa.ubikitouch.app

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test flow: create the window contract, then verify fullscreen geometry, accessibility type,
 * focusability, status-bar spacing, and the two translation endpoints used by the property animation.
 */
class BlinkVoiceFullscreenOverlayPresentationTest {
    @Test
    fun blinkVoiceUsesAnInteractiveFullscreenAccessibilityOverlay() {
        val presentation = blinkVoiceFullscreenOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, blinkVoiceFullscreenStatusBarHeightDp())
    }

    @Test
    fun overlayMotionSlidesUpOnEntryAndDownOnExit() {
        assertEquals(1_000f, blinkVoiceFullscreenEntryTranslationY(1_000))
        assertEquals(-1_000f, blinkVoiceFullscreenExitTranslationY(1_000))
    }
}
