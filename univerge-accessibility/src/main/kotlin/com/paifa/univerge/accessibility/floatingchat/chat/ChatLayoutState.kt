package com.paifa.univerge.accessibility.floatingchat.chat

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.floatingchat.chat.toGroupThreadSelection
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.core.model.FloatingChatMessage
import kotlin.math.abs

internal fun leftRailUsesScrollableLazyColumn(): Boolean = true

internal fun leftRailAllowsScrollWhenContentIsShort(): Boolean = true

internal fun leftRailSupportsBidirectionalScrolling(): Boolean = true

internal fun leftRailLeadingSpacerItemCount(): Int = LeftRailLeadingSpacerItemCount

internal fun leftRailInitialFirstVisibleItemIndex(): Int = 0

internal fun leftRailSelectedThreadFirstVisibleIndex(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    selectedThread: ChatThreadSelection
): Int {
    val railItemIndex = when (selectedThread) {
        ChatThreadSelection.Group -> 0
        is ChatThreadSelection.GroupChat -> groups.indexOfFirst { group -> group.id == selectedThread.groupId }
        is ChatThreadSelection.Private -> {
            val contactIndex = contacts.indexOfFirst { contact -> contact.id == selectedThread.contactId }
            if (contactIndex >= 0) groups.size + contactIndex else -1
        }
    }
    return if (railItemIndex >= 0) {
        LeftRailLeadingSpacerItemCount + railItemIndex
    } else {
        leftRailInitialFirstVisibleItemIndex()
    }
}

internal fun leftRailScrollsSelectedThreadAvatarIntoViewForConnectors(): Boolean = false

internal fun leftRailKeepsScrollPositionWhenSelectingVisibleAvatar(): Boolean = true

internal fun leftRailClearsDisposedAvatarConnectorBounds(): Boolean = true

internal fun leftRailKeepsAnySessionConnectorAnchorWhenScrolledOffscreen(): Boolean {
    val state = ConnectorCoordinateState()
    val avatarBounds = Rect(0f, -32f, 42f, 10f)
    state.updateUserViewport(Rect(0f, 0f, 56f, 220f))
    state.updateUserAvatar("session-store", avatarBounds)
    state.removeUserAvatar("session-store")
    return state.userAvatarFor("session-store")?.center?.y == 0f &&
        state.userAvatarFor("session-work") == null
}

internal fun leftRailSessionConnectorAnchorFollowsVirtualOffscreenPosition(): Float {
    val state = ConnectorCoordinateState()
    val viewport = Rect(0f, 0f, 56f, 220f)
    state.updateUserViewport(viewport)
    state.updateUserAvatar("session-0", Rect(0f, 45f, 42f, 87f))
    state.removeUserAvatar("session-0")
    state.updateVirtualUserAvatars(
        sessionIds = listOf("session-0", "session-1", "session-2"),
        visibleItems = listOf(
            LeftRailVisibleSessionItem(index = 1, offset = 18, size = 42),
            LeftRailVisibleSessionItem(index = 2, offset = 66, size = 42)
        ),
        viewport = viewport,
        fallbackStepPx = 48f
    )
    return state.userAvatarFor("session-0")?.center?.y ?: -1f
}

internal fun leftRailPinsSelectedAvatarWhileScrolledOffscreen(): Boolean = true

internal fun leftRailSelectedActionsDwellMs(): Long = LeftRailSelectedActionsDwellMs

/**
 * The expanded rail actions are deliberate: scrolling or changing the active session resets
 * the dwell timer so adjacent avatars cannot be moved by accident.
 */
internal fun leftRailSelectionActionsVisible(
    selectedDurationMs: Long,
    isSelected: Boolean,
    isScrolling: Boolean
): Boolean = isSelected && !isScrolling && selectedDurationMs >= LeftRailSelectedActionsDwellMs

internal fun leftRailScrollableTopPaddingDp(
    itemCount: Int,
    viewportHeightDp: Int
): Int = 0

