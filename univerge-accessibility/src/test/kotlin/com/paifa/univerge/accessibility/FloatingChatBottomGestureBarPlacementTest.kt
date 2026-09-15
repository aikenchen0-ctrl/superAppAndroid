package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatBottomGestureBarPlacementTest {
    @Test
    fun expandedBottomBarReceivesConfiguredWidthAndStaysAtThePhysicalBottom() {
        val source = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertTrue(source.contains("bottomGestureBarWidthDp: Int"))
        assertTrue(source.contains("widthDp = bottomGestureBarWidthDp"))

        val barStart = source.indexOf("FloatingChatExpandedBottomGestureBar(")
        val nextHost = source.indexOf("FavoriteCollectionOverlayHost(", barStart)
        assertTrue(barStart >= 0)
        assertTrue(nextHost > barStart)
        val barBlock = source.substring(barStart, nextHost)
        assertFalse(barBlock.contains("navigationBarsPadding()"))
    }

    private fun sourceFile(name: String): File {
        val modulePath = File("src/main/kotlin/com/paifa/univerge/accessibility", name)
        if (modulePath.isFile) return modulePath
        return File("univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility", name)
    }
}
