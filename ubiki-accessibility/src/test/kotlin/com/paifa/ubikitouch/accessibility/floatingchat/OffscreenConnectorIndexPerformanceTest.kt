package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorTargetKey
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorViewportEdgeState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.buildOffscreenConnectorIndex
import com.paifa.ubikitouch.accessibility.floatingchat.chat.offscreenConnectorEdges
import com.paifa.ubikitouch.accessibility.floatingchat.chat.offscreenConnectorTargetKey
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffscreenConnectorIndexPerformanceTest {
    @Test
    fun indexQueriesMatchPrefixAndSuffixSetsAtEveryViewportPosition() {
        val messages = listOf(
            message("account-a-1", FloatingChatConnectionTarget.Account, "account-a"),
            message("user-1", FloatingChatConnectionTarget.User, "user-a"),
            message("account-a-2", FloatingChatConnectionTarget.Account, "account-a"),
            message("none", FloatingChatConnectionTarget.None, null),
            message("account-b", FloatingChatConnectionTarget.Account, "account-b")
        )
        val selection = ChatThreadSelection.Group
        val keys = messages.map { message ->
            offscreenConnectorTargetKey(
                message = message,
                selection = selection,
                selectedAccountId = "selected-account",
                homeOverviewVisible = false,
                groupMemberAvatarsVisible = false
            )
        }
        val index = buildOffscreenConnectorIndex(
            messages = messages,
            selection = selection,
            selectedAccountId = "selected-account",
            homeOverviewVisible = false,
            groupMemberAvatarsVisible = false
        )

        for (firstVisible in -1..messages.size) {
            for (lastVisible in -1..messages.size) {
                val expected = expectedEdges(keys, firstVisible, lastVisible)
                val actual = offscreenConnectorEdges(index, firstVisible, lastVisible)
                assertEquals(
                    "first=$firstVisible last=$lastVisible",
                    expected,
                    actual
                )
                assertEquals(
                    "insertion order changed for first=$firstVisible last=$lastVisible",
                    expected.keys.toList(),
                    actual.keys.toList()
                )
            }
        }
    }

    @Test
    fun indexStoresTargetRangesInsteadOfCopyingSetsForEveryMessage() {
        val source = productionSource("floatingchat/chat/OffscreenConnectorTargetResolver.kt").readText()

        assertTrue(source.contains("ConnectorOffscreenIndex.fromKeys("))
        assertFalse(source.contains("MutableList(messages.size)"))
        assertFalse(source.contains("seenBefore.toSet()"))
        assertFalse(source.contains("seenAfter.toSet()"))
    }

    private fun expectedEdges(
        keys: List<ConnectorTargetKey?>,
        firstVisibleIndex: Int,
        lastVisibleIndex: Int
    ): Map<ConnectorTargetKey, ConnectorViewportEdgeState> {
        if (keys.isEmpty()) return emptyMap()
        val firstVisible = firstVisibleIndex.coerceIn(keys.indices)
        val lastVisible = lastVisibleIndex.coerceIn(keys.indices)
        val result = linkedMapOf<ConnectorTargetKey, ConnectorViewportEdgeState>()
        keys.take(firstVisible).forEach { key ->
            key ?: return@forEach
            result[key] = ConnectorViewportEdgeState(hasAbove = true)
        }
        keys.drop(lastVisible + 1).asReversed().forEach { key ->
            key ?: return@forEach
            val previous = result[key]
            result[key] = ConnectorViewportEdgeState(
                hasAbove = previous?.hasAbove == true,
                hasBelow = true
            )
        }
        return result
    }

    private fun message(
        id: String,
        target: FloatingChatConnectionTarget,
        targetId: String?
    ): FloatingChatMessage = FloatingChatMessage(
        id = id,
        type = FloatingChatMessageType.Text,
        text = id,
        fromMe = target == FloatingChatConnectionTarget.Account,
        senderName = id,
        time = "10:00",
        connectionTarget = target,
        connectionTargetId = targetId
    )

    private fun productionSource(relativePath: String): File {
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
