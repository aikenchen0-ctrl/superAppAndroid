package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageRow
import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarRole
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactAvatar
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListBottomClearanceDp
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListReusableContentType
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageCoordinatePane(
    messages: List<FloatingChatMessage>,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    homeOverviewAccountColors: Map<String, Long>,
    homeOverviewAccountIdsByMessageId: Map<String, String>,
    homeOverviewMessageGroups: List<HomeOverviewMessageGroup>,
    homeOverviewSummariesByMessageId: Map<String, HomeUnreadThreadSummary>,
    homeOverviewAccountContacts: Map<String, FloatingChatContact>,
    unrepliedRecipientIndicators: UnrepliedRecipientIndicators,
    unrepliedOverviewState: UnrepliedOverviewState,
    onUnrepliedOverviewStateChanged: (UnrepliedOverviewState) -> Unit,
    onSendUnrepliedDraft: (HomeUnreadThreadSummary, String) -> Unit,
    groupMemberAvatarsVisible: Boolean,
    listState: LazyListState,
    connectorState: ConnectorCoordinateState,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    onGroupMemberAvatarLongClick: (FloatingChatContact) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    claimedPaymentMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onMessageClick: (FloatingChatMessage) -> Unit,
    onBlankAreaTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageIndexes = remember(messages) {
        messages.withIndex().associate { (index, message) -> message.id to index }
    }
    Box(
        modifier = modifier
            .padding(horizontal = messagePaneHorizontalPaddingDp().dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBlankAreaTap
            )
            .onGloballyPositioned { coordinates ->
                connectorState.updateMessageViewport(coordinates.boundsInRoot())
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = messageListBottomClearanceDp().dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (homeOverviewVisible) {
                itemsIndexed(
                    items = homeOverviewMessageGroups,
                    key = { _, group -> group.key },
                    contentType = { _, _ -> "home-overview-message-group" }
                ) { _, group ->
                    HomeOverviewMessageGroupRow(
                        group = group,
                        messageIndexes = messageIndexes,
                        selectedThread = selectedThread,
                        contactsById = contactsById,
                        homeOverviewAccountColors = homeOverviewAccountColors,
                        homeOverviewAccountIdsByMessageId = homeOverviewAccountIdsByMessageId,
                        homeOverviewAccountContacts = homeOverviewAccountContacts,
                        unrepliedRecipientIndicators = unrepliedRecipientIndicators,
                        summary = group.messages.firstOrNull()?.id?.let(homeOverviewSummariesByMessageId::get),
                        unrepliedOverviewState = unrepliedOverviewState,
                        onUnrepliedOverviewStateChanged = onUnrepliedOverviewStateChanged,
                        onSendUnrepliedDraft = onSendUnrepliedDraft,
                        groupMemberAvatarsVisible = groupMemberAvatarsVisible,
                        onPreviewMedia = onPreviewMedia,
                        onOpenMediaActions = onOpenMediaActions,
                        onLongPressMessage = onLongPressMessage,
                        onGroupMemberAvatarLongClick = onGroupMemberAvatarLongClick,
                        multiSelectMode = multiSelectMode,
                        selectedMessageIds = selectedMessageIds,
                        remindedMessageIds = remindedMessageIds,
                        favoriteMessageIds = favoriteMessageIds,
                        claimedPaymentMessageIds = claimedPaymentMessageIds,
                        onToggleMessageSelection = onToggleMessageSelection,
                        onMessageClick = onMessageClick,
                        connectorState = connectorState
                    )
                }
            } else {
                itemsIndexed(
                    items = messages,
                    key = { _, message -> message.id },
                    contentType = { _, message -> messageListReusableContentType(message.type) }
                ) { index, message ->
                    MessageRow(
                    message = message,
                    index = index,
                    selectedThread = selectedThread,
                    homeOverviewVisible = homeOverviewVisible,
                    contactsById = contactsById,
                    homeOverviewAccountColor = homeOverviewAccountColors[message.id],
                    homeOverviewAccountContact = null,
                    unrepliedRecipientIndicators = unrepliedRecipientIndicators,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible,
                    onPreviewMedia = onPreviewMedia,
                    onOpenMediaActions = onOpenMediaActions,
                    onLongPressMessage = onLongPressMessage,
                    onGroupMemberAvatarLongClick = onGroupMemberAvatarLongClick,
                    multiSelectMode = multiSelectMode,
                    selected = selectedMessageIds[message.id] == true,
                    reminded = remindedMessageIds[message.id] == true,
                    favorite = favoriteMessageIds[message.id] == true,
                    claimed = claimedPaymentMessageIds[message.id] == true,
                    onToggleSelection = { onToggleMessageSelection(message) },
                    onClick = { onMessageClick(message) },
                    onBubbleBoundsChanged = { bounds ->
                        connectorState.updateMessageBubble(message.id, bounds)
                    },
                    onGroupMemberAvatarBoundsChanged = { bounds ->
                        connectorState.updateGroupMemberAvatar(message.id, bounds)
                    },
                    onGroupMemberAvatarRemoved = {
                        connectorState.removeGroupMemberAvatar(message.id)
                    }
                    )
                }
            }
        }
        if (homeOverviewVisible) {
            unrepliedOverviewEmptyText(homeOverviewMessageGroups)?.let { emptyText ->
                Text(
                    text = emptyText,
                    color = OverlayTokens.secondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        if (homeOverviewVisible && unrepliedOverviewState.draftMode == UnrepliedDraftMode.Bottom) {
            val selectedSummary = homeOverviewSummariesByMessageId.values
                .distinctBy { summary -> summary.itemId }
                .firstOrNull { summary -> summary.itemId == unrepliedOverviewState.selectedItemId }
            selectedSummary?.let { summary ->
                val draftKey = UnrepliedDraftKey(summary.accountId, summary.threadId)
                val draftText = unrepliedOverviewState.drafts[draftKey]
                    ?: summary.suggestedDraftText.orEmpty()
                UnrepliedBottomDraft(
                    text = draftText,
                    onTextChanged = { text ->
                        onUnrepliedOverviewStateChanged(unrepliedOverviewState.updateDraft(draftKey, text))
                    },
                    onSend = { onSendUnrepliedDraft(summary, draftText) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 8.dp, end = 8.dp, bottom = 76.dp)
                        .zIndex(8f)
                )
            }
        }
    }
}

@Composable
private fun HomeOverviewMessageGroupRow(
    group: HomeOverviewMessageGroup,
    messageIndexes: Map<String, Int>,
    selectedThread: ChatThreadSelection,
    contactsById: Map<String, FloatingChatContact>,
    homeOverviewAccountColors: Map<String, Long>,
    homeOverviewAccountIdsByMessageId: Map<String, String>,
    homeOverviewAccountContacts: Map<String, FloatingChatContact>,
    unrepliedRecipientIndicators: UnrepliedRecipientIndicators,
    summary: HomeUnreadThreadSummary?,
    unrepliedOverviewState: UnrepliedOverviewState,
    onUnrepliedOverviewStateChanged: (UnrepliedOverviewState) -> Unit,
    onSendUnrepliedDraft: (HomeUnreadThreadSummary, String) -> Unit,
    groupMemberAvatarsVisible: Boolean,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    onGroupMemberAvatarLongClick: (FloatingChatContact) -> Unit,
    multiSelectMode: Boolean,
    selectedMessageIds: Map<String, Boolean>,
    remindedMessageIds: Map<String, Boolean>,
    favoriteMessageIds: Map<String, Boolean>,
    claimedPaymentMessageIds: Map<String, Boolean>,
    onToggleMessageSelection: (FloatingChatMessage) -> Unit,
    onMessageClick: (FloatingChatMessage) -> Unit,
    connectorState: ConnectorCoordinateState
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        group.messages.forEachIndexed { offset, message ->
            key(message.id) {
                MessageRow(
                    message = message,
                    index = messageIndexes[message.id] ?: offset,
                    selectedThread = selectedThread,
                    homeOverviewVisible = true,
                    showAttachedAvatar = false,
                    contactsById = contactsById,
                    homeOverviewAccountColor = homeOverviewAccountColors[message.id],
                    homeOverviewAccountContact = homeOverviewAccountIdsByMessageId[message.id]
                        ?.let(homeOverviewAccountContacts::get),
                    unrepliedRecipientIndicators = unrepliedRecipientIndicators,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible,
                    onPreviewMedia = onPreviewMedia,
                    onOpenMediaActions = onOpenMediaActions,
                    onLongPressMessage = onLongPressMessage,
                    onGroupMemberAvatarLongClick = onGroupMemberAvatarLongClick,
                    multiSelectMode = multiSelectMode,
                    selected = selectedMessageIds[message.id] == true,
                    reminded = remindedMessageIds[message.id] == true,
                    favorite = favoriteMessageIds[message.id] == true,
                    claimed = claimedPaymentMessageIds[message.id] == true,
                    onToggleSelection = { onToggleMessageSelection(message) },
                    onClick = { onMessageClick(message) },
                    onBubbleBoundsChanged = { bounds -> connectorState.updateMessageBubble(message.id, bounds) },
                    onGroupMemberAvatarBoundsChanged = {},
                    onGroupMemberAvatarRemoved = {}
                )
            }
        }
        summary?.let { item ->
            val draftKey = UnrepliedDraftKey(item.accountId, item.threadId)
            val draftText = unrepliedOverviewState.drafts[draftKey]
                ?: item.suggestedDraftText.orEmpty()
            when (unrepliedOverviewState.draftMode) {
                UnrepliedDraftMode.Inline -> UnrepliedInlineDraft(
                    text = draftText,
                    onTextChanged = { text ->
                        onUnrepliedOverviewStateChanged(unrepliedOverviewState.updateDraft(draftKey, text))
                    },
                    onSend = { onSendUnrepliedDraft(item, draftText) }
                )
                UnrepliedDraftMode.Bottom -> SelectUnrepliedBottomDraftButton(
                    selected = unrepliedOverviewState.selectedItemId == item.itemId,
                    onClick = {
                        onUnrepliedOverviewStateChanged(
                            unrepliedOverviewState.copy(selectedItemId = item.itemId)
                        )
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
internal fun HomeOverviewAvatarRail(
    groups: List<HomeOverviewMessageGroup>,
    contactsById: Map<String, FloatingChatContact>,
    listState: LazyListState,
    connectorState: ConnectorCoordinateState,
    onAvatarLongClick: (FloatingChatContact) -> Unit,
    modifier: Modifier = Modifier
) {
    var railRootTopPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    @Suppress("UNUSED_VARIABLE")
    val connectorVersion = connectorState.version
    val visibleGroups = homeOverviewVisibleGroups(
        groups = groups,
        visibleGroupIndexes = listState.layoutInfo.visibleItemsInfo.map { item -> item.index }.toSet()
    )

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            railRootTopPx = coordinates.positionInRoot().y
        }
    ) {
        visibleGroups.forEach { group ->
            val contact = group.avatarContactId?.let(contactsById::get) ?: return@forEach
            val messageBounds = group.messages
                .asSequence()
                .mapNotNull { message -> connectorState.messageBubbles[message.id] }
                .firstOrNull()
                ?: return@forEach
            val offsetY = with(density) { (messageBounds.top - railRootTopPx).toDp() }
            CompactAvatar(
                contact = contact,
                role = AvatarRole.Session,
                sizeDp = homeOverviewAvatarSizeDp(),
                onClick = {},
                onLongClick = { onAvatarLongClick(contact) },
                onBoundsChanged = { bounds -> connectorState.updateHomeOverviewAvatar(group.connectorId, bounds) },
                onRemoved = { connectorState.removeHomeOverviewAvatar(group.connectorId) },
                modifier = Modifier
                    .padding(start = with(density) { leftRailAvatarScreenEdgeInsetPx().toDp() })
                    .offset(y = offsetY)
                    .zIndex(leftRailLayerZIndex())
            )
        }
    }
}
