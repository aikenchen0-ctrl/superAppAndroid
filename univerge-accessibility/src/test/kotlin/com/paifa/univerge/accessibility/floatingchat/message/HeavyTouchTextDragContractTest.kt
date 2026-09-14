package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessagePresentation
import com.paifa.univerge.core.model.FloatingChatMessageType
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyTouchTextDragContractTest {
    @Test
    fun onlyNonSystemPlainTextMessagesAreHeavyDragSources() {
        val plain = message(FloatingChatMessageType.Text)
        val mixed = message(FloatingChatMessageType.MixedText)
        val system = plain.copy(presentation = FloatingChatMessagePresentation.System)

        assertTrue(messageUsesTestHeavyDrag(plain))
        assertFalse(messageUsesTestHeavyDrag(mixed))
        assertFalse(messageUsesTestHeavyDrag(system))
    }

    @Test
    fun productionMessageBubbleUsesInjectedHostAndStableMessageSourceId() {
        val messageRow = sourceFile(
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/message/MessageRow.kt"
        ).readText()
        val overlayUi = sourceFile(
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val provider = sourceFile(
            "src/main/kotlin/com/paifa/univerge/accessibility/HeavyDragRuntimeProvider.kt"
        )

        assertTrue(messageRow.contains("LocalHeavyDragCoordinator"))
        assertTrue(messageRow.contains("heavyDraggable"))
        assertTrue(messageRow.contains("sourceId = message.id"))
        assertTrue(messageRow.contains("FloatingChatMessageType.Text"))
        assertTrue(messageRow.contains("heavyDragEnabled"))
        assertTrue(overlayUi.contains("HeavyDragHost"))
        assertTrue(overlayUi.contains("heavyDragLease"))
        assertTrue(overlayUi.contains("HeavyTextDropTargetsOverlay"))
        assertTrue(overlayUi.contains("onTextMessageDroppedToAiKnowledgeBase"))
        assertTrue(overlayUi.contains("onTextMessageDroppedToIntentTaskGenerator"))
        assertTrue(provider.isFile)
    }

    private fun message(type: FloatingChatMessageType): FloatingChatMessage {
        return FloatingChatMessage(
            id = "message-1",
            type = type,
            text = "测试文本",
            fromMe = false,
            senderName = "联系人",
            time = "刚刚"
        )
    }

    private fun sourceFile(path: String): File {
        return listOf(File(path), File("../../../../$path"))
            .firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
