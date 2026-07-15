package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.AvatarRole
import com.paifa.ubikitouch.accessibility.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.CompactAvatar
import com.paifa.ubikitouch.accessibility.MessageBlock
import com.paifa.ubikitouch.accessibility.MessageSelectionToggle
import com.paifa.ubikitouch.accessibility.groupMemberAvatarBubbleCenterOffsetDp
import com.paifa.ubikitouch.accessibility.groupMemberAvatarSizeDp
import com.paifa.ubikitouch.accessibility.groupMemberContactForMessage
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

@Composable
internal fun MessageRow(
    message: FloatingChatMessage,
    index: Int,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    groupMemberAvatarsVisible: Boolean,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    onGroupMemberAvatarLongClick: (FloatingChatContact) -> Unit,
    multiSelectMode: Boolean,
    selected: Boolean,
    reminded: Boolean,
    favorite: Boolean,
    claimed: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onBubbleBoundsChanged: (Rect) -> Unit,
    onGroupMemberAvatarBoundsChanged: (Rect) -> Unit,
    onGroupMemberAvatarRemoved: () -> Unit
) {
    val groupMemberContact = remember(
        message,
        selectedThread,
        homeOverviewVisible,
        contactsById,
        groupMemberAvatarsVisible
    ) {
        groupMemberContactForMessage(
            message = message,
            selectedThread = selectedThread,
            homeOverviewVisible = homeOverviewVisible,
            contactsById = contactsById,
            groupMemberAvatarsVisible = groupMemberAvatarsVisible
        )
    }
    val placement = messageHorizontalPlacement(message.presentation, message.fromMe)
    LaunchedEffect(groupMemberContact) {
        if (groupMemberContact == null) {
            onGroupMemberAvatarRemoved()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (placement) {
            MessageHorizontalPlacement.Start -> Arrangement.Start
            MessageHorizontalPlacement.Center -> Arrangement.Center
            MessageHorizontalPlacement.End -> Arrangement.End
        },
        verticalAlignment = if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
            Alignment.CenterVertically
        } else {
            Alignment.Top
        }
    ) {
        if (multiSelectMode) {
            MessageSelectionToggle(
                selected = selected,
                onClick = onToggleSelection,
                modifier = Modifier.padding(top = 20.dp, end = 4.dp)
            )
        }
        if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
            CompactAvatar(
                contact = groupMemberContact,
                role = AvatarRole.GroupMember,
                sizeDp = groupMemberAvatarSizeDp(),
                onClick = {},
                onLongClick = { onGroupMemberAvatarLongClick(groupMemberContact) },
                onBoundsChanged = onGroupMemberAvatarBoundsChanged,
                modifier = Modifier.offset(y = groupMemberAvatarBubbleCenterOffsetDp().dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        MessageBlock(
            message = message,
            index = index,
            onPreviewMedia = onPreviewMedia,
            onOpenMediaActions = onOpenMediaActions,
            onLongPressMessage = onLongPressMessage,
            multiSelectMode = multiSelectMode,
            selected = selected,
            reminded = reminded,
            favorite = favorite,
            claimed = claimed,
            onToggleSelection = onToggleSelection,
            onClick = onClick,
            onBubbleBoundsChanged = onBubbleBoundsChanged,
            modifier = if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
                Modifier.weight(1f, fill = false)
            } else {
                Modifier.fillMaxWidth(
                    when (message.presentation) {
                        FloatingChatMessagePresentation.Bubble -> 0.99f
                        FloatingChatMessagePresentation.SpecialCard -> 0.99f
                        FloatingChatMessagePresentation.MediaStandalone -> 0.99f
                        FloatingChatMessagePresentation.System -> 1f
                    }
                )
            }
        )
    }
}
