package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun favoriteLibraryScreenUsesTheReusableSurfaceWorkspaceChrome() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FavoriteLibraryActivity.kt"
        ).readText()

        listOf(
            "internal fun FavoriteLibraryScreen(",
            "background(MaterialTheme.colorScheme.surface)",
            "FloatingWorkspaceTopAppBar(title = \"收藏\", onBack = onBack)",
            "loadFavoriteCollectionItems(context)",
            "OutlinedTextField(",
            "PrimaryTabRow",
            "HorizontalPager(",
            "FloatingChatFavoriteLibraryBridge.send(item)"
        ).forEach { requirement ->
            assertTrue("Missing $requirement", source.contains(requirement))
        }
        assertFalse(source.contains("Spacer(Modifier.height(favoriteLibraryStatusBarHeightDp().dp))"))
        assertFalse(source.contains("private fun FavoriteTopBar("))
    }
}
