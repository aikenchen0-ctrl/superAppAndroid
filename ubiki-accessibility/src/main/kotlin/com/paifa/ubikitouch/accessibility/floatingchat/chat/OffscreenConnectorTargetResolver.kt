package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal fun buildOffscreenConnectorIndex(
    messages: List<FloatingChatMessage>,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean
): ConnectorOffscreenIndex {
    if (messages.isEmpty()) return ConnectorOffscreenIndex(emptyList(), emptyList())
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
    return ConnectorOffscreenIndex(beforeByIndex, afterByIndex)
}

/**
 * Resolves offscreen connector targets without depending on the large
 * ConnectorCoordinateStateKt file during initial Compose composition.
 */
internal fun offscreenConnectorTargetKey(
    message: FloatingChatMessage,
    selection: ChatThreadSelection,
    selectedAccountId: String,
    homeOverviewVisible: Boolean,
    groupMemberAvatarsVisible: Boolean
): ConnectorTargetKey? {
    if (homeOverviewVisible) {
        if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
        return ConnectorTargetKey(
            target = FloatingChatConnectionTarget.User,
            targetId = homeOverviewConnectorKeyDebugId(message) ?: return null,
            lane = ConnectorAvatarLane.Session
        )
    }
    val target = message.connectionTarget
    if (target == FloatingChatConnectionTarget.None) return null
    if (selection is ChatThreadSelection.Private) {
        return ConnectorTargetKey(
            target = target,
            targetId = when (target) {
                FloatingChatConnectionTarget.User -> selection.contactId
                FloatingChatConnectionTarget.Account -> selectedAccountId
                FloatingChatConnectionTarget.None -> return null
            },
            lane = offscreenConnectorAvatarLaneFor(selection, target)
        )
    }
    val targetId = message.connectionTargetId ?: return null
    if (selection.isGroupThread() && target == FloatingChatConnectionTarget.User) {
        return if (groupMemberAvatarsVisible) {
            ConnectorTargetKey(target, message.id, ConnectorAvatarLane.GroupMember)
        } else {
            ConnectorTargetKey(target, selection.groupConnectorId(), ConnectorAvatarLane.Session)
        }
    }
    return ConnectorTargetKey(target, targetId, offscreenConnectorAvatarLaneFor(selection, target))
}

private fun offscreenConnectorAvatarLaneFor(
    selection: ChatThreadSelection,
    target: FloatingChatConnectionTarget
): ConnectorAvatarLane {
    return when (target) {
        FloatingChatConnectionTarget.Account -> ConnectorAvatarLane.Account
        FloatingChatConnectionTarget.User -> {
            if (selection.isGroupThread()) ConnectorAvatarLane.GroupMember
            else ConnectorAvatarLane.Session
        }
        FloatingChatConnectionTarget.None -> ConnectorAvatarLane.Session
    }
}
