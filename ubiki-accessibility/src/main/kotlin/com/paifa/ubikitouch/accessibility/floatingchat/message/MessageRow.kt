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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.paifa.ubikitouch.accessibility.floatingchat.components.LocalOverlayTextShadow
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberContactForMessage
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberAvatarBubbleCenterOffsetDp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberAvatarSizeDp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.rootBoundsFromPosition
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

internal class MessageBoundsHolder {
    var value: Rect? = null
        private set

    fun update(bounds: Rect, onChanged: (Rect) -> Unit): Boolean {
        if (value == bounds) return false
        value = bounds
        onChanged(bounds)
        return true
    }
}

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
    onGroupMemberAvatarRemoved: () -> Unit,
    detailedBubble: Boolean = usesDetailedMessageBubble(homeOverviewVisible, selectedThread),
    showOwnSenderName: Boolean = false,
    bubbleAppearance: BubbleAppearance = BubbleAppearance.TwoD
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
    val showSenderNickname = shouldShowDetailedBubbleSenderName(
        detailedBubble = detailedBubble,
        presentation = message.presentation
    ) || (showOwnSenderName && message.fromMe && messageUsesBubbleChrome(message.presentation))
    val senderNickname = remember(message, groupMemberContact, contactsById) {
        val resolvedNickname = groupMemberContact?.name
            ?: message.threadContactId?.let { threadId -> contactsById[threadId]?.name }
            ?: contactsById.values.firstOrNull { contact ->
                message.senderName.isNotBlank() && contact.description.contains(message.senderName)
            }?.name
        chatBubbleDisplaySenderName(
            fromMe = message.fromMe,
            rawSenderName = message.senderName,
            resolvedNickname = resolvedNickname
        )
    }
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
            senderNickname = senderNickname,
            showSenderNickname = showSenderNickname,
            bubbleAppearance = bubbleAppearance,
            modifier = if (groupMemberContact != null && placement == MessageHorizontalPlacement.Start) {
                Modifier.weight(1f, fill = false)
            } else {
                Modifier.fillMaxWidth(
                        when (message.presentation) {
                        FloatingChatMessagePresentation.Bubble -> 0.88f
                        FloatingChatMessagePresentation.SpecialCard -> 0.88f
                        FloatingChatMessagePresentation.MediaStandalone -> 0.88f
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
    senderNickname: String,
    showSenderNickname: Boolean,
    bubbleAppearance: BubbleAppearance = BubbleAppearance.TwoD,
    modifier: Modifier = Modifier
) {
    val bubbleClickSource = remember { MutableInteractionSource() }
    val currentBounds = remember(message.id) { MessageBoundsHolder() }
    fun updateCurrentBounds(bounds: Rect) {
        currentBounds.update(bounds, onBubbleBoundsChanged)
    }
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    val isSpecialCard = message.presentation == FloatingChatMessagePresentation.SpecialCard
    val isPaymentCard = message.isPaymentCardMessage()
    val displayGroup = messageDisplayGroupFor(message)
    val usesBubbleChrome = messageDisplayGroupUsesBubbleChrome(displayGroup)
    val bubbleShape = when {
        isSpecialCard -> RoundedCornerShape(7.dp)
        message.fromMe -> RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 10.dp,
            bottomEnd = 3.dp
        )
        else -> RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 3.dp,
            bottomEnd = 10.dp
        )
    }
    val bubbleColor = messageBubbleColor(message, claimed)
    val bubbleBorderColor = messageBubbleBorderColor(message, claimed)
    val usesThreeDimensionalBubble = messageUsesThreeDimensionalBubble(message, bubbleAppearance)
    val threeDimensionalAccent = messageThreeDimensionalAccent(message)
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
        Column(horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start) {
            Box(modifier = Modifier.padding(top = if (isSystem) 0.dp else 8.dp)) {
                if (usesBubbleChrome) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = if (usesThreeDimensionalBubble) 3.dp else if (usesDemoBubble) 1.dp else 2.dp,
                                shape = bubbleShape,
                                ambientColor = OverlayTokens.glassShadow,
                                spotColor = if (usesThreeDimensionalBubble) {
                                    threeDimensionalAccent.copy(alpha = 0.52f)
                                } else {
                                    OverlayTokens.glassShadow
                                }
                            )
                            .drawBehind {
                                if (usesThreeDimensionalBubble) {
                                    val radius = 10.dp.toPx()
                                    drawRoundRect(
                                        color = threeDimensionalAccent.copy(alpha = 0.28f),
                                        topLeft = Offset(
                                            x = if (message.fromMe) -1.2.dp.toPx() else 1.2.dp.toPx(),
                                            y = 1.4.dp.toPx()
                                        ),
                                        size = size,
                                        cornerRadius = CornerRadius(radius, radius)
                                    )
                                }
                            }
                            .clip(bubbleShape)
                            .background(
                                if (usesDemoBubble) {
                                    bubbleColor.copy(
                                        alpha = (bubbleColor.alpha * imModuleBubbleGlassFillMultiplier())
                                            .coerceIn(0f, 0.72f)
                                    )
                                } else {
                                    bubbleColor
                                }
                            )
                            .drawBehind {
                                if (usesThreeDimensionalBubble) {
                                    val radius = 10.dp.toPx()
                                    drawRoundRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.16f),
                                                Color.White.copy(alpha = 0.05f),
                                                Color.Transparent
                                            ),
                                            start = Offset(size.width * 0.08f, 0f),
                                            end = Offset(size.width * 0.92f, size.height)
                                        ),
                                        cornerRadius = CornerRadius(radius, radius)
                                    )
                                }
                            }
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
                                    } else if (homeOverviewVisible) {
                                        onClick()
                                    } else if (usesBubbleChrome) {
                                        onLongPressMessage(message, currentBounds.value)
                                    } else {
                                        onClick()
                                    }
                                },
                                onLongClick = { onLongPressMessage(message, currentBounds.value) }
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
                        CompositionLocalProvider(
                            LocalOverlayTextShadow provides OverlayTokens.imModuleTextShadow
                        ) {
                            MessageContent(
                                message = message,
                                index = index,
                                onPreviewMedia = onPreviewMedia,
                                onOpenMediaActions = onOpenMediaActions,
                                onMessageClick = { onClick() },
                                onLongPressMessage = onLongPressMessage,
                                multiSelectMode = multiSelectMode,
                                onToggleSelection = onToggleSelection,
                                claimed = claimed
                            )
                        }
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
                        modifier = Modifier
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
                                    } else if (homeOverviewVisible) {
                                        onClick()
                                    } else if (usesBubbleChrome) {
                                        onLongPressMessage(message, currentBounds.value)
                                    } else {
                                        onClick()
                                    }
                                },
                                onLongClick = { onLongPressMessage(message, currentBounds.value) }
                            )
                    ) {
                        MessageContent(
                            message = message,
                            index = index,
                            onPreviewMedia = onPreviewMedia,
                            onOpenMediaActions = onOpenMediaActions,
                            onMessageClick = { onClick() },
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
                if (showSenderNickname) {
                    val senderNameSize = detailedBubbleSenderNameSizeSp(
                        messageContentTextSizeSp(message)
                    ).sp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = 12.dp,
                                y = detailedBubbleSenderNameTopOffsetDp().dp
                            )
                            .height(detailedBubbleSenderNameBadgeHeightDp().dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    color = OverlayTokens.panel.copy(alpha = 0.46f),
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .detailedBubbleSenderNameBlur()
                        )
                        TextLabel(
                            text = senderNickname,
                            size = senderNameSize,
                            modifier = Modifier.padding(horizontal = 5.dp),
                            weight = FontWeight.Medium,
                            color = OverlayTokens.imModuleBubbleText.copy(alpha = 0.90f),
                            maxLines = 1,
                            shadow = OverlayTokens.imModuleTextShadow
                        )
                    }
                }
            }
            scrmSendStatusTextFor(message)?.let { statusText ->
                ScrmSendStatusLabel(
                    text = statusText,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp)
                )
            }
            if (multiSelectMode) {
                MessageSelectionToggle(
                    selected = selected,
                    onClick = onToggleSelection,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .offset(y = (-5).dp)
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