internal fun leftRailDefaultsToTopAlignedAvatars(): Boolean = true

internal fun leftRailBouncesBackToTopWhenContentFitsViewport(): Boolean = true

internal fun leftRailDisablesScrollWhenContentFitsViewport(): Boolean = false

internal fun leftRailUsesNonAnimatedTopResetWhenContentFitsViewport(): Boolean = true

internal fun leftRailSupportsGentleTopPullAndRelease(): Boolean = true

internal fun leftRailTopOverscrollMaxDp(): Int = LeftRailTopOverscrollMaxDp

internal fun leftRailTopOverscrollResistance(): Float = LeftRailTopOverscrollResistance

internal fun leftRailTopOverscrollReturnMs(): Int = LeftRailTopOverscrollReturnMs

internal fun leftRailContentFitsViewport(
    itemCount: Int,
    viewportHeightDp: Int
): Boolean {
    if (itemCount <= 0 || viewportHeightDp <= 0) return false
    return leftRailContentHeightDp(itemCount) <= viewportHeightDp
}

internal fun leftRailScrollShowsFollowTextOverlay(): Boolean = true

internal fun leftRailFollowTextHideDelayMs(): Int = LeftRailFollowTextHideDelayMs

internal fun leftRailFollowTextHidesOnRelease(): Boolean = LeftRailFollowTextHideDelayMs == 0

internal fun leftRailFollowTextStartOffsetDp(): Int = LeftRailFollowTextStartOffsetDp

internal fun leftRailFollowTextWidthDp(): Int = LeftRailFollowTextWidthDp

internal fun leftRailFollowTextAvatarGapDp(): Int = LeftRailFollowTextAvatarGapDp

internal fun leftRailFollowTextContainerWidthDp(): Int = LeftRailFollowTextContainerWidthDp

internal fun leftRailFollowTextLayerWidthDp(): Int = LeftRailFollowTextLayerWidthDp

internal fun leftRailTouchableWidthDp(): Int = SessionRailWidthDp

internal fun leftRailAvatarSizeDp(): Int = RailAvatarSizeDp

internal fun leftRailItemGapDp(): Int = LeftRailItemGapDp

internal fun railScreenEdgeInsetPx(): Int = RailScreenEdgeInsetPx

internal fun leftRailAvatarScreenEdgeInsetPx(): Int = RailScreenEdgeInsetPx

internal fun leftRailFollowTextInnerPaddingDp(): Int = LeftRailFollowTextInnerPaddingDp

internal fun leftRailFollowTextNameSizeSp(): Float = LeftRailFollowTextNameSizeSp

internal fun leftRailFollowTextMessageSizeSp(): Float = LeftRailFollowTextMessageSizeSp

internal fun leftRailFollowTextTimeSizeSp(): Float = LeftRailFollowTextTimeSizeSp

internal fun leftRailFollowTextCardHeightDp(): Int = LeftRailFollowTextCardHeightDp

internal fun leftRailFollowTextCardCornerDp(): Int = LeftRailFollowTextCardCornerDp

internal fun leftRailFollowTextCardBackgroundAlpha(): Float = LeftRailFollowTextCardBackgroundAlpha

internal fun leftRailFollowTextStartsAtAvatarRightEdge(): Boolean {
    return LeftRailFollowTextStartOffsetDp + LeftRailFollowTextInnerPaddingDp == RailAvatarSizeDp
}

internal fun leftRailFollowTextIncludesScreenEdgeInset(): Boolean = true

internal fun leftRailScrollableBottomPaddingDp(
    itemCount: Int,
    viewportHeightDp: Int
): Int {
    val safeItemCount = itemCount.coerceAtLeast(0)
    val contentHeight = leftRailContentHeightDp(safeItemCount)
    val minScrollableContentHeight = viewportHeightDp + LeftRailMinimumScrollRangeDp
    return (minScrollableContentHeight - contentHeight)
        .coerceAtLeast(LeftRailShortContentScrollPaddingDp)
}

