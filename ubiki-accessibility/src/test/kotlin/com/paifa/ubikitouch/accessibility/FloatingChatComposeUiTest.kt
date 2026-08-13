package com.paifa.ubikitouch.accessibility

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.paifa.ubikitouch.accessibility.floatingchat.chat.AccountScopedConversation
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatNavigationRoute
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatNavigationState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.CoordinateChatBody
import com.paifa.ubikitouch.accessibility.floatingchat.chat.homeUnreadThreadSummaries
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingChatComposeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 60_000)
    fun headerLeadingControlInvokesBack() {
        var backCalls = 0
        composeRule.setContent {
            FloatingChatWorkspaceHeader(
                state = floatingChatHeaderState(
                    route = FloatingChatHeaderRoute.AllAccountsUnread,
                    accountName = TestAccount.name,
                    conversationTitle = "",
                    unreadCount = 0,
                    messageScrollInProgress = false,
                    editable = false
                ),
                accountName = TestAccount.name,
                onLeadingClick = { backCalls += 1 },
                onEditClick = null,
                onSearchClick = {},
                onScanClick = {},
                onAddFriendClick = {}
            )
        }

        composeRule.onNode(hasContentDescription("返回") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test(timeout = 60_000)
    fun accountAvatarTouchSelectsUnreadAccount() {
        var selectedAccountId: String? = null
        setUnreadBody(
            conversation = testConversation(messages = emptyList()),
            accountConversations = emptyList(),
            onAccountAvatarClick = { account -> selectedAccountId = account.id }
        )

        composeRule.onNodeWithText("Q", useUnmergedTree = true)
            .performTouchInput { click() }

        composeRule.runOnIdle { assertEquals(TestAccount.id, selectedAccountId) }
    }

    @Test(timeout = 60_000)
    fun unreadMessageTouchOpensOwningConversation() {
        var selectedThread: ChatThreadSelection? = null
        val conversation = testConversation(messages = listOf(TestUnreadMessage))
        setUnreadBody(
            conversation = conversation,
            accountConversations = listOf(AccountScopedConversation(TestAccount.id, conversation)),
            onHomeUnreadSelected = { summary -> selectedThread = summary.selection }
        )

        composeRule.onNodeWithText(TestUnreadMessage.text).performClick()

        composeRule.runOnIdle {
            assertEquals(ChatThreadSelection.Private(TestContact.id), selectedThread)
        }
    }

    @Test(timeout = 60_000)
    fun handledUnreadScopeRendersEmptyState() {
        val conversation = testConversation(messages = listOf(TestUnreadMessage))
        val accountConversations = listOf(AccountScopedConversation(TestAccount.id, conversation))
        val handledSummary = homeUnreadThreadSummaries(accountConversations).single()
        val navigationState = ChatNavigationState(
            route = ChatNavigationRoute.AllAccountsUnread,
            activeAccountId = TestAccount.id,
            selectedThread = ChatThreadSelection.Private(TestContact.id)
        ).markHandled(handledSummary)

        setUnreadBody(
            conversation = conversation,
            accountConversations = accountConversations,
            navigationState = navigationState
        )

        composeRule.onNodeWithText("暂无未回消息").assertIsDisplayed()
    }

    private fun setUnreadBody(
        conversation: FloatingChatConversation,
        accountConversations: List<AccountScopedConversation>,
        navigationState: ChatNavigationState = ChatNavigationState(
            route = ChatNavigationRoute.AllAccountsUnread,
            activeAccountId = TestAccount.id,
            selectedThread = ChatThreadSelection.Private(TestContact.id)
        ),
        onHomeUnreadSelected: (com.paifa.ubikitouch.accessibility.floatingchat.chat.HomeUnreadThreadSummary) -> Unit = {},
        onAccountAvatarClick: (FloatingChatContact) -> Unit = {}
    ) {
        composeRule.setContent {
            CoordinateChatBody(
                conversation = conversation,
                homeOverviewConversations = accountConversations,
                accountProfiles = emptyMap(),
                navigationState = navigationState,
                activeAccountId = TestAccount.id,
                selectedThread = navigationState.selectedThread,
                homeOverviewVisible = true,
                unreadThreadIds = emptySet(),
                inputFocused = false,
                groupMemberAvatarsVisible = false,
                onThreadSelected = {},
                onHomeUnreadSelected = onHomeUnreadSelected,
                onToolAction = {},
                onGroupAvatarLongClick = {},
                onContactAvatarLongClick = {},
                onAccountAvatarClick = onAccountAvatarClick,
                onAccountAvatarLongClick = {},
                onPreviewMedia = {},
                onPreviewDocument = {},
                onOpenMediaActions = {},
                onPaymentCardClick = {},
                onChatHistoryClick = {},
                onAiDraftClick = {},
                onMessageClick = {},
                onLongPressMessage = { _, _ -> },
                multiSelectMode = false,
                selectedMessageIds = emptyMap(),
                remindedMessageIds = emptyMap(),
                favoriteMessageIds = emptyMap(),
                claimedPaymentMessageIds = emptyMap(),
                onToggleMessageSelection = {},
                onBlankAreaTap = {},
                onCloseChat = {},
                onScanClick = {},
                onAddFriendClick = {},
                showTopToolbar = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun testConversation(messages: List<FloatingChatMessage>) = FloatingChatConversation(
        peerName = TestContact.name,
        accountName = TestAccount.name,
        contacts = listOf(TestContact),
        accountContacts = listOf(TestAccount),
        messages = messages,
        toolActions = emptyList()
    )

    private companion object {
        val TestAccount = FloatingChatContact(
            id = "account-q",
            name = "Q Account",
            initials = "Q",
            description = "Test account",
            avatarColor = 0xFF3A86FF,
            selected = true
        )
        val TestContact = FloatingChatContact(
            id = "contact-a",
            name = "Contact A",
            initials = "C",
            description = "Test contact",
            avatarColor = 0xFF1B9AAA
        )
        val TestUnreadMessage = FloatingChatMessage(
            id = "inbound-v1",
            type = FloatingChatMessageType.Text,
            text = "Need reply",
            fromMe = false,
            senderName = TestContact.name,
            time = "10:00",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = TestContact.id,
            threadContactId = TestContact.id
        )
    }
}
