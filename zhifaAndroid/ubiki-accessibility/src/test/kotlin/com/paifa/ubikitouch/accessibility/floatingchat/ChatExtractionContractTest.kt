package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExtractionContractTest {
    @Test
    fun chatContractAndRenderEntryPointsAreSeparated() {
        val contract = sourceFile("floatingchat/contract/ChatContract.kt")
        val coordinator = sourceFile("floatingchat/shell/FloatingChatCoordinator.kt")
        val shell = sourceFile("floatingchat/shell/FloatingChatShell.kt")
        val screen = sourceFile("floatingchat/chat/ChatScreen.kt")

        listOf(contract, coordinator, shell, screen).forEach { source ->
            assertTrue("Missing extracted chat source: ${source.path}", source.isFile)
        }
        val contractText = contract.readText()
        assertTrue(contractText.contains("data class ChatUiState"))
        assertTrue(contractText.contains("sealed interface ChatUiEvent"))
        assertFalse(contractText.contains("import android."))
        assertFalse(contractText.contains("import androidx.compose."))

        assertTrue(coordinator.readText().contains("class FloatingChatCoordinator"))
        assertTrue(shell.readText().contains("fun FloatingChatShell("))
        assertTrue(screen.readText().contains("fun ChatScreen("))
    }

    @Test
    fun sessionRailModelAndOrderingLiveInChatPackage() {
        val extracted = sourceFile(
            "floatingchat/chat/ChatSessionRail.kt"
        )
        assertTrue("Missing extracted session rail source", extracted.isFile)

        val text = extracted.readText()
        assertTrue(text.contains("sealed class SessionRailItem"))
        assertTrue(text.contains("fun sessionRailItemKeys("))
        assertTrue(text.contains("fun sessionRailItemKeysByLatestChatTime("))
        assertTrue(text.contains("fun sessionRailItemsByLatestChatTime("))
    }

    @Test
    fun connectorGeometryLivesInChatPackage() {
        val extracted = sourceFile("floatingchat/chat/ChatConnectorGeometry.kt")
        val layer = sourceFile("floatingchat/chat/ChatConnectorLayer.kt")
        assertTrue("Missing extracted connector geometry", extracted.isFile)
        assertTrue("Missing extracted connector layer", layer.isFile)

        val text = extracted.readText()
        assertTrue(text.contains("data class ChatConnectorTree"))
        assertTrue(text.contains("fun createChatConnectorLine("))
        assertTrue(text.contains("fun createChatConnectorTree("))
        assertTrue(text.contains("fun createChatConnectorBraceGeometry("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal data class ChatConnectorTree"))
        assertFalse(legacy.contains("internal fun createChatConnectorLine("))
        assertFalse(legacy.contains("internal fun createChatConnectorTree("))
        assertFalse(legacy.contains("private fun ChatConnectorLayer("))
    }

    @Test
    fun messagePresentationAndContentDispatchLiveInMessagePackage() {
        val presentation = sourceFile("floatingchat/message/MessagePresentation.kt")
        val listPresentation = sourceFile("floatingchat/message/MessageListPresentation.kt")
        val content = sourceFile("floatingchat/message/MessageContent.kt")
        val row = sourceFile("floatingchat/message/MessageRow.kt")
        val textContent = sourceFile("floatingchat/message/MessageTextContent.kt")
        val cards = sourceFile("floatingchat/message/MessageCards.kt")

        listOf(presentation, listPresentation, content, row, textContent, cards).forEach { source ->
            assertTrue("Missing extracted message source: ${source.path}", source.isFile)
        }
        assertTrue(content.readText().contains("fun MessageContent("))
        assertTrue(row.readText().contains("fun MessageRow("))
        assertTrue(row.readText().contains("fun MessageBlock("))
        assertTrue(textContent.readText().contains("fun SimpleTextMessageContent("))
        assertTrue(textContent.readText().contains("fun MixedTextMessageContent("))
        assertTrue(textContent.readText().contains("fun QuoteMessageContent("))
        assertTrue(textContent.readText().contains("fun ChatHistoryMessageContent("))
        assertTrue(cards.readText().contains("fun LocationMessageContent("))
        assertTrue(cards.readText().contains("fun ContactLinkCardContent("))
        assertTrue(cards.readText().contains("fun MiniProgramLinkContent("))
        assertTrue(cards.readText().contains("fun InlineContactContent("))
        assertTrue(cards.readText().contains("fun InlineLocationContent("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun MessageContent("))
        assertFalse(legacy.contains("private fun MessageRow("))
        assertFalse(legacy.contains("internal fun MessageBlock("))
        assertFalse(legacy.contains("internal fun SimpleTextMessageContent("))
        assertFalse(legacy.contains("internal fun MixedTextMessageContent("))
        assertFalse(legacy.contains("internal fun QuoteMessageContent("))
        assertFalse(legacy.contains("internal fun ChatHistoryMessageContent("))
        assertFalse(legacy.contains("internal fun LocationMessageContent("))
        assertFalse(legacy.contains("internal fun ContactLinkCardContent("))
        assertFalse(legacy.contains("internal fun MiniProgramLinkContent("))
        assertFalse(legacy.contains("internal fun InlineContactContent("))
        assertFalse(legacy.contains("internal fun InlineLocationContent("))
    }

    @Test
    fun legacyOverlayNoLongerDefinesSessionRailOrdering() {
        val legacy = sourceFile("FloatingChatOverlayUi.kt")
        assertTrue("Missing legacy overlay source", legacy.isFile)

        val text = legacy.readText()
        assertFalse(text.contains("private sealed class SessionRailItem"))
        assertFalse(text.contains("internal fun sessionRailItemKeys("))
        assertFalse(text.contains("internal fun sessionRailItemKeysByLatestChatTime("))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
    }
}
