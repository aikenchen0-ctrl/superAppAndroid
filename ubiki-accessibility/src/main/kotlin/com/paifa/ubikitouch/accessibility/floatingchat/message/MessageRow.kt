package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.AvatarRole
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactAvatar
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberContactForMessage
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberAvatarBubbleCenterOffsetDp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberAvatarSizeDp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rootBoundsFromPosition
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

@Composable
internal fun MessageRow(
    message: FloatingChatMessage,
    index: Int,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    showAttachedAvatar: Boolean = true,
    contactsById: Map<String, FloatingChatContact>,
    homeOverviewAccountColor: Long?,
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
        )?.takeIf { showAttachedAvatar }
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
            homeOverviewVisible = homeOverviewVisible,
            homeOverviewAccountColor = homeOverviewAccountColor,
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageBlock(
    message: FloatingChatMessage,
    index: Int,
    onPreviewMedia: (FloatingChatMessage) -> Unit,
    onOpenMediaActions: (FloatingChatMessage) -> Unit,
    onLongPressMessage: (FloatingChatMessage, Rect?) -> Unit,
    multiSelectMode: Boolean,
    selected: Boolean,
    reminded: Boolean,
    favorite: Boolean,
    claimed: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onBubbleBoundsChanged: (Rect) -> Unit,
    homeOverviewVisible: Boolean = false,
    homeOverviewAccountColor: Long? = null,
    modifier: Modifier = Modifier
) {
    val bubbleClickSource = remember { MutableInteractionSource() }
    var currentBounds by remember(message.id) { mutableStateOf<Rect?>(null) }
    fun updateCurrentBounds(bounds: Rect) {
        currentBounds = bounds
        onBubbleBoundsChanged(bounds)
    }
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    val isSpecialCard = message.presentation == FloatingChatMessagePresentation.SpecialCard
    val isPaymentCard = message.isPaymentCardMessage()
    val usesBubbleChrome = messageUsesBubbleChrome(message.presentation)
    val bubbleShape = RoundedCornerShape(if (isSpecialCard) 7.dp else 8.dp)
    val bubbleColor = messageBubbleColor(message, claimed)
    val bubbleBorderColor = messageBubbleBorderColor(message, claimed)
    val aiDraftDashedBubble = aiDraftMessageUsesGreenDashedBubble(message)
    val usesDemoBubble = messageTypeUsesImModuleBubble(message.type) && !isSystem
    Column(
        modifier = modifier,
        horizontalAlignment = when {
            isSystem -> Alignment.CenterHorizontally
            message.fromMe -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Box(modifier = Modifier.padding(top = if (isSystem) 0.dp else 8.dp)) {
                if (usesBubbleChrome) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = if (usesDemoBubble && message.fromMe) {
                                    imModuleSelfBubbleShadowBlurDp().dp
                                } else {
                                    3.dp
                                },
                                shape = bubbleShape,
                                ambientColor = OverlayTokens.glassShadow,
                                spotColor = OverlayTokens.glassShadow
                            )
                            .clip(bubbleShape)
                            .background(bubbleColor)
                            .then(
                                if (aiDraftDashedBubble) {
                                    Modifier.aiDraftDashedBorder(bubbleShape)
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = bubbleBorderColor,
                                        shape = bubbleShape
                                    )
                                }
                            )
                            .onGloballyPositioned { coordinates ->
                                updateCurrentBounds(
                                    rootBoundsFromPosition(
                                        positionInRoot = coordinates.positionInRoot(),
                                        width = coordinates.size.width,
                                        height = coordinates.size.height
                                    )
                                )
                            }
                            .combinedClickable(
                                interactionSource = bubbleClickSource,
                                indication = null,
                                onClick = {
                                    if (multiSelectMode) {
                                        onToggleSelection()
                                    } else {
                                        onClick()
                                    }
                                },
                                onLongClick = { onLongPressMessage(message, currentBounds) }
                            )
                            .padding(
                                horizontal = when {
                                    isSystem -> 10.dp
                                    isSpecialCard -> 16.dp
                                    else -> 12.dp
                                },
                                vertical = when {
                                    isSystem -> 5.dp
                                    isPaymentCard -> paymentCardOuterVerticalPaddingDp().dp
                                    isSpecialCard -> 14.dp
                                    else -> 10.dp
                                }
                            )
                    ) {
                        MessageContent(
                            message = message,
                            index = index,
                            onPreviewMedia = onPreviewMedia,
                            onOpenMediaActions = onOpenMediaActions,
                            onLongPressMessage = onLongPressMessage,
                            multiSelectMode = multiSelectMode,
                            onToggleSelection = onToggleSelection,
                            claimed = claimed
                        )
                    }
                    if (homeOverviewVisible && homeOverviewAccountColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 4.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(homeOverviewAccountColor))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.combinedClickable(
                            interactionSource = bubbleClickSource,
                            indication = null,
                            onClick = {
                                if (multiSelectMode) {
                                    onToggleSelection()
                                } else {
                                    onClick()
                                }
                            },
                            onLongClick = { onLongPressMessage(message, currentBounds) }
                        )
                    ) {
                        MessageContent(
                            message = message,
                            index = index,
                            onPreviewMedia = onPreviewMedia,
                            onOpenMediaActions = onOpenMediaActions,
                            onLongPressMessage = onLongPressMessage,
                            multiSelectMode = multiSelectMode,
                            onToggleSelection = onToggleSelection,
                            claimed = claimed,
                            onContentBoundsChanged = ::updateCurrentBounds
                        )
                    }
                }
                if (favorite || reminded) {
                    MessageStateBadges(
                        favorite = favorite,
                        reminded = reminded,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = 4.dp)
                    )
                }
                if (!isSystem) {
                    TextLabel(
                        text = message.senderName,
                        size = 10.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 16.dp, y = (-6).dp),
                        weight = FontWeight.Bold,
                        color = OverlayTokens.bubbleNameText,
                        maxLines = 1,
                        shadow = OverlayTokens.imModuleTextShadow
                    )
                }
            }
            scrmSendStatusTextFor(message)?.let { statusText ->
                ScrmSendStatusLabel(
                    text = statusText,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                )
            }
        }
    }
}


@Composable
private fun ScrmSendStatusLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    TextLabel(
        text = text,
        size = 10.sp,
        modifier = modifier,
        color = OverlayTokens.cardSecondaryText,
        maxLines = 1,
        shadow = OverlayTokens.imModuleTextShadow
    )
}
