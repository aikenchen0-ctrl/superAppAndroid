package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageListViewportKey
import com.paifa.ubikitouch.accessibility.floatingchat.message.isPaymentCardMessage
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListInitialFirstVisibleItemIndex
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListViewportKey
import com.paifa.ubikitouch.accessibility.floatingchat.message.shouldRetargetMessageList
import com.paifa.ubikitouch.accessibility.floatingchat.tools.RightCoordinateRail
import com.paifa.ubikitouch.accessibility.floatingchat.tools.rightRailWidthDp
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
private val FloatingContentSideInset = 58.dp
private val EdgeGestureSafeInset = 8.dp
@Composable
internal fun CoordinateChatBody(
    conversation: FloatingChatConversation,
    homeOverviewConversations: List<AccountScopedConversation>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    activeAccountId: String,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    unreadThreadIds: Set<String>,
    inputText: String,
    inputFocused: Boolean,
    groupMemberAvatarsVisible: Boolean,
    onThreadSelected: (ChatThreadSelection) -> Unit,
    onHomeUnreadSelected: (HomeUnreadThreadSummary) -> Unit,
    onToolAction: (FloatingChatToolAction) -> Unit,
    onGroupAvatarLongClick: (FloatingChatContact) -> Unit,
    onContactAvatarLongClick: (FloatingChatContact) -> Unit,
    onAccountAvatarClick: (FloatingChatContact) -> Unit,
    onAccountAvatarLongClick: (FloatingChatContact) -> Unit,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onPreviewDocument: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onPaymentCardClick: (FloatingChatMessage) -> Unit,
    onChatHistoryClick: (FloatingChatMessage) -> Unit,
    onAiDraftClick: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    claimedPaymentMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onBlankAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectorState = remember { ConnectorCoordinateState() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val selectedAccount = remember(conversation, selectedThread, activeAccountId) {
        selectedAccountForCoordinateBody(
            conversation = conversation,
            selectedThread = selectedThread,
            activeAccountId = activeAccountId
        )
    }
    val homeUnreadSummaries = remember(homeOverviewConversations) {
        if (shouldBuildAllAccountHomeOverview(homeOverviewVisible)) {
            homeUnreadThreadSummaries(
                accountConversations = homeOverviewConversations
            )
        } else {
            emptyList()
        }
    }
    val homeUnreadSummaryByMessageId = remember(homeUnreadSummaries) {
        homeUnreadSummaries.associateBy { summary -> summary.message.id }
    }
    val threadMessages = remember(conversation, selectedThread, selectedAccount.id) {
        visibleMessagesForThread(
            conversation = conversation,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id
        )
    }
    val visibleMessages = remember(homeOverviewVisible, homeUnreadSummaries, threadMessages) {
        if (homeOverviewVisible) {
            homeUnreadSummaries.map { summary -> summary.message }
        } else {
            threadMessages
        }
    }
    val viewportKey = remember(selectedThread, selectedAccount.id, homeOverviewVisible) {
        messageListViewportKey(
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible
        )
    }
    val messageListState = rememberLazyListState(
        initialFirstVisibleItemIndex = messageListInitialFirstVisibleItemIndex(
            messageCount = visibleMessages.size,
            homeOverviewVisible = homeOverviewVisible
        )
    )
    val viewportTracker = remember {
        MessageListViewportTracker(viewportKey, visibleMessages.size)
    }
    if (shouldRetargetMessageList(viewportTracker.viewportKey, viewportKey)) {
        messageListState.requestScrollToItem(
            index = messageListInitialFirstVisibleItemIndex(
                messageCount = visibleMessages.size,
                homeOverviewVisible = homeOverviewVisible
            )
        )
        viewportTracker.viewportKey = viewportKey
        viewportTracker.messageCount = visibleMessages.size
    }
    val visibleMessageIds = remember(visibleMessages) {
        visibleMessages.map { message -> message.id }.toSet()
    }
    val offscreenConnectorIndex = remember(
        visibleMessages,
        homeOverviewVisible,
        selectedThread,
        selectedAccount.id,
        groupMemberAvatarsVisible
    ) {
        ConnectorOffscreenIndex.fromMessages(
            messages = visibleMessages,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible
        )
    }
    val contactsById = remember(conversation.groupContacts, conversation.contacts) {
        (conversation.groupContacts + conversation.contacts).associateBy { contact -> contact.id }
    }
    LaunchedEffect(visibleMessageIds) {
        connectorState.retainMessageBounds(visibleMessageIds)
    }
    LaunchedEffect(viewportKey, visibleMessages.size) {
        if (!homeOverviewVisible && visibleMessages.size > viewportTracker.messageCount && visibleMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(visibleMessages.lastIndex)
        }
        viewportTracker.messageCount = visibleMessages.size
    }
    LaunchedEffect(viewportKey, inputFocused, inputText, imeBottomPx, visibleMessages.size) {
        if (inputFocused && visibleMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        MessageCoordinatePane(
            messages = visibleMessages,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            contactsById = contactsById,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible,
            listState = messageListState,
            connectorState = connectorState,
            onPreviewMedia = onPreviewMedia,
            onOpenMediaActions = onOpenMediaActions,
            onLongPressMessage = onLongPressMessage,
            onGroupMemberAvatarLongClick = onContactAvatarLongClick,
            multiSelectMode = multiSelectMode,
            selectedMessageIds = selectedMessageIds,
            remindedMessageIds = remindedMessageIds,
            favoriteMessageIds = favoriteMessageIds,
            claimedPaymentMessageIds = claimedPaymentMessageIds,
            onToggleMessageSelection = onToggleMessageSelection,
            onMessageClick = { message ->
                if (homeOverviewVisible) {
                    homeUnreadSummaryByMessageId[message.id]?.let(onHomeUnreadSelected)
                } else if (message.kind == FloatingChatMessageKind.AiDraft) {
                    onAiDraftClick(message)
                } else if (message.isPaymentCardMessage()) {
                    onPaymentCardClick(message)
                } else if (message.type == FloatingChatMessageType.FilePreview) {
                    onPreviewDocument(message)
                } else if (message.type == FloatingChatMessageType.ChatHistory) {
                    onChatHistoryClick(message)
                }
            },
            onBlankAreaTap = onBlankAreaTap,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .imePadding()
                .padding(
                    start = FloatingContentSideInset + EdgeGestureSafeInset,
                    end = FloatingContentSideInset + EdgeGestureSafeInset
                )
        )
        ChatSessionRail(
            groups = conversation.groupContacts,
            contacts = conversation.contacts,
            conversation = conversation,
            selectedAccountId = selectedAccount.id,
            selectedThread = selectedThread,
            unreadThreadIds = unreadThreadIds,
            onThreadSelected = onThreadSelected,
            onGroupAvatarLongClick = onGroupAvatarLongClick,
            onContactAvatarLongClick = onContactAvatarLongClick,
            connectorState = connectorState,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(leftRailFollowTextLayerWidthDp().dp)
                .zIndex(leftRailLayerZIndex())
        )
        RightCoordinateRail(
            accounts = conversation.accountContacts,
            accountProfiles = accountProfiles,
            selectedAccountId = selectedAccount.id,
            actions = conversation.toolActions,
            connectorState = connectorState,
            onToolAction = onToolAction,
            onAccountAvatarClick = onAccountAvatarClick,
            onAccountAvatarLongClick = onAccountAvatarLongClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(rightRailWidthDp().dp)
        )
        ChatConnectorLayer(
            messages = visibleMessages,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible,
            listState = messageListState,
            offscreenIndex = offscreenConnectorIndex,
            connectorState = connectorState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(connectorLayerZIndex())
        )
    }
}

private class MessageListViewportTracker(
    var viewportKey: MessageListViewportKey,
    var messageCount: Int
)
