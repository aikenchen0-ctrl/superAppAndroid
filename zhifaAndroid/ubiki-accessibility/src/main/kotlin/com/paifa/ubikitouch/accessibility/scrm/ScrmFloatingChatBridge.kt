package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.accessibility.floatingchat.media.normalizedRemoteImageUri
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.net.URLDecoder
import java.net.URLEncoder

private const val ScrmFloatingContactIdPrefix = "scrm-contact:"
private const val ScrmFloatingGroupIdPrefix = "scrm-group:"
private const val ScrmFloatingAccountIdPrefix = "scrm-account:"
private const val ScrmFloatingScopedThreadSeparator = "__"
private const val ScrmGroupAvatarMemberLimit = 9
private const val ScrmChatRoomMemberOwnerRole = 1
private const val ScrmChatRoomMemberAdminRole = 2

private val ScrmAvatarPalette = longArrayOf(
    0xFF1B9AAA,
    0xFFE07A5F,
    0xFF8E7DBE,
    0xFF2A9D8F,
    0xFFB56576,
    0xFFEF476F,
    0xFFFFB703,
    0xFF457B9D,
    0xFF118AB2,
    0xFF3A86FF
)

internal data class ScrmFloatingAccountRoute(
    val deviceUuid: String,
    val weChatId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

internal data class ScrmFloatingAccountConversation(
    val deviceUuid: String,
    val weChatId: String,
    val contacts: List<ScrmContact>,
    val chatRooms: List<ScrmChatRoom> = emptyList(),
    val chatRoomMembers: Map<String, List<ScrmChatRoomMember>> = emptyMap()
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

internal fun scrmFloatingContactId(conversationId: String): String {
    require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
    return ScrmFloatingContactIdPrefix + scrmEncodeIdPart(conversationId)
}

internal fun scrmFloatingGroupId(conversationId: String): String {
    require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
    return ScrmFloatingGroupIdPrefix + scrmEncodeIdPart(conversationId)
}

internal fun scrmFloatingScopedThreadId(accountId: String, threadId: String): String {
    require(accountId.isNotBlank()) { "accountId cannot be blank" }
    require(threadId.isNotBlank()) { "threadId cannot be blank" }
    return "$accountId$ScrmFloatingScopedThreadSeparator$threadId"
}

internal fun scrmFloatingContactConversationId(contactId: String): String? {
    val unscoped = contactId.substringAfter(
        delimiter = ScrmFloatingScopedThreadSeparator,
        missingDelimiterValue = contactId
    )
    val encoded = when {
        unscoped.startsWith(ScrmFloatingContactIdPrefix) -> {
            unscoped.removePrefix(ScrmFloatingContactIdPrefix)
        }
        unscoped.startsWith(ScrmFloatingGroupIdPrefix) -> {
            unscoped.removePrefix(ScrmFloatingGroupIdPrefix)
        }
        else -> return null
    }
    return scrmDecodeIdPart(encoded)
        ?.takeIf { it.isNotBlank() }
}

internal fun scrmFloatingAccountId(deviceUuid: String, weChatId: String): String {
    require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
    require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    return ScrmFloatingAccountIdPrefix +
        scrmEncodeIdPart(deviceUuid) +
        ":" +
        scrmEncodeIdPart(weChatId)
}

internal fun scrmFloatingAccountRouteForContactId(contactId: String): ScrmFloatingAccountRoute? {
    if (!contactId.startsWith(ScrmFloatingAccountIdPrefix)) return null
    val payload = contactId.removePrefix(ScrmFloatingAccountIdPrefix)
    val separatorIndex = payload.indexOf(':')
    if (separatorIndex <= 0 || separatorIndex == payload.lastIndex) return null
    val deviceUuid = scrmDecodeIdPart(payload.substring(0, separatorIndex)) ?: return null
    val weChatId = scrmDecodeIdPart(payload.substring(separatorIndex + 1)) ?: return null
    return ScrmFloatingAccountRoute(
        deviceUuid = deviceUuid,
        weChatId = weChatId
    )
}

internal fun scrmFloatingAccountRouteForSelection(
    selectedAccountId: String?,
    fallbackDeviceUuid: String,
    fallbackWeChatId: String
): ScrmFloatingAccountRoute {
    return selectedAccountId
        ?.takeIf { it.isNotBlank() }
        ?.let(::scrmFloatingAccountRouteForContactId)
        ?: ScrmFloatingAccountRoute(
            deviceUuid = fallbackDeviceUuid,
            weChatId = fallbackWeChatId
        )
}

internal fun scrmFloatingChatConversation(
    base: FloatingChatConversation,
    contacts: List<ScrmContact>,
    accountConversations: List<ScrmFloatingAccountConversation> = emptyList(),
    accounts: List<ScrmWechatAccount>,
    devices: List<ScrmDevice>,
    selectedDeviceUuid: String,
    selectedWeChatId: String
): FloatingChatConversation {
    val floatingAccounts = scrmFloatingAccountContacts(
        accounts = accounts,
        devices = devices,
        accountConversations = accountConversations,
        selectedDeviceUuid = selectedDeviceUuid,
        selectedWeChatId = selectedWeChatId
    )
    val selectedAccount = floatingAccounts.firstOrNull { account -> account.selected }
        ?: floatingAccounts.firstOrNull()
    val scopedContacts = scrmFloatingScopedContacts(
        fallbackContacts = contacts,
        accountConversations = accountConversations,
        selectedDeviceUuid = selectedDeviceUuid,
        selectedWeChatId = selectedWeChatId
    )
    val scopedGroups = scrmFloatingScopedChatRooms(accountConversations)

    return base.copy(
        peerName = "SCRM Contacts",
        accountName = selectedAccount?.name ?: selectedWeChatId.ifBlank { base.accountName },
        contacts = scopedContacts,
        accountContacts = floatingAccounts,
        messages = emptyList(),
        homeUnreadDemoMessages = scrmUnreadDemoMessages(scopedContacts, scopedGroups),
        groupContacts = scopedGroups
    )
}

private fun scrmUnreadDemoMessages(
    contacts: List<FloatingChatContact>,
    groups: List<FloatingChatContact>
): List<FloatingChatMessage> {
    val routes = contacts.take(10) + groups.take(4)
    if (routes.isEmpty()) return emptyList()
    val texts = listOf(
        "我把今天的内容整理好了，方便时帮我看一下。",
        "这个细节想和你确认一下，确认后我就继续处理。",
        "对方还在等回复，你看到后回我一句就行。",
        "刚补充了一点说明，麻烦确认是否需要调整。",
        "后续安排我先预留着，等你确认后再通知大家。"
    )
    return (0 until ScrmUnreadDemoMessageCount).map { index ->
        val route = routes[index % routes.size]
        FloatingChatMessage(
            id = "scrm-unread-demo-${route.id}-$index",
            type = FloatingChatMessageType.Text,
            text = texts[index % texts.size],
            fromMe = false,
            senderName = route.name,
            time = "${10 + index / 15}:${((index * 4 + 7) % 60).toString().padStart(2, '0')}",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = route.id,
            threadContactId = route.id
        )
    }
}

private const val ScrmUnreadDemoMessageCount = 30

private fun scrmFloatingScopedContacts(
    fallbackContacts: List<ScrmContact>,
    accountConversations: List<ScrmFloatingAccountConversation>,
    selectedDeviceUuid: String,
    selectedWeChatId: String
): List<FloatingChatContact> {
    val source = accountConversations.ifEmpty {
        if (selectedDeviceUuid.isBlank() || selectedWeChatId.isBlank()) {
            emptyList()
        } else {
            listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = selectedDeviceUuid,
                    weChatId = selectedWeChatId,
                    contacts = fallbackContacts
                )
            )
        }
    }
    return scrmDistinctFloatingContactsById(source.flatMap { account ->
        val accountId = scrmFloatingAccountId(account.deviceUuid, account.weChatId)
        account.contacts.mapNotNull { contact ->
            scrmFloatingContact(contact, accountId)
        }
    })
}

