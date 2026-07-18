package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatConnectorViewportEdge
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ConnectorCoordinateState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.RightRailVisibleAccountItem
import com.paifa.ubikitouch.accessibility.floatingchat.chat.createChatConnectorTree
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget

internal fun rightRailToolButtonsUseMaterialIcons(): Boolean = true

internal fun rightRailToolButtonsShowTextLabels(): Boolean = true

internal fun rightRailToolButtonsSupportLongPressReorder(): Boolean = true

internal fun rightRailToolButtonsSupportDragReorder(): Boolean = true

internal fun rightRailToolReorderUsesLongPressDragGesture(): Boolean = true

internal fun rightRailToolReorderFollowsDraggedIcon(): Boolean = true

internal fun rightRailToolReorderKeepsOriginalIcon(): Boolean = true

internal fun rightRailToolReorderShowsLongPressFeedback(): Boolean = true

internal fun rightRailToolReorderAnimatesDisplacedItems(): Boolean = true

internal fun rightRailToolReorderUsesSinglePointerGesture(): Boolean = true

internal fun rightRailToolReorderUsesAbsoluteFingerOffset(): Boolean = true

internal fun rightRailToolReorderSkipsPlacementAnimationForDraggedItem(): Boolean = true

internal fun rightRailToolReorderModeLetsAnyButtonDragImmediately(): Boolean = true

internal fun rightRailToolReorderModeChangeDoesNotRestartActivePointerGesture(): Boolean = true

internal fun rightRailToolReorderParentConsumesTapGestures(): Boolean = false

internal fun rightRailToolGestureCancelsLongPressWhenMovedBeforeTimeout(): Boolean = true

internal fun rightRailToolGestureCancelsClickWhenMovedPastTouchSlop(): Boolean = true

internal fun rightRailToolGestureConsumesDownBeforeClick(): Boolean = false

internal fun rightRailToolReorderMovesByDraggedCenterCrossingSlots(): Boolean = true

internal fun rightRailToolListScrollDisabledDuringReorderDrag(): Boolean = true

internal fun rightRailToolReorderPersistsOnDragEnd(): Boolean = true

internal fun rightRailToolSingleTapExitsReorder(): Boolean = true

internal fun rightRailToolReorderUsesClickToMove(): Boolean = false

internal fun rightRailKeepsSelectedAccountConnectorAnchorWhenCompressed(): Boolean {
    val state = ConnectorCoordinateState()
    val selectedBounds = Rect(12f, 24f, 54f, 66f)
    state.updateAccountAvatar("account-work", selectedBounds)
    state.updateSelectedAccountAvatar("account-work", selectedBounds)
    state.removeAccountAvatar("account-work")
    return state.accountAvatarFor("account-work") == selectedBounds &&
        state.accountAvatarFor("account-private") == null
}

internal fun rightRailKeepsAnyAccountConnectorAnchorWhenScrolledOffscreen(): Boolean {
    val state = ConnectorCoordinateState()
    val accountBounds = Rect(12f, -32f, 54f, 10f)
    state.updateAccountViewport(Rect(0f, 0f, 58f, 220f))
    state.updateAccountAvatar("account-store", accountBounds)
    state.removeAccountAvatar("account-store")
    return state.accountAvatarFor("account-store")?.center?.y == 0f &&
        state.accountAvatarFor("account-work") == null
}

internal fun rightRailAccountConnectorAnchorFollowsVirtualOffscreenPosition(): Float {
    val state = ConnectorCoordinateState()
    val viewport = Rect(0f, 0f, 58f, 220f)
    state.updateAccountViewport(viewport)
    state.updateAccountAvatar("account-0", Rect(16f, 45f, 58f, 87f))
    state.removeAccountAvatar("account-0")
    state.updateVirtualAccountAvatars(
        accountIds = listOf("account-0", "account-1", "account-2"),
        visibleItems = listOf(
            RightRailVisibleAccountItem(index = 1, offset = 18, size = 42),
            RightRailVisibleAccountItem(index = 2, offset = 66, size = 42)
        ),
        viewport = viewport,
        fallbackStepPx = 48f
    )
    return state.accountAvatarFor("account-0")?.center?.y ?: -1f
}

internal fun rightRailSingleVisibleAccountKeepsUpperOffscreenAnchorAbove(): Float {
    val state = ConnectorCoordinateState()
    val viewport = Rect(0f, 0f, 58f, 220f)
    state.updateAccountViewport(viewport)
    state.updateVirtualAccountAvatars(
        accountIds = listOf("account-0", "account-1", "account-2"),
        visibleItems = listOf(
            RightRailVisibleAccountItem(index = 1, offset = 18, size = 42)
        ),
        viewport = viewport,
        fallbackStepPx = 48f
    )
    return state.accountAvatarFor("account-0")?.center?.y ?: -1f
}

internal fun rightRailOffscreenAccountConnectorUsesEdgeIndicator(): Boolean {
    val tree = createChatConnectorTree(
        avatarBounds = Rect(240f, -42f, 282f, 0f),
        bubbleBounds = listOf(Rect(112f, 90f, 220f, 134f)),
        layerBounds = Rect(0f, 0f, 282f, 360f),
        visibleRootBounds = Rect(58f, 0f, 224f, 300f),
        target = FloatingChatConnectionTarget.Account,
        hasMessagesAbove = false,
        hasMessagesBelow = false,
        avatarOffscreenEdge = ChatConnectorViewportEdge.Above
    ) ?: return false

    return tree.avatarBranch == null &&
        tree.trunkStart.y == 0f &&
        tree.messageBranches.isNotEmpty()
}

internal fun rightRailPinnedSelectedAccountConnectorAnchorYWhenCompressed(): Float {
    val state = ConnectorCoordinateState()
    val selectedBounds = Rect(12f, 45f, 54f, 87f)
    state.updateAccountViewport(Rect(0f, 0f, 58f, 46f))
    state.updateAccountAvatar("account-work", selectedBounds)
    state.updateSelectedAccountAvatar("account-work", selectedBounds)
    state.removeAccountAvatar("account-work")
    return state.accountAvatarFor("account-work")?.center?.y ?: -1f
}