internal fun leftRailContentHeightDp(itemCount: Int): Int {
    val safeItemCount = itemCount.coerceAtLeast(0)
    return if (safeItemCount == 0) {
        0
    } else {
        safeItemCount * RailAvatarSizeDp + (safeItemCount - 1) * LeftRailItemGapDp
    }
}

internal fun leftRailUsesPointerDragToRevealFollowText(): Boolean = false

internal fun leftRailFollowTextOverlayConsumesPointerEvents(): Boolean = false

internal fun keyboardDismissUsesBlockingFullScreenPointerLayer(): Boolean = false

internal fun leftRailSortsSessionsByLatestChatTime(): Boolean = true

internal fun leftRailLayerDrawsAboveConnectorLayer(): Boolean {
    return leftRailLayerZIndex() > connectorLayerZIndex()
}

internal fun leftRailLayerZIndex(): Float = LeftRailLayerZIndex

internal fun connectorLayerZIndex(): Float = ConnectorLayerZIndex

internal fun leftRailFollowTextIncludesNameLastMessageAndTime(): Boolean = true

internal fun leftRailFollowTextUsesAvatarTopAlignment(): Boolean = true

internal fun leftRailFollowTextUsesLiveAvatarBounds(): Boolean = true

internal fun leftRailFollowTextYMatchesAvatarBounds(): Boolean = true

internal fun leftRailFollowTextBoundsUseSingleInvalidationVersion(): Boolean = true

internal fun leftRailFollowTextUsesCompactTypography(): Boolean {
    return LeftRailFollowTextNameSizeSp <= 11f &&
        LeftRailFollowTextMessageSizeSp <= 13f &&
        LeftRailFollowTextTimeSizeSp <= 8.5f
}

internal fun leftRailFollowTextUsesBackgroundHalo(): Boolean = false

internal fun leftRailFollowTextUsesDarkTextShadow(): Boolean {
    return OverlayTokens.leftRailFollowTextShadow.color.toArgb() != 0x00000000
}

internal data class LeftRailFollowInfo(
    val contactId: String,
    val name: String,
    val metaLines: List<String>,
    val lastMessage: String,
    val lastTime: String,
    val avatarColor: Long,
    val topPx: Float = 0f,
    val heightPx: Float = 0f
)

internal data class LeftRailFollowTextColors(
    val name: Color,
    val message: Color,
    val time: Color
)

internal data class LeftRailProfilePlaceholder(
    val name: String,
    val region: String,
    val tags: List<String>,
    val summary: String
)

internal fun leftRailFollowMetaText(info: LeftRailFollowInfo): String {
    return info.metaLines.joinToString("  ·  ")
}

internal fun leftRailProfilePlaceholder(contact: FloatingChatContact): LeftRailProfilePlaceholder {
    return LeftRailProfilePlaceholder(
        name = contact.name,
        region = contact.region.trim(),
        tags = contact.tags.map(String::trim).filter(String::isNotEmpty),
        summary = contact.description.trim()
    )
}

internal fun leftRailFollowTextColors(avatarColor: Long): LeftRailFollowTextColors {
    val base = Color(avatarColor)
    return LeftRailFollowTextColors(
        name = base.leftRailFollowTextTone(0.58f),
        message = base.leftRailFollowTextTone(0.46f),
        time = base.leftRailFollowTextTone(0.36f)
    )
}

private fun Color.leftRailFollowTextTone(intensity: Float): Color {
    return Color(
        red = red * intensity,
        green = green * intensity,
        blue = blue * intensity,
        alpha = 1f
    )
}

