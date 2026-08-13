package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
private val FloatingContentSideInset = 58.dp
private val EdgeGestureSafeInset = 8.dp
private const val ChatStatusBarHeightDp = 30
private const val ChatToolbarHeightDp = 42

internal fun chatStatusBarHeightDp(): Int = ChatStatusBarHeightDp

internal fun chatToolbarHeightDp(): Int = ChatToolbarHeightDp

internal fun chatContentTopInsetDp(): Int = 0

internal fun chatToolbarPaintsStatusBarBackground(): Boolean = true

internal fun chatToolbarUsesOpaqueSurface(): Boolean = OverlayTokens.toolbarSurface.alpha == 1f

internal fun chatToolbarUnreadBadgeLabel(unreadCount: Int): String? = when {
    unreadCount <= 0 -> null
    unreadCount > 99 -> "99+"
    else -> unreadCount.toString()
}

internal fun chatToolbarHasCloseButtonBeforeUnreadBadge(): Boolean = true

internal fun chatToolbarUnreadPrefixLabel(): String? = null

internal fun chatToolbarUnreadBadgeHeightDp(): Int = 18

internal fun chatToolbarUnreadBadgeHorizontalPaddingDp(): Int = 5

internal fun chatToolbarUnreadBadgeTextSizeSp(): Int = 10

internal fun chatToolbarHasSearchButton(): Boolean = true

internal fun chatToolbarWeightDistribution(): List<Int> = listOf(1, 2, 1)

private data class ChatSearchResult(
    val title: String,
    val preview: String,
    val time: String
)

// TODO(SCRM): replace this local preview list with the remote conversation/message search API.
private val PreviewChatSearchResults = listOf(
    ChatSearchResult("林晓晓", "晚点把客户资料发给你", "14:32"),
    ChatSearchResult("产品讨论群", "新的版本已经提交测试", "昨天"),
    ChatSearchResult("周明", "下周一上午方便开会吗？", "周一"),
    ChatSearchResult("售后支持群", "这个问题已经定位到设备连接", "周日")
)

internal fun chatSearchPreviewResultCount(): Int = PreviewChatSearchResults.size

internal fun chatToolbarTitle(
    conversation: FloatingChatConversation,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean
): String {
    if (homeOverviewVisible) return conversation.peerName.ifBlank { "消息" }
    return when (selectedThread) {
        ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull()?.name
            ?: conversation.peerName.ifBlank { "群聊" }
        is ChatThreadSelection.GroupChat -> conversation.groupContacts
            .firstOrNull { group -> group.id == selectedThread.groupId }
            ?.name
            ?: "群聊"
        is ChatThreadSelection.Private -> conversation.contacts
            .firstOrNull { contact -> contact.id == selectedThread.contactId }
            ?.name
            ?: conversation.peerName.ifBlank { "消息" }
    }
}