private fun scrmFloatingContact(contact: ScrmContact, accountId: String): FloatingChatContact? {
    if (contact.isDeleted) return null
    val conversationId = contact.wxid?.takeIf { it.isNotBlank() }
        ?: contact.friendNo?.takeIf { it.isNotBlank() }
        ?: return null
    val displayName = contact.displayName.trim().ifBlank { conversationId }
    return FloatingChatContact(
        id = scrmFloatingScopedThreadId(accountId, scrmFloatingContactId(conversationId)),
        name = displayName,
        initials = displayName.take(2).ifBlank { "WX" },
        description = "WeChat friend / $conversationId",
        avatarColor = scrmStableColor(conversationId),
        avatarUrl = normalizedRemoteImageUri(contact.avatar),
        online = contact.isBlocked == 0
    )
}

private fun scrmFloatingScopedChatRooms(
    accountConversations: List<ScrmFloatingAccountConversation>
): List<FloatingChatContact> {
    return scrmDistinctFloatingContactsById(accountConversations.flatMap { account ->
        val accountId = scrmFloatingAccountId(account.deviceUuid, account.weChatId)
        account.chatRooms.mapNotNull { chatRoom ->
            val conversationId = chatRoom.chatRoomId?.takeIf { it.isNotBlank() }
            scrmFloatingChatRoom(
                chatRoom = chatRoom,
                accountId = accountId,
                members = conversationId?.let { account.chatRoomMembers[it] }.orEmpty()
            )
        }
    })
}

