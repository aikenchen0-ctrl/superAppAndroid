package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatWindowPresentationTest {
    @Test
    fun collapsedOverlayKeepsItsSurfaceButBecomesTransparentAndNonInteractive() {
        val presentation = floatingChatWindowPresentation(expanded = false)

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(0f, presentation.alpha, 0f)
        assertFalse(presentation.touchable)
        assertFalse(presentation.focusable)
    }

    @Test
    fun expandedOverlayRestoresTheRetainedSurfaceWithoutResizingIt() {
        val presentation = floatingChatWindowPresentation(expanded = true)

        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(1f, presentation.alpha, 0f)
        assertTrue(presentation.touchable)
        assertTrue(presentation.focusable)
    }
}
