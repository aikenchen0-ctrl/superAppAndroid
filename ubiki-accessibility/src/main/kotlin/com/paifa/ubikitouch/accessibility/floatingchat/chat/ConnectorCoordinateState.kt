package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupConnectorId
import com.paifa.ubikitouch.accessibility.floatingchat.chat.isGroupThread
import com.paifa.ubikitouch.accessibility.floatingchat.tools.rightRailAvatarSizeDp
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlin.math.abs

internal data class RightRailVisibleAccountItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

internal data class LeftRailVisibleSessionItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

internal enum class RailPinnedAvatarEdge {
    Top,
    Bottom
}

internal enum class ConnectorAvatarLane {
    Session,
    GroupMember,
    Account
}

internal fun connectorAvatarLaneFor(
    selection: ChatThreadSelection,
    target: FloatingChatConnectionTarget
): ConnectorAvatarLane {
    return when (target) {
        FloatingChatConnectionTarget.Account -> ConnectorAvatarLane.Account
        FloatingChatConnectionTarget.User -> when (selection) {
            ChatThreadSelection.Group -> ConnectorAvatarLane.GroupMember
            is ChatThreadSelection.GroupChat -> ConnectorAvatarLane.GroupMember
            is ChatThreadSelection.Private -> ConnectorAvatarLane.Session
        }
        FloatingChatConnectionTarget.None -> ConnectorAvatarLane.Session
    }
}

internal class ConnectorCoordinateState {
    var version by mutableIntStateOf(0)
        private set
    val userAvatars = mutableMapOf<String, Rect>()
    val homeOverviewAvatars = mutableMapOf<String, Rect>()
    val groupMemberAvatars = mutableMapOf<String, Rect>()
    val accountAvatars = mutableMapOf<String, Rect>()
    val messageBubbles = mutableMapOf<String, Rect>()
    var groupThreadAvatar: Rect? = null
        private set
    private var groupThreadAvatarId: String? = null
    var privateThreadAvatar: Rect? = null
        private set
    private var privateThreadAvatarId: String? = null
    private val retainedUserAvatars = mutableMapOf<String, Rect>()
    private var userViewport: Rect? = null
    private val retainedAccountAvatars = mutableMapOf<String, Rect>()
    private var accountViewport: Rect? = null
    var messageViewport: Rect? = null
        private set

    private fun invalidate() {
        version += 1
    }

    fun updateUserAvatar(id: String, bounds: Rect) {
        val changed = userAvatars.updateIfChanged(id, bounds) or
            retainedUserAvatars.updateIfChanged(id, bounds)
        if (changed) invalidate()
    }

    fun removeUserAvatar(id: String) {
        if (userAvatars.remove(id) != null) invalidate()
    }