private fun scrmDistinctFloatingContactsById(
    contacts: List<FloatingChatContact>
): List<FloatingChatContact> {
    val mergedById = linkedMapOf<String, FloatingChatContact>()
    contacts.forEach { contact ->
        val existing = mergedById[contact.id]
        mergedById[contact.id] = if (existing == null) {
            contact
        } else {
            scrmMergeFloatingContact(existing, contact)
        }
    }
    return mergedById.values.toList()
}

private fun scrmMergeFloatingContact(
    existing: FloatingChatContact,
    candidate: FloatingChatContact
): FloatingChatContact {
    return existing.copy(
        name = existing.name.ifBlank { candidate.name },
        initials = existing.initials.ifBlank { candidate.initials },
        description = existing.description.ifBlank { candidate.description },
        selected = existing.selected || candidate.selected,
        online = existing.online || candidate.online,
        avatarUrl = existing.avatarUrl ?: candidate.avatarUrl,
        groupMemberAvatarUrls = existing.groupMemberAvatarUrls.ifEmpty { candidate.groupMemberAvatarUrls },
        groupMemberIsOwner = existing.groupMemberIsOwner || candidate.groupMemberIsOwner,
        groupMemberIsAdmin = existing.groupMemberIsAdmin || candidate.groupMemberIsAdmin,
        groupMemberContacts = scrmDistinctFloatingContactsById(
            existing.groupMemberContacts + candidate.groupMemberContacts
        )
    )
}

private fun scrmFloatingChatRoom(
    chatRoom: ScrmChatRoom,
    accountId: String,
    members: List<ScrmChatRoomMember>
): FloatingChatContact? {
    if (chatRoom.isDeleted) return null
    val conversationId = chatRoom.chatRoomId?.takeIf { it.isNotBlank() } ?: return null
    val displayName = chatRoom.displayName.trim().ifBlank { conversationId }
    return FloatingChatContact(
        id = scrmFloatingScopedThreadId(accountId, scrmFloatingGroupId(conversationId)),
        name = displayName,
        initials = displayName.take(2).ifBlank { "群" },
        description = "${chatRoom.memberCount.coerceAtLeast(0)} members / $conversationId",
        avatarColor = scrmStableColor(conversationId),
        avatarUrl = normalizedRemoteImageUri(chatRoom.avatar),
        groupMemberAvatarUrls = members
            .asSequence()
            .mapNotNull { member -> normalizedRemoteImageUri(member.avatar) }
            .take(ScrmGroupAvatarMemberLimit)
            .toList(),
        groupMemberContacts = members.mapNotNull { member ->
            scrmFloatingChatRoomMember(member = member, accountId = accountId)
        },
        online = chatRoom.groupStatus >= 0
    )
}

private fun scrmFloatingChatRoomMember(
    member: ScrmChatRoomMember,
    accountId: String
): FloatingChatContact? {
    val conversationId = member.memberWxid?.takeIf { it.isNotBlank() } ?: return null
    val displayName = member.displayNameValue.trim().ifBlank { conversationId }
    return FloatingChatContact(
        id = scrmFloatingScopedThreadId(accountId, scrmFloatingContactId(conversationId)),
        name = displayName,
        initials = displayName.take(2).ifBlank { "群员" },
        description = "WeChat group member / $conversationId",
        avatarColor = scrmStableColor(conversationId),
        avatarUrl = normalizedRemoteImageUri(member.avatar),
        groupMemberIsOwner = member.isOwner || member.memberRole == ScrmChatRoomMemberOwnerRole,
        groupMemberIsAdmin = member.isAdmin || member.memberRole == ScrmChatRoomMemberAdminRole,
        online = true
    )
}

