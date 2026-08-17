package com.paifa.ubikitouch.accessibility.floatingchat.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageListPrefetchPerformanceContractTest {
    private val source by lazy { coordinateChatBodySource() }

    @Test
    fun messageListAvoidsSchedulingBackgroundMessageCompositionDuringScroll() {
        val stateSection = source.sectionBetween(
            start = "val messageListState =",
            end = "val viewportTracker ="
        )

        assertTrue(stateSection.contains("prefetchStrategy = NoMessageListPrefetchStrategy"))
    }

    @Test
    fun messageListPrefetchStrategyDoesNotScheduleItems() {
        val strategySection = source.sectionFrom("private object NoMessageListPrefetchStrategy")

        assertTrue(strategySection.contains("override fun LazyListPrefetchScope.onScroll"))
        assertTrue(strategySection.contains("override fun LazyListPrefetchScope.onVisibleItemsUpdated"))
        assertTrue(strategySection.contains("override fun NestedPrefetchScope.onNestedPrefetch"))
        assertFalse(strategySection.contains("schedulePrefetch("))
    }

    private fun String.sectionBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        val endIndex = indexOf(end, startIndex)
        require(endIndex > startIndex) { "Missing source marker after $start: $end" }
        return substring(startIndex, endIndex)
    }

    private fun String.sectionFrom(start: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing source marker: $start" }
        return substring(startIndex)
    }

    private fun coordinateChatBodySource(): String {
        val relativePath = "src/main/kotlin/com/paifa/ubikitouch/accessibility/" +
            "floatingchat/chat/CoordinateChatBody.kt"
        val candidates = listOf(
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "ubiki-accessibility/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("CoordinateChatBody.kt not found from ${System.getProperty("user.dir")}")
    }
}
