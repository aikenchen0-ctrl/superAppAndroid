package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedDraftKey
import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedDraftMode
import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedOverviewState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedRecipientIndicators
import com.paifa.ubikitouch.accessibility.floatingchat.chat.restoreUnrepliedOverviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnrepliedOverviewStateTest {
    @Test
    fun restoreKeepsValidUiStateButUsesLatestItems() {
        val draftKey = UnrepliedDraftKey("account-a", "thread-a")
        val saved = UnrepliedOverviewState(
            accountFilterId = "account-a",
            selectedItemId = "account-a::thread-a",
            firstVisibleItemIndex = 8,
            firstVisibleItemScrollOffset = 24,
            draftMode = UnrepliedDraftMode.Bottom,
            drafts = mapOf(draftKey to "稍后回复"),
            indicators = UnrepliedRecipientIndicators(
                watermarkVisible = false,
                colorDotVisible = true
            )
        )

        val restored = restoreUnrepliedOverviewState(
            saved = saved,
            availableAccountIds = setOf("account-a"),
            availableItemIds = listOf("account-a::thread-a", "account-a::thread-b")
        )

        assertTrue(restored.visible)
        assertEquals("account-a", restored.accountFilterId)
        assertEquals("account-a::thread-a", restored.selectedItemId)
        assertEquals(1, restored.firstVisibleItemIndex)
        assertEquals(24, restored.firstVisibleItemScrollOffset)
        assertEquals(UnrepliedDraftMode.Bottom, restored.draftMode)
        assertEquals("稍后回复", restored.drafts[draftKey])
        assertFalse(restored.indicators.watermarkVisible)
        assertTrue(restored.indicators.colorDotVisible)
    }

    @Test
    fun restoreDropsInvalidSelectionAndAccountFilter() {
        val saved = UnrepliedOverviewState(
            accountFilterId = "removed-account",
            selectedItemId = "removed-item",
            firstVisibleItemIndex = 4,
            firstVisibleItemScrollOffset = 18
        )

        val restored = restoreUnrepliedOverviewState(
            saved = saved,
            availableAccountIds = setOf("account-a"),
            availableItemIds = listOf("account-a::thread-a")
        )

        assertNull(restored.accountFilterId)
        assertNull(restored.selectedItemId)
        assertEquals(0, restored.firstVisibleItemIndex)
        assertEquals(18, restored.firstVisibleItemScrollOffset)
    }

    @Test
    fun restoreEmptyOverviewResetsScrollPosition() {
        val restored = restoreUnrepliedOverviewState(
            saved = UnrepliedOverviewState(
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 40
            ),
            availableAccountIds = emptySet(),
            availableItemIds = emptyList()
        )

        assertTrue(restored.visible)
        assertEquals(0, restored.firstVisibleItemIndex)
        assertEquals(0, restored.firstVisibleItemScrollOffset)
    }
}
