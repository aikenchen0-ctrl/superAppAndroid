package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatHeaderStateTest {
    @Test
    fun allAccountUnreadUsesBackAndAllUnreadTitle() {
        val state = floatingChatHeaderState(
            route = FloatingChatHeaderRoute.AllAccountsUnread,
            accountName = "工作号",
            conversationTitle = "联系人",
            unreadCount = 4,
            messageScrollInProgress = false,
            editable = false
        )

        assertEquals("返回", state.leadingLabel)
        assertEquals("全部未回消息", state.title)
        assertFalse(state.showUnreadDot)
        assertFalse(state.showEdit)
        assertFalse(state.compact)
    }

    @Test
    fun singleAccountUnreadUsesAccountScopedTitle() {
        val state = floatingChatHeaderState(
            route = FloatingChatHeaderRoute.SingleAccountUnread,
            accountName = "工作号",
            conversationTitle = "联系人",
            unreadCount = 2,
            messageScrollInProgress = false,
            editable = false
        )

        assertEquals("返回", state.leadingLabel)
        assertEquals("工作号的未回消息", state.title)
        assertFalse(state.showUnreadDot)
        assertFalse(state.showEdit)
    }

    @Test
    fun ordinaryConversationShowsBackToAllUnreadAndEdit() {
        val state = floatingChatHeaderState(
            route = FloatingChatHeaderRoute.Conversation,
            accountName = "工作号",
            conversationTitle = "林晓晓",
            unreadCount = 3,
            messageScrollInProgress = false,
            editable = true
        )

        assertEquals("返回全部未回", state.leadingLabel)
        assertEquals("林晓晓", state.title)
        assertTrue(state.showUnreadDot)
        assertTrue(state.showEdit)
        assertFalse(state.compact)
    }

    @Test
    fun ordinaryConversationCompactsOnlyWhileMessageListScrolls() {
        val state = floatingChatHeaderState(
            route = FloatingChatHeaderRoute.Conversation,
            accountName = "工作号",
            conversationTitle = "林晓晓",
            unreadCount = 0,
            messageScrollInProgress = true,
            editable = true
        )

        assertEquals("返回全部未回", state.leadingLabel)
        assertTrue(state.showUnreadDot)
        assertTrue(state.compact)
    }
}