@Composable
internal fun CoordinateChatBody(
    conversation: FloatingChatConversation,
    homeOverviewConversations: List<AccountScopedConversation>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    navigationState: ChatNavigationState,
    activeAccountId: String,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    unreadThreadIds: Set<String>,
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
    onMessageClick: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    claimedPaymentMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onBlankAreaTap: () -> Unit,
    onCloseChat: () -> Unit,
    onScanClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    showTopToolbar: Boolean = true,
    onMessageScrollStateChanged: (Boolean) -> Unit = {},
    openSearchRequestKey: Int = 0,
    modifier: Modifier = Modifier
) {
    val connectorState = remember { ConnectorCoordinateState() }
    val density = LocalDensity.current
    val selectedAccount = remember(conversation, selectedThread, activeAccountId) {
        selectedAccountForCoordinateBody(
            conversation = conversation,
            selectedThread = selectedThread,
            activeAccountId = activeAccountId
        )
    }
    val homeUnreadSummaries = remember(
        homeOverviewConversations,
        conversation.homeUnreadDemoMessages,
        navigationState
    ) {
        if (shouldBuildAllAccountHomeOverview(homeOverviewVisible)) {
            navigationState.visibleUnreadSummaries(
                homeUnreadThreadSummaries(accountConversations = homeOverviewConversations) +
                    homeUnreadDemoThreadSummaries(conversation)
            )
        } else {
            emptyList()
        }
    }
    val homeUnreadSummaryByMessageId = remember(homeUnreadSummaries) {
        homeUnreadSummaries.associateBy { summary -> summary.message.id }
    }
    val homeUnreadAvatarContacts = remember(homeUnreadSummaries) {
        homeUnreadSummaries.map { summary -> summary.avatarContact }.distinctBy { contact -> contact.id }
    }
    val homeUnreadAccountIds = remember(homeUnreadSummaries) {
        homeUnreadSummaries.map { summary -> summary.accountId }.toSet()
    }
    val homeUnreadColorsByAccountId = remember(conversation.accountContacts) {
        homeOverviewAccountColorsById(conversation.accountContacts)
    }
    val homeUnreadAccountColors = remember(homeUnreadSummaries, homeUnreadColorsByAccountId) {
        homeUnreadSummaries.associate { summary ->
            summary.message.id to (homeUnreadColorsByAccountId[summary.accountId] ?: 0xFF00A6FB)
        }
    }
    val homeUnreadAccountIdsByMessageId = remember(homeUnreadSummaries) {
        homeUnreadSummaries.associate { summary -> summary.message.id to summary.accountId }
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
    LaunchedEffect(navigationState.route, homeOverviewVisible, visibleMessages) {
        Log.i(
            "UbikiChatData",
            "stage=rendered_messages " + homeUnreadRenderDiagnostics(
                route = navigationState.route,
                homeOverviewVisible = homeOverviewVisible,
                messages = visibleMessages
            )
        )
    }
    val homeOverviewConnectorGroupIds = remember(
        homeOverviewVisible,
        visibleMessages,
        homeUnreadAccountIdsByMessageId
    ) {
        if (homeOverviewVisible) {
            homeOverviewMessageGroups(visibleMessages, homeUnreadAccountIdsByMessageId)
                .flatMap { group -> group.messages.map { message -> message.id to group.connectorId } }
                .toMap()
        } else {
            emptyMap()
        }
    }
    val homeOverviewMessageGroups = remember(
        homeOverviewVisible,
        visibleMessages,
        homeUnreadAccountIdsByMessageId
    ) {
        if (homeOverviewVisible) {
            homeOverviewMessageGroups(visibleMessages, homeUnreadAccountIdsByMessageId)
        } else {
            emptyList()
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
        buildOffscreenConnectorIndex(
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
    var chatSearchVisible by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(openSearchRequestKey) {
        if (openSearchRequestKey > 0) {
            chatSearchQuery = ""
            chatSearchVisible = true
        }
    }
    LaunchedEffect(visibleMessageIds) {
        connectorState.retainMessageBounds(visibleMessageIds)
    }
    LaunchedEffect(messageListState.isScrollInProgress) {
        onMessageScrollStateChanged(messageListState.isScrollInProgress)
    }
    LaunchedEffect(viewportKey, visibleMessages.size) {
        if (!homeOverviewVisible && visibleMessages.size > viewportTracker.messageCount && visibleMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(visibleMessages.lastIndex)
        }
        viewportTracker.messageCount = visibleMessages.size
    }
    LaunchedEffect(viewportKey, inputFocused, visibleMessages.size) {
        if (inputFocused && visibleMessages.isNotEmpty()) {
            messageListState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        val toolbarTitle = remember(conversation, selectedThread, homeOverviewVisible) {
            chatToolbarTitle(
                conversation = conversation,
                selectedThread = selectedThread,
                homeOverviewVisible = homeOverviewVisible
            )
        }
        val toolbarEditableContact = remember(conversation.contacts, selectedThread) {
            when (selectedThread) {
                is ChatThreadSelection.Private -> conversation.contacts
                    .firstOrNull { contact -> contact.id == selectedThread.contactId }
                else -> null
            }
        }
        val toolbarEditableGroup = remember(conversation.groupContacts, selectedThread) {
            when (selectedThread) {
                ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull()
                is ChatThreadSelection.GroupChat -> conversation.groupContacts
                    .firstOrNull { group -> group.id == selectedThread.groupId }
                is ChatThreadSelection.Private -> null
            }
        }
        if (showTopToolbar) {
            ChatTopToolbar(
                title = toolbarTitle,
                accountName = selectedAccount.name,
                hasUnreadIndicator = unreadThreadIds.isNotEmpty(),
                onNavigationClick = onCloseChat,
                onEditClick = {
                    toolbarEditableContact?.let(onContactAvatarLongClick)
                        ?: toolbarEditableGroup?.let(onGroupAvatarLongClick)
                },
                onScanClick = onScanClick,
                onAddFriendClick = onAddFriendClick,
                onSearchClick = {
                    chatSearchQuery = ""
                    chatSearchVisible = true
                }
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (homeOverviewVisible && visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .padding(
                            start = FloatingContentSideInset + EdgeGestureSafeInset,
                            end = FloatingContentSideInset + EdgeGestureSafeInset
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无未回消息",
                        color = OverlayTokens.panelSecondaryText,
                        fontSize = 14.sp
                    )
                }
            } else MessageCoordinatePane(
                messages = visibleMessages,
                selectedThread = selectedThread,
                homeOverviewVisible = homeOverviewVisible,
                contactsById = contactsById,
                homeOverviewAccountColors = homeUnreadAccountColors,
                homeOverviewAccountIdsByMessageId = homeUnreadAccountIdsByMessageId,
                homeOverviewMessageGroups = homeOverviewMessageGroups,
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
                    onBlankAreaTap()
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
                    } else {
                        onMessageClick(message)
                    }
                },
                onBlankAreaTap = onBlankAreaTap,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(
                        start = FloatingContentSideInset + EdgeGestureSafeInset,
                        end = FloatingContentSideInset + EdgeGestureSafeInset
                    )
            )
        if (!homeOverviewVisible) ChatSessionRail(
            groups = conversation.groupContacts,
            contacts = conversation.contacts,
            conversation = conversation,
            selectedAccountId = selectedAccount.id,
            selectedThread = selectedThread,
            unreadThreadIds = unreadThreadIds,
            onThreadSelected = onThreadSelected,
            onGroupAvatarLongClick = onGroupAvatarLongClick,
            onContactAvatarLongClick = onContactAvatarLongClick,
            onToolAction = onToolAction,
            connectorState = connectorState,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(leftRailFollowTextLayerWidthDp().dp)
            .zIndex(leftRailLayerZIndex())
        )
        if (homeOverviewVisible) HomeOverviewAvatarRail(
            groups = homeOverviewMessageGroups,
            contactsById = contactsById,
            listState = messageListState,
            connectorState = connectorState,
            onAvatarLongClick = onContactAvatarLongClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(leftRailTouchableWidthDp().dp)
                .zIndex(leftRailLayerZIndex())
        )
        RightCoordinateRail(
            accounts = conversation.accountContacts,
            accountProfiles = accountProfiles,
            selectedAccountId = selectedAccount.id,
            actions = conversation.toolActions,
            highlightedAccountColors = if (homeOverviewVisible) {
                homeUnreadColorsByAccountId.filterKeys(homeUnreadAccountIds::contains)
            } else {
                emptyMap()
            },
            connectorState = connectorState,
            onToolAction = onToolAction,
            onAccountAvatarClick = onAccountAvatarClick,
            onAccountAvatarLongClick = onAccountAvatarLongClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(rightRailWidthDp().dp)
                .zIndex(leftRailLayerZIndex())
        )
        ChatConnectorLayer(
            messages = visibleMessages,
            selection = selectedThread,
            selectedAccountId = selectedAccount.id,
            homeOverviewVisible = homeOverviewVisible,
            homeOverviewConnectorGroupIds = homeOverviewConnectorGroupIds,
            homeOverviewMessageGroups = homeOverviewMessageGroups,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible,
            listState = messageListState,
            offscreenIndex = offscreenConnectorIndex,
            connectorState = connectorState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(connectorLayerZIndex())
        )
        }
        if (chatSearchVisible) {
            ChatSearchPanel(
                query = chatSearchQuery,
                onQueryChange = { chatSearchQuery = it },
                onClose = { chatSearchVisible = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showTopToolbar) ChatStatusBarHeightDp.dp else 0.dp)
                    .zIndex(40f)
            )
        }
    }
}
}

@Composable
private fun ChatTopToolbar(
    title: String,
    accountName: String,
    hasUnreadIndicator: Boolean,
    onNavigationClick: () -> Unit,
    onEditClick: () -> Unit,
    onScanClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var utilityMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((ChatStatusBarHeightDp + ChatToolbarHeightDp).dp)
            .background(OverlayTokens.toolbarSurface)
            .padding(
                start = 8.dp,
                top = ChatStatusBarHeightDp.dp,
                end = 8.dp
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier.width(36.dp).height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = OverlayTokens.panelPrimaryText,
                        modifier = Modifier.width(20.dp).height(20.dp)
                    )
                }
                if (hasUnreadIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 4.dp)
                            .width(8.dp)
                            .height(8.dp)
                            .background(OverlayTokens.accent, CircleShape)
                    )
                }
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = OverlayTokens.panelPrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.width(30.dp).height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "编辑会话",
                        tint = OverlayTokens.panelSecondaryText,
                        modifier = Modifier.width(16.dp).height(16.dp)
                    )
                }
            }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { utilityMenuExpanded = true },
                    modifier = Modifier.width(30.dp).height(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "扫一扫与加人",
                            tint = OverlayTokens.panelPrimaryText,
                            modifier = Modifier.width(18.dp).height(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.PersonAddAlt1,
                            contentDescription = null,
                            tint = OverlayTokens.accent,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .width(10.dp)
                                .height(10.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = utilityMenuExpanded,
                    onDismissRequest = { utilityMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("扫一扫") },
                        onClick = {
                            utilityMenuExpanded = false
                            onScanClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加朋友") },
                        onClick = {
                            utilityMenuExpanded = false
                            onAddFriendClick()
                        }
                    )
                }
                Text(
                    text = accountName,
                    modifier = Modifier
                        .width(72.dp)
                        .padding(horizontal = 2.dp),
                    color = OverlayTokens.panelSecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.width(30.dp).height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索聊天记录",
                        tint = OverlayTokens.panelPrimaryText,
                        modifier = Modifier.width(18.dp).height(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val results = remember(query) {
        if (query.isBlank()) PreviewChatSearchResults
        else PreviewChatSearchResults.filter { result ->
            result.title.contains(query, ignoreCase = true) ||
                result.preview.contains(query, ignoreCase = true)
        }
    }
    androidx.compose.foundation.layout.Column(
        modifier = modifier.background(OverlayTokens.panel)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回聊天",
                    tint = OverlayTokens.panelPrimaryText
                )
            }
            Text(
                text = "搜索聊天记录",
                color = OverlayTokens.panelPrimaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = OverlayTokens.panelSecondaryText
                )
            },
            placeholder = { Text("搜索联系人、群聊或消息") }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
        ) {
            items(results) { result ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.title,
                            modifier = Modifier.weight(1f),
                            color = OverlayTokens.panelPrimaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = result.time,
                            color = OverlayTokens.panelSecondaryText,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = result.preview,
                        color = OverlayTokens.panelSecondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private class MessageListViewportTracker(
    var viewportKey: MessageListViewportKey,
    var messageCount: Int
)
