package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatNavigationActions
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.HomeUnreadThreadSummary
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatNavigationActionsTest {
    @Test
    fun openingChatThreadDoesNotClearUnrepliedRecords() {
        val firstThread = ChatThreadSelection.Private("account-a__contact-a")
        val secondThread = ChatThreadSelection.Private("account-a__contact-b")
        val unreadThreadIds = linkedMapOf(
            firstThread.toLocalThreadId() to true,
            secondThread.toLocalThreadId() to true
        )
        val actions = ChatNavigationActions(
            unreadThreadIds = unreadThreadIds,
            onActiveAccountIdChanged = {},
            onSelectedThreadChanged = {},
            onHomeOverviewVisibleChanged = {}
        )

        actions.openChatThread(firstThread)

        assertEquals(
            setOf(firstThread.toLocalThreadId(), secondThread.toLocalThreadId()),
            unreadThreadIds.keys
        )
    }

    @Test
    fun openingHomeUnreadDoesNotClearUnrepliedRecords() {
        val selection = ChatThreadSelection.Private("account-a__contact-a")
        val threadId = selection.toLocalThreadId()
        val unreadThreadIds = linkedMapOf(threadId to true)
        val message = FloatingChatMessage(
            id = "message-a",
            type = FloatingChatMessageType.Text,
            text = "请回复",
            fromMe = false,
            senderName = "客户",
            time = "10:00",
            threadContactId = "account-a__contact-a"
        )
        val summary = HomeUnreadThreadSummary(
            accountId = "account-a",
            threadId = threadId,
            selection = selection,
            unreadCount = 1,
            avatarContact = FloatingChatContact(
                id = "account-a__contact-a",
                name = "客户",
                initials = "客",
                description = "客户",
                avatarColor = 0xFF1B9AAA
            ),
            message = message,
            unrepliedMessages = listOf(message)
        )
        val actions = ChatNavigationActions(
            unreadThreadIds = unreadThreadIds,
            onActiveAccountIdChanged = {},
            onSelectedThreadChanged = {},
            onHomeOverviewVisibleChanged = {}
        )

        actions.openHomeUnread(summary)

        assertEquals(setOf(threadId), unreadThreadIds.keys)
    }
}
