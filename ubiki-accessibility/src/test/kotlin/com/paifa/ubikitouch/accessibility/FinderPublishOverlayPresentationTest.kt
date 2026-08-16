package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun finderPublishScreenUsesTheSharedFloatingWorkspaceVisualHost() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FinderPublishActivity.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertFalse(source.contains("Spacer(Modifier.height(finderPublishStatusBarHeightDp().dp))"))
        assertFalse(source.contains("FinderPublishTopBar(onBack = onBack)"))
    }
}
