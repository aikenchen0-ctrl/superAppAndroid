package com.paifa.ubikitouch.accessibility.floatingchat.group

import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupMemberRailContacts
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
internal fun groupInfoMemberCount(members: List<FloatingChatContact>): Int {
    return members.size
}

internal data class GroupInfoMemberGridItem(
    val member: FloatingChatContact? = null,
    val isAddAction: Boolean = false,
    val isRemoveAction: Boolean = false
) {
    val key: String
        get() = member?.id ?: when {
            isAddAction -> "add"
            isRemoveAction -> "remove"
            else -> "empty"
        }
}

internal fun groupInfoMembersForGroup(
    group: FloatingChatContact,
    contacts: List<FloatingChatContact>,
    messages: List<FloatingChatMessage>
): List<FloatingChatContact> {
    if (group.groupMemberContacts.isEmpty()) {
        return groupMemberRailContacts(contacts = contacts, messages = messages)
    }
    val knownContactsById = contacts.associateBy { contact -> contact.id }
    return group.groupMemberContacts.map { member ->
        knownContactsById[member.id]?.let { knownContact ->
            knownContact.copy(
                groupMemberIsOwner = knownContact.groupMemberIsOwner || member.groupMemberIsOwner,
                groupMemberIsAdmin = knownContact.groupMemberIsAdmin || member.groupMemberIsAdmin
            )
        } ?: member
    }
}

internal fun groupInfoCurrentMemberForRoute(
    weChatId: String?,
    members: List<FloatingChatContact>
): FloatingChatContact? {
    val targetWeChatId = weChatId?.takeIf { it.isNotBlank() } ?: return null
    return members.firstOrNull { member ->
        scrmFloatingContactConversationId(member.id) == targetWeChatId
    }
}

internal fun groupInviteCandidates(
    contacts: List<FloatingChatContact>,
    members: List<FloatingChatContact>
): List<FloatingChatContact> {
    val memberWxids = members.mapNotNull { member -> scrmFloatingContactConversationId(member.id) }.toSet()
    return contacts
        .filter { contact -> scrmFloatingContactConversationId(contact.id) !in memberWxids }
        .distinctBy { contact -> scrmFloatingContactConversationId(contact.id) ?: contact.id }
}

internal fun groupKickCandidates(
    members: List<FloatingChatContact>,
    currentMember: FloatingChatContact?
): List<FloatingChatContact> {
    val currentWxid = currentMember?.let { member -> scrmFloatingContactConversationId(member.id) }
    return members
        .filter { member -> scrmFloatingContactConversationId(member.id) != currentWxid }
        .distinctBy { member -> scrmFloatingContactConversationId(member.id) ?: member.id }
}

internal fun groupInfoMemberGridRows(
    members: List<FloatingChatContact>,
    columns: Int = 4,
    canManageMembers: Boolean = false
): List<List<GroupInfoMemberGridItem>> {
    require(columns > 0) { "columns must be positive" }
    val cells = members.map { member -> GroupInfoMemberGridItem(member = member) } +
        GroupInfoMemberGridItem(isAddAction = true) +
        if (canManageMembers) listOf(GroupInfoMemberGridItem(isRemoveAction = true)) else emptyList()
    return cells.chunked(columns).map { row ->
        row + List(columns - row.size) { GroupInfoMemberGridItem() }
    }
}

internal fun groupInfoCanManageMembers(currentMember: FloatingChatContact?): Boolean {
    return currentMember?.groupMemberIsOwner == true || currentMember?.groupMemberIsAdmin == true
}

internal fun groupInfoMemberManagementLabels(canManageMembers: Boolean): List<String> {
    return listOf("添加成员") + if (canManageMembers) listOf("移出成员") else emptyList()
}

internal fun groupInfoMemberIsFriend(
    member: FloatingChatContact,
    contacts: List<FloatingChatContact>
): Boolean {
    return contacts.any { contact -> contact.id == member.id }
}

internal fun groupInfoMemberPrimaryActionLabel(isFriend: Boolean): String {
    return if (isFriend) "发消息" else "添加到通讯录"
}

internal fun groupMemberAddFriendStatusText(
    loading: Boolean,
    status: String?,
    error: String?
): String? {
    error?.takeIf { it.isNotBlank() }?.let { return it }
    if (loading) return "正在发送好友申请"
    return status?.takeIf { it.isNotBlank() }
}