internal fun leftRailVisibleFollowInfos(
    conversation: FloatingChatConversation,
    selectedAccountId: String,
    contactsById: Map<String, FloatingChatContact>,
    visibleAvatarBounds: Map<String, Rect>,
    railRootTopPx: Float
): List<LeftRailFollowInfo> {
    return visibleAvatarBounds.mapNotNull { (contactId, bounds) ->
        val contact = contactsById[contactId] ?: return@mapNotNull null
        leftRailFollowInfoForContact(
            conversation = conversation,
            contact = contact,
            selectedAccountId = selectedAccountId
        ).copy(
            topPx = bounds.top - railRootTopPx,
            heightPx = bounds.height
        )
    }
}

internal fun leftRailFollowInfoForContact(
    conversation: FloatingChatConversation,
    contact: FloatingChatContact,
    selectedAccountId: String
): LeftRailFollowInfo {
    val selection = if (conversation.groupContacts.any { group -> group.id == contact.id }) {
        contact.toGroupThreadSelection()
    } else {
        ChatThreadSelection.Private(contact.id)
    }
    val latestMessage = visibleMessagesForThread(
        conversation = conversation,
        selection = selection,
        selectedAccountId = selectedAccountId
    ).lastOrNull()
    return LeftRailFollowInfo(
        contactId = contact.id,
        name = contact.name,
        metaLines = buildList {
            contact.region.trim().takeIf(String::isNotEmpty)?.let { region -> add("地区 $region") }
            contact.tags.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .joinToString(" · ")
                .takeIf(String::isNotEmpty)
                ?.let { tags -> add("标签 $tags") }
        },
        lastMessage = latestMessage?.text?.ifBlank { contact.description } ?: contact.description,
        lastTime = latestMessage?.time ?: "",
        avatarColor = contact.avatarColor,
        topPx = 0f,
        heightPx = 0f
    )
}

internal fun messagePaneUsesLazyColumn(): Boolean = true

internal fun messagePaneOnlyComposesVisibleMessages(): Boolean = true

internal fun messagePaneUsesStableMessageKeys(): Boolean = true

internal fun messagePaneUsesMessageContentTypes(): Boolean = true

internal fun messagePaneHorizontalPaddingDp(): Int = MessagePaneHorizontalPaddingDp

internal fun connectorLayerUsesLazyListVisibleItemsOnly(): Boolean = true

internal fun connectorLayerClearsStaleMessageBoundsWhenThreadChanges(): Boolean = true

internal fun connectorLayerClearsStaleMessageBoundsWhenMediaMessagesChange(): Boolean = true

internal fun connectorLayerUsesPrecomputedOffscreenTargets(): Boolean = true

internal fun messageBoundsForLongPressAreCapturedOnlyWhenNeeded(): Boolean = true

internal fun connectorLayerAvoidsSnapshotStateForPerFrameMessageBounds(): Boolean = true

internal fun connectorLayerDefersLazyListLayoutReadsToDrawPhase(): Boolean = true

internal fun connectorLayerReusesNativePaintDuringDraw(): Boolean = true

internal fun connectorCoordinateCacheUsesSingleInvalidationVersion(): Boolean = true

internal fun mediaThumbnailsDecodeOffMainThread(): Boolean = true

internal fun privateChatConnectorUsesSelectedAvatarLiveBounds(): Boolean = true

internal fun privateChatConnectorAllowsMessagesWithoutStoredTargetId(): Boolean = true

internal fun privateChatConnectorKeepsSelectedAvatarAnchorWhenLazyItemDisposes(): Boolean = true

internal fun privateChatConnectorDoesNotReuseGroupThreadAvatarAfterGroupSwitch(): Boolean = true

internal fun imModuleConnectionLineColorArgb(): Int = OverlayTokens.connectorLine.toArgb()

internal fun imModuleConnectionLineStrokeWidthPx(): Float = 6f

internal fun imModuleConnectionLineShadowColorArgb(): Int = OverlayTokens.connectorLineShadow.toArgb()

internal fun imModuleConnectionLineShadowBlurPx(): Float = 4f

internal fun imModuleConnectionLineShadowOffsetXPx(): Float = 1f

internal fun imModuleConnectionLineShadowOffsetYPx(): Float = 1f

