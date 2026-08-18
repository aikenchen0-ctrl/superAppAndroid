package com.paifa.univerge.accessibility.floatingchat.chat

import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLayoutStateTest {
    @Test
    fun selectedRailActionsAppearOnlyAfterFiveSecondsWithoutScroll() {
        assertFalse(leftRailSelectionActionsVisible(4_999L, isSelected = true, isScrolling = false))
        assertTrue(leftRailSelectionActionsVisible(5_000L, isSelected = true, isScrolling = false))
        assertFalse(leftRailSelectionActionsVisible(5_000L, isSelected = true, isScrolling = true))
        assertFalse(leftRailSelectionActionsVisible(6_000L, isSelected = false, isScrolling = false))
    }

    @Test
    fun followTextIncludesRegionAndTagsFromContactMetadata() {
        val contact = FloatingChatContact(
            id = "contact-1",
            name = "林舟",
            initials = "林",
            description = "最近消息",
            avatarColor = 0xFF5E8FA3,
            region = "浙江 杭州",
            tags = listOf("客户", "摄影")
        )
        val conversation = FloatingChatConversation(
            peerName = "消息",
            accountName = "账号",
            contacts = listOf(contact),
            accountContacts = emptyList(),
            messages = emptyList(),
            toolActions = emptyList()
        )

        val info = leftRailFollowInfoForContact(conversation, contact, selectedAccountId = "account")

        assertEquals(listOf("地区 浙江 杭州", "标签 客户 · 摄影"), info.metaLines)
        assertEquals("地区 浙江 杭州  ·  标签 客户 · 摄影", leftRailFollowMetaText(info))
    }

    @Test
    fun profilePlaceholderUsesSelectedContactMetadata() {
        val contact = FloatingChatContact(
            id = "contact-2",
            name = "顾言",
            initials = "顾",
            description = "重点联系人",
            avatarColor = 0xFF426B8A,
            region = "上海",
            tags = listOf("合作方")
        )

        val placeholder = leftRailProfilePlaceholder(contact)

        assertEquals("顾言", placeholder.name)
        assertEquals("上海", placeholder.region)
        assertEquals(listOf("合作方"), placeholder.tags)
        assertEquals("重点联系人", placeholder.summary)
    }
}
