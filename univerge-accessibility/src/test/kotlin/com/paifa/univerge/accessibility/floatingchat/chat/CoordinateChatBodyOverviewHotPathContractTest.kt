package com.paifa.univerge.accessibility.floatingchat.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateChatBodyOverviewHotPathContractTest {
    private val source by lazy { coordinateChatBodySource() }

    @Test
    fun homeOverviewGroupsAreBuiltOnceAndReusedForConnectorMap() {
        val groupCallCount = Regex("""\bhomeOverviewMessageGroups\(""")
            .findAll(source)
            .count()

        assertEquals("Home overview groups must be calculated once", 1, groupCallCount)

        val groupingSection = source.sectionBetween(
            start = "val homeOverviewMessageGroups =",
            end = "val viewportKey ="
        )
        val groupsIndex = groupingSection.indexOf("val homeOverviewMessageGroups =")
        val connectorMapIndex = groupingSection.indexOf("val homeOverviewConnectorGroupIds =")

        assertTrue("Groups must be calculated before the connector map", groupsIndex < connectorMapIndex)
        assertTrue(
            "Connector IDs must be derived from the remembered overview groups",
            Regex("""homeOverviewMessageGroups\s*\.flatMap""").containsMatchIn(groupingSection)
        )
    }

    @Test
    fun homeOverviewSkipsThreadMessageResolutionWhileChatModeKeepsIt() {
        val threadMessagesSection = source.sectionBetween(
            start = "val threadMessages =",
            end = "val visibleMessages ="
        )
        val overviewBranchIndex = threadMessagesSection.indexOf("if (homeOverviewVisible)")
        val emptyMessagesIndex = threadMessagesSection.indexOf("emptyList()")
        val normalBranchIndex = threadMessagesSection.indexOf("else", emptyMessagesIndex)
        val threadResolverIndex = threadMessagesSection.indexOf("visibleMessagesForThread(")

        assertTrue("Thread message resolution must choose the mode first", overviewBranchIndex >= 0)
        assertTrue("Overview mode must use an empty thread message list", emptyMessagesIndex > overviewBranchIndex)
        assertTrue("Normal chat resolution must stay in the else branch", normalBranchIndex > emptyMessagesIndex)
        assertTrue("Thread message resolution must stay unreachable from overview mode", threadResolverIndex > normalBranchIndex)

        val normalBranch = threadMessagesSection.substring(threadResolverIndex)
        assertTrue(normalBranch.contains("conversation = conversation"))
        assertTrue(normalBranch.contains("selection = selectedThread"))
        assertTrue(normalBranch.contains("selectedAccountId = selectedAccount.id"))
    }

    @Test
    fun homeOverviewUsesEmptyOffscreenIndexWithoutBuildingOne() {
        val offscreenSection = source.sectionBetween(
            start = "val offscreenConnectorIndex =",
            end = "val contactsById ="
        )
        val overviewBranchIndex = offscreenSection.indexOf("if (homeOverviewVisible)")
        val emptyIndexIndex = offscreenSection.indexOf("ConnectorOffscreenIndex.empty()")
        val normalBranchIndex = offscreenSection.indexOf("else", emptyIndexIndex)
        val builderIndex = offscreenSection.indexOf("buildOffscreenConnectorIndex(")

        assertTrue("Overview mode must branch before offscreen index work", overviewBranchIndex >= 0)
        assertTrue("Overview mode must use an empty offscreen index", emptyIndexIndex > overviewBranchIndex)
        assertTrue("Normal chat offscreen indexing must stay in the else branch", normalBranchIndex > emptyIndexIndex)
        assertTrue("Offscreen index building must stay unreachable from overview mode", builderIndex > normalBranchIndex)
    }

    @Test
    fun unusedHomeUnreadAvatarContactsAreRemoved() {
        assertFalse(source.contains("homeUnreadAvatarContacts"))
    }

    private fun String.sectionBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        val endIndex = indexOf(end, startIndex)
        require(endIndex > startIndex) { "Missing source marker after $start: $end" }
        return substring(startIndex, endIndex)
    }

    private fun coordinateChatBodySource(): String {
        val relativePath = "src/main/kotlin/com/paifa/univerge/accessibility/" +
            "floatingchat/chat/CoordinateChatBody.kt"
        val candidates = listOf(
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "univerge-accessibility/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("CoordinateChatBody.kt not found from ${System.getProperty("user.dir")}")
    }
}