    fun updateHomeOverviewAvatar(id: String, bounds: Rect) {
        if (homeOverviewAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun removeHomeOverviewAvatar(id: String) {
        if (homeOverviewAvatars.remove(id) != null) invalidate()
    }

    fun homeOverviewAvatarFor(id: String): Rect? = homeOverviewAvatars[id]

    fun updateVirtualUserAvatars(
        sessionIds: List<String>,
        visibleItems: List<LeftRailVisibleSessionItem>,
        viewport: Rect,
        fallbackStepPx: Float
    ) {
        var changed = false
        forEachLeftRailVirtualSessionAvatarBounds(
            sessionIds = sessionIds,
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = fallbackStepPx
        ) { id, bounds ->
            changed = retainedUserAvatars.updateVirtualIfVisuallyChanged(id, bounds, viewport) || changed
        }
        if (changed) invalidate()
    }

    fun userAvatarFor(id: String): Rect? {
        val bounds = userAvatars[id] ?: retainedUserAvatars[id]
        return bounds?.pinnedVerticallyTo(userViewport)
    }

    fun updateUserViewport(bounds: Rect) {
        if (userViewport != bounds) {
            userViewport = bounds
            invalidate()
        }
    }

    fun updateGroupMemberAvatar(id: String, bounds: Rect) {
        if (groupMemberAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun removeGroupMemberAvatar(id: String) {
        if (groupMemberAvatars.remove(id) != null) invalidate()
    }

    fun updateAccountAvatar(id: String, bounds: Rect) {
        val changed = accountAvatars.updateIfChanged(id, bounds) or
            retainedAccountAvatars.updateIfChanged(id, bounds)
        if (changed) invalidate()
    }

    fun updateSelectedAccountAvatar(id: String, bounds: Rect) {
        if (retainedAccountAvatars.updateIfChanged(id, bounds)) invalidate()
    }

    fun updateVirtualAccountAvatars(
        accountIds: List<String>,
        visibleItems: List<RightRailVisibleAccountItem>,
        viewport: Rect,
        fallbackStepPx: Float
    ) {
        var changed = false
        forEachRightRailVirtualAccountAvatarBounds(
            accountIds = accountIds,
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = fallbackStepPx
        ) { id, bounds ->
            changed = retainedAccountAvatars.updateVirtualIfVisuallyChanged(id, bounds, viewport) || changed
        }
        if (changed) invalidate()
    }

    fun accountAvatarFor(id: String): Rect? {
        val bounds = accountAvatars[id] ?: retainedAccountAvatars[id]
        return bounds?.pinnedVerticallyTo(accountViewport)
    }

    fun accountAvatarEdgeFor(id: String): ChatConnectorViewportEdge? {
        val bounds = accountAvatars[id] ?: retainedAccountAvatars[id] ?: return null
        val viewport = accountViewport ?: return null
        return when {
            bounds.center.y < viewport.top -> ChatConnectorViewportEdge.Above
            bounds.center.y > viewport.bottom -> ChatConnectorViewportEdge.Below
            else -> null
        }
    }

    fun removeAccountAvatar(id: String) {
        if (accountAvatars.remove(id) != null) invalidate()
    }

    fun updateAccountViewport(bounds: Rect) {
        if (accountViewport != bounds) {
            accountViewport = bounds
            invalidate()
        }
    }

    fun updateMessageBubble(id: String, bounds: Rect) {
        if (messageBubbles.updateIfChanged(id, bounds)) invalidate()
    }

    fun retainMessageBounds(activeMessageIds: Set<String>) {
        var changed = false
        messageBubbles.keys
            .filterNot { id -> id in activeMessageIds }
            .forEach { id -> changed = messageBubbles.remove(id) != null || changed }
        groupMemberAvatars.keys
            .filterNot { id -> id in activeMessageIds }
            .forEach { id -> changed = groupMemberAvatars.remove(id) != null || changed }
        if (changed) invalidate()
    }

    fun updateGroupThreadAvatar(id: String, bounds: Rect) {
        groupThreadAvatarId = id
        if (groupThreadAvatar != bounds) {
            groupThreadAvatar = bounds
            invalidate()
        }
    }

    fun removeGroupThreadAvatar(id: String) {
        if (groupThreadAvatarId == id) {
            groupThreadAvatarId = null
            groupThreadAvatar = null
            invalidate()
        }
    }

    fun updatePrivateThreadAvatar(id: String, bounds: Rect) {
        privateThreadAvatarId = id
        if (privateThreadAvatar != bounds) {
            privateThreadAvatar = bounds
            invalidate()
        }
    }

    fun privateThreadAvatarFor(id: String): Rect? {
        return privateThreadAvatar.takeIf { privateThreadAvatarId == id }
    }

    fun removePrivateThreadAvatar(id: String) {
        if (privateThreadAvatarId == id) {
            clearPrivateThreadAvatar()
        }
    }

    fun clearPrivateThreadAvatar() {
        privateThreadAvatarId = null
        if (privateThreadAvatar != null) {
            privateThreadAvatar = null
            invalidate()
        }
    }

    fun updateMessageViewport(bounds: Rect) {
        if (messageViewport != bounds) {
            messageViewport = bounds
            invalidate()
        }
    }
}

internal fun rightRailVirtualAccountAvatarBounds(
    accountIds: List<String>,
    visibleItems: List<RightRailVisibleAccountItem>,
    viewport: Rect,
    fallbackStepPx: Float
): Map<String, Rect> {
    if (accountIds.isEmpty() || visibleItems.isEmpty()) return emptyMap()
    return buildMap {
        forEachRightRailVirtualAccountAvatarBounds(
            accountIds = accountIds,
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = fallbackStepPx
        ) { id, bounds -> put(id, bounds) }
    }
}

private inline fun forEachRightRailVirtualAccountAvatarBounds(
    accountIds: List<String>,
    visibleItems: List<RightRailVisibleAccountItem>,
    viewport: Rect,
    fallbackStepPx: Float,
    action: (String, Rect) -> Unit
) {
    if (accountIds.isEmpty() || visibleItems.isEmpty()) return
    var anchor: RightRailVisibleAccountItem? = null
    var nextAnchor: RightRailVisibleAccountItem? = null
    visibleItems.forEach { item ->
        if (item.index !in accountIds.indices || item.size <= 0) return@forEach
        val currentAnchor = anchor
        when {
            currentAnchor == null || item.index < currentAnchor.index -> {
                nextAnchor = currentAnchor
                anchor = item
            }
            item.index > currentAnchor.index &&
                (nextAnchor == null || item.index < nextAnchor!!.index) -> {
                nextAnchor = item
            }
        }
    }
    val resolvedAnchor = anchor ?: return
    val anchorSize = resolvedAnchor.size.toFloat()
    val anchorCenterY = viewport.top + resolvedAnchor.offset + anchorSize / 2f
    val stepPx = nextAnchor
        ?.let { next ->
            val indexDelta = next.index - resolvedAnchor.index
            if (indexDelta == 0) null else {
                val firstCenterY = resolvedAnchor.offset + resolvedAnchor.size / 2f
                val secondCenterY = next.offset + next.size / 2f
                (secondCenterY - firstCenterY) / indexDelta
            }
        }
        ?.takeIf { step -> step != 0f }
        ?: fallbackStepPx.takeIf { step -> step != 0f }
        ?: return
    val left = viewport.right - anchorSize
    val right = viewport.right

    accountIds.forEachIndexed { index, id ->
        val centerY = anchorCenterY + stepPx * (index - resolvedAnchor.index)
        action(
            id,
            Rect(
                left = left,
                top = centerY - anchorSize / 2f,
                right = right,
                bottom = centerY + anchorSize / 2f
            )
        )
    }
}

internal fun rightRailPinnedSelectedAccountEdge(
    accountIds: List<String>,
    selectedAccountId: String?,
    visibleItems: List<RightRailVisibleAccountItem>,
    viewportHeightPx: Float,
    fallbackStepPx: Float,
    reverseLayout: Boolean = false
): RailPinnedAvatarEdge? {
    val selectedId = selectedAccountId?.takeIf { id -> id.isNotBlank() } ?: return null
    val selectedIndex = accountIds.indexOf(selectedId)
    if (selectedIndex < 0 || visibleItems.isEmpty()) return null
    val visibleIndices = visibleItems
        .asSequence()
        .map { item -> item.index }
        .filter { index -> index in accountIds.indices }
        .toList()
    if (visibleIndices.isNotEmpty()) {
        val firstVisibleIndex = visibleIndices.minOrNull() ?: return null
        val lastVisibleIndex = visibleIndices.maxOrNull() ?: return null
        // reverseLayout 下索引越小越靠近底部，越大越靠近顶部。
        if (selectedIndex < firstVisibleIndex) return RailPinnedAvatarEdge.Bottom
        if (selectedIndex > lastVisibleIndex) return RailPinnedAvatarEdge.Top
    }
    val viewport = Rect(0f, 0f, rightRailAvatarSizeDp().toFloat(), viewportHeightPx)
    var selectedBounds: Rect? = null
    forEachRightRailVirtualAccountAvatarBounds(
        accountIds = accountIds,
        visibleItems = visibleItems,
        viewport = viewport,
        fallbackStepPx = abs(fallbackStepPx)
    ) { id, bounds ->
        if (id == selectedId) selectedBounds = bounds
    }
    val resolvedSelectedBounds = selectedBounds ?: return null
    return resolvedSelectedBounds.pinnedAvatarEdgeForViewport(viewport)
}

internal fun leftRailVirtualSessionAvatarBounds(
    sessionIds: List<String>,
    visibleItems: List<LeftRailVisibleSessionItem>,
    viewport: Rect,
    fallbackStepPx: Float
): Map<String, Rect> {
    if (sessionIds.isEmpty() || visibleItems.isEmpty()) return emptyMap()
    return buildMap {
        forEachLeftRailVirtualSessionAvatarBounds(
            sessionIds = sessionIds,
            visibleItems = visibleItems,
            viewport = viewport,
            fallbackStepPx = fallbackStepPx
        ) { id, bounds -> put(id, bounds) }
    }
}

private inline fun forEachLeftRailVirtualSessionAvatarBounds(
    sessionIds: List<String>,
    visibleItems: List<LeftRailVisibleSessionItem>,
    viewport: Rect,
    fallbackStepPx: Float,
    action: (String, Rect) -> Unit
) {
    if (sessionIds.isEmpty() || visibleItems.isEmpty()) return
    var anchor: LeftRailVisibleSessionItem? = null
    var nextAnchor: LeftRailVisibleSessionItem? = null
    visibleItems.forEach { item ->
        if (item.index !in sessionIds.indices || item.size <= 0) return@forEach
        val currentAnchor = anchor
        when {
            currentAnchor == null || item.index < currentAnchor.index -> {
                nextAnchor = currentAnchor
                anchor = item
            }
            item.index > currentAnchor.index &&
                (nextAnchor == null || item.index < nextAnchor!!.index) -> {
                nextAnchor = item
            }
        }
    }
    val resolvedAnchor = anchor ?: return
    val anchorSize = resolvedAnchor.size.toFloat()
    val anchorCenterY = viewport.top + resolvedAnchor.offset + anchorSize / 2f
    val stepPx = nextAnchor
        ?.let { next ->
            val indexDelta = next.index - resolvedAnchor.index
            if (indexDelta == 0) null else {
                val firstCenterY = resolvedAnchor.offset + resolvedAnchor.size / 2f
                val secondCenterY = next.offset + next.size / 2f
                (secondCenterY - firstCenterY) / indexDelta
            }
        }
        ?.takeIf { step -> step != 0f }
        ?: fallbackStepPx.takeIf { step -> step != 0f }
        ?: return
    val left = viewport.left
    val right = viewport.left + anchorSize

    sessionIds.forEachIndexed { index, id ->
        val centerY = anchorCenterY + stepPx * (index - resolvedAnchor.index)
        action(
            id,
            Rect(
                left = left,
                top = centerY - anchorSize / 2f,
                right = right,
                bottom = centerY + anchorSize / 2f
            )
        )
    }
}

internal fun leftRailPinnedSelectedAvatarEdge(
    sessionIds: List<String>,
    selectedSessionId: String?,
    visibleItems: List<LeftRailVisibleSessionItem>,
    viewportHeightPx: Float,
    fallbackStepPx: Float
): RailPinnedAvatarEdge? {
    val selectedId = selectedSessionId?.takeIf { id -> id.isNotBlank() } ?: return null
    val viewport = Rect(0f, 0f, leftRailAvatarSizeDp().toFloat(), viewportHeightPx)
    var selectedBounds: Rect? = null
    forEachLeftRailVirtualSessionAvatarBounds(
        sessionIds = sessionIds,
        visibleItems = visibleItems,
        viewport = viewport,
        fallbackStepPx = fallbackStepPx
    ) { id, bounds ->
        if (id == selectedId) selectedBounds = bounds
    }
    val resolvedSelectedBounds = selectedBounds ?: return null
    return resolvedSelectedBounds.pinnedAvatarEdgeForViewport(viewport)
}

private fun Rect.pinnedAvatarEdgeForViewport(viewport: Rect): RailPinnedAvatarEdge? {
    return when {
        top < viewport.top -> RailPinnedAvatarEdge.Top
        bottom > viewport.bottom -> RailPinnedAvatarEdge.Bottom
        else -> null
    }
}

private fun Rect.pinnedVerticallyTo(viewport: Rect?): Rect {
    viewport ?: return this
    val pinnedCenterY = center.y.coerceIn(viewport.top, viewport.bottom)
    if (pinnedCenterY == center.y) return this
    val halfHeight = height / 2f
    return Rect(
        left = left,
        top = pinnedCenterY - halfHeight,
        right = right,
        bottom = pinnedCenterY + halfHeight
    )
}

private fun MutableMap<String, Rect>.updateIfChanged(id: String, bounds: Rect): Boolean {
    if (this[id] == bounds) return false
    this[id] = bounds
    return true
}

private fun MutableMap<String, Rect>.updateVirtualIfVisuallyChanged(
    id: String,
    bounds: Rect,
    viewport: Rect
): Boolean {
    val previousVisibleBounds = this[id]?.pinnedVerticallyTo(viewport)
    this[id] = bounds
    return previousVisibleBounds != bounds.pinnedVerticallyTo(viewport)
}

internal data class ConnectorTargetKey(
    val target: FloatingChatConnectionTarget,
    val targetId: String,
    val lane: ConnectorAvatarLane
)

internal data class ConnectorViewportEdgeState(
    val hasAbove: Boolean = false,
    val hasBelow: Boolean = false
)

internal data class ConnectorOffscreenIndex(
    val beforeByIndex: List<Set<ConnectorTargetKey>>,
    val afterByIndex: List<Set<ConnectorTargetKey>>
) {
    fun targetsAbove(firstVisibleIndex: Int): Set<ConnectorTargetKey> {
        if (beforeByIndex.isEmpty()) return emptySet()
        return beforeByIndex[firstVisibleIndex.coerceIn(0, beforeByIndex.lastIndex)]
    }

    fun targetsBelow(lastVisibleIndex: Int): Set<ConnectorTargetKey> {
        if (afterByIndex.isEmpty()) return emptySet()
        return afterByIndex[lastVisibleIndex.coerceIn(0, afterByIndex.lastIndex)]
    }

    companion object {
        fun fromMessages(
            messages: List<FloatingChatMessage>,
            selection: ChatThreadSelection,
            selectedAccountId: String,
            homeOverviewVisible: Boolean = false,
            groupMemberAvatarsVisible: Boolean
        ): ConnectorOffscreenIndex {
            if (messages.isEmpty()) {
                return ConnectorOffscreenIndex(emptyList(), emptyList())
            }

            val keysByIndex = messages.map { message ->
                offscreenConnectorTargetKey(
                    message = message,
                    selection = selection,
                    selectedAccountId = selectedAccountId,
                    homeOverviewVisible = homeOverviewVisible,
                    groupMemberAvatarsVisible = groupMemberAvatarsVisible
                )
            }
            val beforeByIndex = MutableList(messages.size) { emptySet<ConnectorTargetKey>() }
            val seenBefore = linkedSetOf<ConnectorTargetKey>()
            keysByIndex.forEachIndexed { index, key ->
                beforeByIndex[index] = seenBefore.toSet()
                if (key != null) seenBefore += key
            }

            val afterByIndex = MutableList(messages.size) { emptySet<ConnectorTargetKey>() }
            val seenAfter = linkedSetOf<ConnectorTargetKey>()
            for (index in keysByIndex.lastIndex downTo 0) {
                afterByIndex[index] = seenAfter.toSet()
                keysByIndex[index]?.let { key -> seenAfter += key }
            }

            return ConnectorOffscreenIndex(
                beforeByIndex = beforeByIndex,
                afterByIndex = afterByIndex
            )
        }
    }
}

internal fun createGroupMemberMessageConnectorBranch(
    avatarBounds: Rect,
    bubbleBounds: Rect,
    layerBounds: Rect
): ChatConnectorBranch {
    val avatarAnchor = avatarBounds.rightCenterIn(layerBounds)
    val bubbleAnchor = Offset(
        x = bubbleBounds.left - layerBounds.left -
            imModuleConnectionLineBubbleGapPx() +
            imModuleConnectionLineBubbleOverlapPx(),
        y = avatarAnchor.y
    )
    return ChatConnectorBranch(
        start = avatarAnchor,
        end = bubbleAnchor
    )
}

internal fun homeOverviewFallbackConnectorAvatarBounds(
    bubbleBounds: List<Rect>,
    layerBounds: Rect,
    visibleRootBounds: Rect,
    target: FloatingChatConnectionTarget
): Rect? {
    if (bubbleBounds.isEmpty()) return null
    val centerY = bubbleBounds
        .map { bounds -> bounds.center.y }
        .average()
        .toFloat()
        .coerceIn(visibleRootBounds.top, visibleRootBounds.bottom)
    val size = imModuleConnectionLineStrokeWidthPx().coerceAtLeast(1f)
    val offset = imModuleConnectionLineHorizontalOffsetPx()
    return if (target == FloatingChatConnectionTarget.Account) {
        Rect(
            left = visibleRootBounds.right + offset,
            top = centerY - size / 2f,
            right = visibleRootBounds.right + offset + size,
            bottom = centerY + size / 2f
        )
    } else {
        val avatarRight = visibleRootBounds.left -
            (leftRailTouchableWidthDp() - leftRailAvatarSizeDp()).toFloat()
        Rect(
            left = avatarRight - size,
            top = centerY - size / 2f,
            right = avatarRight,
            bottom = centerY + size / 2f
        )
    }
}

internal fun offscreenConnectorEdges(
    index: ConnectorOffscreenIndex,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int
): Map<ConnectorTargetKey, ConnectorViewportEdgeState> {
    val states = linkedMapOf<ConnectorTargetKey, ConnectorViewportEdgeState>()
    updateOffscreenConnectorEdges(
        index = index,
        firstVisibleIndex = firstVisibleIndex,
        lastVisibleIndex = lastVisibleIndex,
        destination = states
    )
    return states
}

internal fun updateOffscreenConnectorEdges(
    index: ConnectorOffscreenIndex,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    destination: MutableMap<ConnectorTargetKey, ConnectorViewportEdgeState>
) {
    destination.clear()
    index.targetsAbove(firstVisibleIndex).forEach { key ->
        destination[key] = (destination[key] ?: ConnectorViewportEdgeState()).copy(hasAbove = true)
    }
    index.targetsBelow(lastVisibleIndex).forEach { key ->
        destination[key] = (destination[key] ?: ConnectorViewportEdgeState()).copy(hasBelow = true)
    }
}

internal fun FloatingChatMessage.toConnectorTargetKey(
    selection: ChatThreadSelection,
    selectedAccountId: String,
    groupMemberAvatarsVisible: Boolean
): ConnectorTargetKey? {
    val target = connectionTarget
    if (target == FloatingChatConnectionTarget.None) return null
    if (selection is ChatThreadSelection.Private) {
        return ConnectorTargetKey(
            target = target,
            targetId = when (target) {
                FloatingChatConnectionTarget.User -> selection.contactId
                FloatingChatConnectionTarget.Account -> selectedAccountId
                FloatingChatConnectionTarget.None -> return null
            },
            lane = connectorAvatarLaneFor(selection, target)
        )
    }
    val targetId = connectionTargetId ?: return null
    if (
        selection.isGroupThread() &&
        target == FloatingChatConnectionTarget.User
    ) {
        return if (groupMemberAvatarsVisible) {
            ConnectorTargetKey(
                target = target,
                targetId = id,
                lane = ConnectorAvatarLane.GroupMember
            )
        } else {
            ConnectorTargetKey(
                target = target,
                targetId = selection.groupConnectorId(),
                lane = ConnectorAvatarLane.Session
            )
        }
    }
    return ConnectorTargetKey(
        target = target,
        targetId = targetId,
        lane = connectorAvatarLaneFor(selection, target)
    )
}

internal fun FloatingChatMessage.toHomeOverviewConnectorTargetKey(
    connectorGroupId: String? = null
): ConnectorTargetKey? {
    if (connectionTarget != FloatingChatConnectionTarget.User) return null
    return ConnectorTargetKey(
        target = FloatingChatConnectionTarget.User,
        targetId = connectorGroupId ?: homeOverviewConnectorKeyDebugId(this) ?: return null,
        lane = ConnectorAvatarLane.Session
    )
}

internal fun FloatingChatMessage.toHomeOverviewConnectorSourceKey(): ConnectorTargetKey? {
    if (connectionTarget != FloatingChatConnectionTarget.User) return null
    return ConnectorTargetKey(
        target = FloatingChatConnectionTarget.User,
        targetId = homeOverviewConnectorSourceKeyDebugId(this) ?: return null,
        lane = ConnectorAvatarLane.Session
    )
}

private fun FloatingChatMessage.toOffscreenConnectorTargetKey(
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean
): ConnectorTargetKey? {
    if (homeOverviewVisible) {
        return toHomeOverviewConnectorTargetKey()
    }
    if (
        selection.isGroupThread() &&
        groupMemberAvatarsVisible &&
        connectionTarget == FloatingChatConnectionTarget.User
    ) {
        return ConnectorTargetKey(
            target = FloatingChatConnectionTarget.User,
            targetId = selection.groupConnectorId(),
            lane = ConnectorAvatarLane.Session
        )
    }
    return toConnectorTargetKey(selection, selectedAccountId, groupMemberAvatarsVisible)
}