internal fun imModuleConnectionLineUsesNativeCanvasShadow(): Boolean = true

internal fun imModuleConnectionLineHorizontalOffsetPx(): Float = 48f

internal fun imModuleConnectionLineMinimumBranchPx(): Float = 32f

internal fun imModuleConnectionLineCornerRadiusPx(): Float = 12f

internal fun imModuleConnectionLineCornerArcFraction(): Float = 0.25f

internal fun imModuleConnectionLineUsesRoundedElbows(): Boolean = true

internal fun connectorRoundedElbowRadiusPx(horizontalRoom: Float): Float {
    return (abs(horizontalRoom) * imModuleConnectionLineCornerArcFraction())
        .coerceIn(1f, imModuleConnectionLineCornerRadiusPx())
}

internal fun imModuleConnectionLineBubbleGapPx(): Float = 0f

internal fun imModuleConnectionLineBubbleOverlapPx(): Float = 0f

internal fun imModuleConnectionLineUsesBraceHooks(): Boolean = true

internal fun imModuleConnectionLineHooksStartAtTrunkJoint(): Boolean = true

internal fun imModuleConnectionLineDrawsEndpointDots(): Boolean = false

internal fun chatConnectorTreeUsesButtCapsToAvoidSubpathCaps(): Boolean = true

internal fun chatConnectorHookTrunkOverlapPx(): Float = 3f

internal fun connectorTargetIdForMessage(
    message: FloatingChatMessage,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    groupMemberAvatarsVisible: Boolean
): String? {
    return message.toConnectorTargetKey(
        selection = selection,
        selectedAccountId = selectedAccountId,
        groupMemberAvatarsVisible = groupMemberAvatarsVisible
    )?.targetId
}

internal fun privateChatConnectorsUseCurrentThreadTargets(): Boolean = true

internal fun privateChatLeftConnectorAnchorsToUserAvatarRightEdge(): Boolean = true

internal fun privateChatRightConnectorAnchorsToAccountAvatarLeftEdge(): Boolean = true

private const val SessionRailWidthDp = 56
private const val RailScreenEdgeInsetPx = 12
private const val RailAvatarSizeDp = 46
private const val MessagePaneHorizontalPaddingDp = 4
private const val LeftRailFollowTextHideDelayMs = 0
private const val LeftRailFollowTextStartOffsetDp = RailAvatarSizeDp
// Mirrors iOS leftScrollPreviewView/card sizing: the preview crosses the
// window midpoint slightly while leaving the right account rail unobstructed.
private const val LeftRailFollowTextWidthDp = 172
private const val LeftRailFollowTextAvatarGapDp = 4
private const val LeftRailFollowTextContainerWidthDp = LeftRailFollowTextWidthDp + LeftRailFollowTextAvatarGapDp
private const val LeftRailFollowTextLayerWidthDp = LeftRailFollowTextStartOffsetDp + LeftRailFollowTextContainerWidthDp
private const val LeftRailFollowTextInnerPaddingDp = 0
private const val LeftRailFollowTextNameSizeSp = 10f
private const val LeftRailFollowTextMessageSizeSp = 13f
private const val LeftRailFollowTextTimeSizeSp = 8f
private const val LeftRailFollowTextCardHeightDp = 62
private const val LeftRailFollowTextCardCornerDp = 9
private const val LeftRailFollowTextCardBackgroundAlpha = 0.42f
private const val LeftRailLeadingSpacerItemCount = 1
private const val LeftRailShortContentScrollPaddingDp = 96
private const val LeftRailItemGapDp = 6
private const val LeftRailMinimumScrollRangeDp = 160
private const val LeftRailTopOverscrollMaxDp = 18
private const val LeftRailSelectedActionsDwellMs = 5_000L
private const val LeftRailTopOverscrollResistance = 0.32f
private const val LeftRailTopOverscrollReturnMs = 170
private const val LeftRailLayerZIndex = 30f
private const val ConnectorLayerZIndex = 10f
