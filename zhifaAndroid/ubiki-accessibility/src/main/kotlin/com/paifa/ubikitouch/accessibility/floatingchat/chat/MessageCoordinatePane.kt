package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageRow
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListBottomClearanceDp
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListReusableContentType
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageCoordinatePane(
    messages: List<FloatingChatMessage>,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
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
}