private fun scrmFloatingAccountContacts(
    accounts: List<ScrmWechatAccount>,
    devices: List<ScrmDevice>,
    accountConversations: List<ScrmFloatingAccountConversation>,
    selectedDeviceUuid: String,
    selectedWeChatId: String
): List<FloatingChatContact> {
    val derivedAvatarByWxid = scrmDerivedAvatarByWxid(accountConversations)
    val devicesByUuid = devices
        .mapNotNull { device ->
            val uuid = device.uuid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            uuid to device
        }
        .toMap()
    val devicesByWechatId = devices
        .mapNotNull { device ->
            val weChatId = device.weChatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            weChatId to device
        }
        .toMap()
    val routes = linkedMapOf<String, ScrmFloatingAccountDisplay>()

    accounts.forEach { account ->
        val weChatId = account.wxid?.takeIf { it.isNotBlank() } ?: return@forEach
        val deviceUuid = account.clientUuid?.takeIf { it.isNotBlank() }
            ?: devicesByWechatId[weChatId]?.uuid?.takeIf { it.isNotBlank() }
            ?: return@forEach
        val device = devicesByUuid[deviceUuid]
        routes["$deviceUuid/$weChatId"] = ScrmFloatingAccountDisplay(
            route = ScrmFloatingAccountRoute(deviceUuid = deviceUuid, weChatId = weChatId),
            name = account.nickname?.takeIf { it.isNotBlank() } ?: weChatId,
            online = device?.isOnline ?: false,
            accountStatus = account.accountStatus,
            avatarUrl = normalizedRemoteImageUri(account.avatar) ?: derivedAvatarByWxid[weChatId]
        )
    }

    devices.forEach { device ->
        val deviceUuid = device.uuid?.takeIf { it.isNotBlank() } ?: return@forEach
        val weChatId = device.weChatId?.takeIf { it.isNotBlank() } ?: return@forEach
        routes.putIfAbsent(
            "$deviceUuid/$weChatId",
            ScrmFloatingAccountDisplay(
                route = ScrmFloatingAccountRoute(deviceUuid = deviceUuid, weChatId = weChatId),
                name = weChatId,
                online = device.isOnline,
                accountStatus = null,
                avatarUrl = derivedAvatarByWxid[weChatId]
            )
        )
    }

    if (selectedDeviceUuid.isNotBlank() && selectedWeChatId.isNotBlank()) {
        routes.putIfAbsent(
            "$selectedDeviceUuid/$selectedWeChatId",
            ScrmFloatingAccountDisplay(
                route = ScrmFloatingAccountRoute(
                    deviceUuid = selectedDeviceUuid,
                    weChatId = selectedWeChatId
                ),
                name = selectedWeChatId,
                online = true,
                accountStatus = null,
                avatarUrl = derivedAvatarByWxid[selectedWeChatId]
            )
        )
    }

    val selectedKey = "$selectedDeviceUuid/$selectedWeChatId"
    return routes.map { (key, display) ->
        val route = display.route
        FloatingChatContact(
            id = scrmFloatingAccountId(
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId
            ),
            name = display.name,
            initials = display.name.take(2).ifBlank { "WX" },
            description = scrmAccountDescription(display),
            avatarColor = scrmStableColor(route.weChatId),
            avatarUrl = display.avatarUrl,
            selected = key == selectedKey,
            online = display.online
        )
    }.let { mapped ->
        if (mapped.isEmpty() || mapped.any { account -> account.selected }) {
            mapped
        } else {
            mapped.mapIndexed { index, account -> account.copy(selected = index == 0) }
        }
    }
}

private data class ScrmFloatingAccountDisplay(
    val route: ScrmFloatingAccountRoute,
    val name: String,
    val online: Boolean,
    val accountStatus: Int?,
    val avatarUrl: String?
)

private fun scrmDerivedAvatarByWxid(
    accountConversations: List<ScrmFloatingAccountConversation>
): Map<String, String> {
    val avatars = linkedMapOf<String, String>()
    accountConversations.forEach { account ->
        account.contacts.forEach { contact ->
            val wxid = contact.wxid?.takeIf { it.isNotBlank() }
                ?: contact.friendNo?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val avatar = normalizedRemoteImageUri(contact.avatar) ?: return@forEach
            avatars.putIfAbsent(wxid, avatar)
        }
        account.chatRoomMembers.values.flatten().forEach { member ->
            val wxid = member.memberWxid?.takeIf { it.isNotBlank() } ?: return@forEach
            val avatar = normalizedRemoteImageUri(member.avatar) ?: return@forEach
            avatars.putIfAbsent(wxid, avatar)
        }
    }
    return avatars
}

private fun scrmAccountDescription(display: ScrmFloatingAccountDisplay): String {
    val onlineText = if (display.online) "online" else "offline"
    val statusText = display.accountStatus?.let { status -> "status $status" } ?: "status unknown"
    return "$onlineText / $statusText / ${display.route.weChatId}"
}

private fun scrmStableColor(key: String): Long {
    val index = ((key.hashCode().toLong() and 0x7fffffff) % ScrmAvatarPalette.size).toInt()
    return ScrmAvatarPalette[index]
}

private fun scrmEncodeIdPart(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name())
}

private fun scrmDecodeIdPart(value: String): String? {
    return runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrNull()
}
