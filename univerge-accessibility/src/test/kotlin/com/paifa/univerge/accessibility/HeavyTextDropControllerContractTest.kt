package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyTextDropControllerContractTest {
    @Test
    fun overlayControllerExposesTextDropPortForBothDestinations() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        ).readText()

        assertTrue(source.contains("onHeavyTextDrop"))
        assertTrue(source.contains("onTextMessageDroppedToAiKnowledgeBase"))
        assertTrue(source.contains("onTextMessageDroppedToIntentTaskGenerator"))
    }

    @Test
    fun accessibilityServiceConnectsTheDropPortWithoutLoggingMessageText() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt"
        ).readText()

        assertTrue(source.contains("onHeavyTextDrop ="))
        assertTrue(source.contains("event.message.id"))
        assertTrue(!source.contains("event.message.text"))
    }
}
