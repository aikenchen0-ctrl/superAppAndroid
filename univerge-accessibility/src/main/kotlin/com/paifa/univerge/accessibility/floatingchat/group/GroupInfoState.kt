package com.paifa.univerge.accessibility.floatingchat.group

import com.paifa.univerge.accessibility.floatingchat.chat.groupMemberRailContacts
import com.paifa.univerge.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatMessage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal sealed interface GroupInfoMemberPreviewCell {
    data class Member(val index: Int) : GroupInfoMemberPreviewCell
    data object Empty : GroupInfoMemberPreviewCell
    data object More : GroupInfoMemberPreviewCell
    data object Add : GroupInfoMemberPreviewCell
}

internal fun groupInfoMemberPreviewCells(memberCount: Int): List<GroupInfoMemberPreviewCell> {
    require(memberCount >= 0) { "memberCount cannot be negative" }
    val visibleMembers = memberCount.coerceAtMost(GroupInfoMemberPreviewCapacity)
    return buildList(GroupInfoPreviewCellCount) {
        repeat(visibleMembers) { index -> add(GroupInfoMemberPreviewCell.Member(index)) }
        add(GroupInfoMemberPreviewCell.More)
        add(GroupInfoMemberPreviewCell.Add)
    }
}

private const val GroupInfoPreviewColumns = 5
private const val GroupInfoPreviewRows = 3
private const val GroupInfoPreviewCellCount = GroupInfoPreviewColumns * GroupInfoPreviewRows
private const val GroupInfoMemberPreviewCapacity = GroupInfoPreviewCellCount - 2

internal data class GroupQrCodePayload(
    val imageUrl: String? = null,
    val content: String? = null
)

internal fun parseGroupQrCodePayload(element: JsonElement?): GroupQrCodePayload? {
    element ?: return null
    val imageUrl = findQrValue(element, QrImageKeys)
    val content = findQrValue(element, QrContentKeys)
    if (imageUrl != null || content != null) return GroupQrCodePayload(imageUrl, content)
    val primitive = (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    return if (primitive.startsWith("http://") || primitive.startsWith("https://") || primitive.startsWith("data:image/")) {
        GroupQrCodePayload(imageUrl = primitive)
    } else {
        GroupQrCodePayload(content = primitive)
    }
}

private fun findQrValue(element: JsonElement, keys: Set<String>): String? {
    val objectValue = element as? JsonObject ?: return null
    objectValue.entries.firstNotNullOfOrNull { (key, value) ->
        if (key.lowercase() !in keys) return@firstNotNullOfOrNull null
        (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }?.let { return it }
    return objectValue.values.firstNotNullOfOrNull { nested -> findQrValue(nested, keys) }
}

private val QrImageKeys = setOf("imageurl", "qrcodeurl", "qrurl", "downloadurl")
private val QrContentKeys = setOf("qrcontent", "qrcode", "content")
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
