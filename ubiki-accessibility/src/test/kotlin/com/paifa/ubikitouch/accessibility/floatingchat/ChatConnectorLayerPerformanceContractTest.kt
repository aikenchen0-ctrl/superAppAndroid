package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConnectorLayerPerformanceContractTest {
    private val source by lazy {
        sourceFile("floatingchat/chat/ChatConnectorLayer.kt").readText()
    }

    @Test
    fun connectorKeysAreRememberedFromEverySemanticInput() {
        val rememberArguments = rememberArgumentsFor("connectorTargetKeysByMessageId")

        listOf(
            "messages",
            "selection",
            "selectedAccountId",
            "homeOverviewVisible",
            "homeOverviewConnectorGroupIds",
            "groupMemberAvatarsVisible"
        ).forEach { input ->
            assertTrue("Missing remember input: $input", input in rememberArguments)
        }
    }

    @Test
    fun ordinaryVisibleItemsDoNotAllocateSingletonMessageLists() {
        assertFalse(source.contains("listOfNotNull(messages.getOrNull(itemInfo.index))"))
    }

    @Test
    fun connectorDrawDoesNotMaintainAnIdentityAvatarSourceMap() {
        assertFalse(source.contains("avatarSourceKeys"))
    }

    @Test
    fun visibleBubbleScratchDropsHistoricalKeysAndUsesCompleteRememberInputs() {
        val rememberArguments = rememberArgumentsFor("visibleBubbleGroups")

        listOf(
            "messages",
            "selection",
            "selectedAccountId",
            "homeOverviewVisible",
            "homeOverviewConnectorGroupIds",
            "groupMemberAvatarsVisible"
        ).forEach { input ->
            assertTrue("Missing scratch remember input: $input", input in rememberArguments)
        }
        assertTrue(source.contains("visibleBubbleGroups.keys.retainAll(activeBubbleGroupKeys)"))
    }

    @Test
    fun visibleIndexRangeIsAccumulatedDuringTheMessageLoop() {
        assertFalse(source.contains("visibleItems.minOf"))
        assertFalse(source.contains("visibleItems.maxOf"))
        assertTrue(source.contains("firstVisibleIndex = minOf(firstVisibleIndex, itemInfo.index)"))
        assertTrue(source.contains("lastVisibleIndex = maxOf(lastVisibleIndex, itemInfo.index)"))
    }

    private fun rememberArgumentsFor(variableName: String): String {
        val match = Regex(
            pattern = """val\s+${Regex.escape(variableName)}\s*=\s*remember\((.*?)\)\s*\{""",
            option = RegexOption.DOT_MATCHES_ALL
        ).find(source)
        assertTrue("Missing remembered value: $variableName", match != null)
        return match?.groupValues?.get(1).orEmpty()
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
