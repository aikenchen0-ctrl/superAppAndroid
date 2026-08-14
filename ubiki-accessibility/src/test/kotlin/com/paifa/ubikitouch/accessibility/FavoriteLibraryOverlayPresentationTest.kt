package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteLibraryOverlayPresentationTest {
    @Test
    fun favoriteLibraryUsesAnInteractiveFullscreenAccessibilityOverlay() {
        val presentation = favoriteLibraryOverlayWindowPresentation()

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, favoriteLibraryStatusBarHeightDp())
    }

    @Test
    fun overlayMotionSlidesUpOnEntryAndDownOnExit() {
        assertEquals(1_000f, favoriteLibraryEntryTranslationY(1_000))
        assertEquals(0f, favoriteLibraryEntryTranslationY(0))
        assertEquals(-1_000f, favoriteLibraryExitTranslationY(1_000))
        assertEquals(0f, favoriteLibraryExitTranslationY(0))
    }
}
