package com.paifa.univerge.accessibility.floatingchat.chat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDoubleClickRoutingContractTest {
    @Test
    fun `double click callback is forwarded through message block and overview group`() {
        val row = source("floatingchat/message/MessageRow.kt")
        val blockSignature = row.substringAfter("internal fun MessageBlock(")
            .substringBefore(") {")
        assertTrue(blockSignature.contains("onDoubleClick: () -> Unit"))
        assertTrue(row.contains("onDoubleClick = onDoubleClick"))

        val pane = source("floatingchat/chat/MessageCoordinatePane.kt")
        val groupSignature = pane.substringAfter("private fun HomeOverviewMessageGroupRow(")
            .substringBefore(") {")
        assertTrue(groupSignature.contains("onMessageDoubleClick"))
        assertTrue(pane.contains("onMessageDoubleClick = onMessageDoubleClick"))
        assertTrue(pane.contains("onDoubleClick = { onMessageDoubleClick(message) }"))
    }

    @Test
    fun `home overview renders every unread message and routes each one to its summary`() {
        val body = source("floatingchat/chat/CoordinateChatBody.kt")

        assertTrue(body.contains("homeUnreadSummaries.flatMap { summary -> summary.messages }"))
        assertTrue(body.contains("summary.messages.map { message -> message.id to summary }"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            File(System.getProperty("user.dir"), "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"),
            File(System.getProperty("user.dir"), "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source file not found from ${System.getProperty("user.dir")}: $relativePath")
    }
}
