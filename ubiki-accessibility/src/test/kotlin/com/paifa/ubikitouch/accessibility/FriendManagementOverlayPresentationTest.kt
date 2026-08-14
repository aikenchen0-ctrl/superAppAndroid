package com.paifa.ubikitouch.accessibility

import android.view.WindowManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test flow: verify the friend-management fullscreen window contract and translation endpoints. */
class FriendManagementOverlayPresentationTest {
    @Test
    fun friendManagementUsesFullscreenAccessibilityOverlay() {
        val presentation = friendManagementOverlayWindowPresentation()
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, presentation.height)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, presentation.type)
        assertTrue(presentation.focusable)
        assertEquals(30, friendManagementStatusBarHeightDp())
    }

    @Test
    fun overlayUsesVerticalPropertyAnimationEndpoints() {
        assertEquals(1_000f, friendManagementEntryTranslationY(1_000))
        assertEquals(-1_000f, friendManagementExitTranslationY(1_000))
    }

    @Test
    fun friendManagementScreenIsReusableWhileTheOverlayControllerKeepsUsingIt() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FriendManagementOverlayController.kt"
        ).readText()

        assertTrue(source.contains("internal fun FriendManagementFullscreenScreen("))
        assertTrue(source.contains("setContent { FriendManagementFullscreenScreen(onBack = ::dismiss) }"))
    }
}
