package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.accessibility.floatingchat.media.normalizedRemoteImageUri
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.net.URLEncoder

private const val ScrmFloatingContactIdPrefix = "scrm-contact:"
private const val ScrmFloatingGroupIdPrefix = "scrm-group:"
private const val ScrmFloatingAccountIdPrefix = "scrm-account:"
private const val ScrmFloatingScopedThreadSeparator = "__"
private const val ScrmGroupAvatarMemberLimit = 9
private const val ScrmChatRoomMemberOwnerRole = 1
private const val ScrmChatRoomMemberAdminRole = 2
private val ScrmMessageJson = Json { isLenient = true; ignoreUnknownKeys = true }

private val ScrmAvatarPalette = longArrayOf(
    0xFFFFB4AB,
    0xFFFFB77D,
    0xFFF8D67E,
    0xFFD7ED8E,
    0xFFA4E8B2,
    0xFF8EE6DD,
    0xFF9ECAFF,
    0xFFCBB8FF
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
    val chatRoomMembers: Map<String, List<ScrmChatRoomMember>> = emptyMap(),
    val messagesByConversation: Map<String, List<ScrmChatMessage>> = emptyMap(),
    val conversationWxidByBackendId: Map<Long, String> = emptyMap(),
    val nextSequence: Long = 0L
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
    val rawFloatingAccounts = scrmFloatingAccountContacts(
        accounts = accounts,
        devices = devices,
        accountConversations = accountConversations,
        selectedDeviceUuid = selectedDeviceUuid,
        selectedWeChatId = selectedWeChatId
    )
    val rawGroups = scrmFloatingScopedChatRooms(accountConversations)
    val scopedGroups = scrmApplyAvatarPalette(rawGroups)
    val scopedContacts = scrmApplyAvatarPalette(
        contacts = scrmFloatingScopedContacts(
            fallbackContacts = contacts,
            accountConversations = accountConversations,
            selectedDeviceUuid = selectedDeviceUuid,
            selectedWeChatId = selectedWeChatId
        ),
        startPosition = scrmAvatarVisibleEntryCount(scopedGroups)
    )
    val floatingAccounts = scrmApplyAvatarPalette(
        contacts = rawFloatingAccounts,
        startPosition = scrmAvatarVisibleEntryCount(scopedGroups) +
            scrmAvatarVisibleEntryCount(scopedContacts)
    )
    val selectedAccount = floatingAccounts.firstOrNull { account -> account.selected }
        ?: floatingAccounts.firstOrNull()
    val historyMessages = scrmFloatingHistoryMessages(accountConversations)

    return base.copy(
        peerName = "SCRM Contacts",
        accountName = selectedAccount?.name ?: selectedWeChatId.ifBlank { base.accountName },
        contacts = scopedContacts,
        accountContacts = floatingAccounts,
        messages = historyMessages,
        homeUnreadDemoMessages = scrmUnreadDemoMessages(scopedContacts, scopedGroups),
        groupContacts = scopedGroups
    )
}

private fun scrmFloatingHistoryMessages(
    accountConversations: List<ScrmFloatingAccountConversation>
): List<FloatingChatMessage> {
    return accountConversations.flatMap { account ->
        val accountId = scrmFloatingAccountId(account.deviceUuid, account.weChatId)
        account.messagesByConversation.flatMap { (conversationWxid, messages) ->
            val isGroup = conversationWxid.endsWith("@chatroom", ignoreCase = true)
            val threadId = scrmFloatingScopedThreadId(
                accountId,
                if (isGroup) scrmFloatingGroupId(conversationWxid)
                else scrmFloatingContactId(conversationWxid)
            )
            messages.mapNotNull { message ->
                val remoteId = message.messageId.takeIf { it > 0L }?.toString()
                    ?: message.messageServerId?.toString()
                    ?: message.clientMessageId
                    ?: message.localMessageId
                    ?: return@mapNotNull null
                val fromMe = message.direction == 1 || message.senderWxid == account.weChatId
                scrmFloatingMappedMessage(
                    remote = message,
                    id = "scrm-message:$accountId:$remoteId",
                    fromMe = fromMe,
                    senderName = if (fromMe) account.weChatId else message.senderWxid.orEmpty().ifBlank { conversationWxid },
                    time = message.createdAt.orEmpty().substringAfter('T').take(5),
                    connectionTarget = if (fromMe) FloatingChatConnectionTarget.Account else FloatingChatConnectionTarget.User,
                    connectionTargetId = threadId,
                    threadContactId = threadId
                )
            }
        }
    }
}

private fun scrmFloatingMappedMessage(
    remote: ScrmChatMessage,
    id: String,
    fromMe: Boolean,
    senderName: String,
    time: String,
    connectionTarget: FloatingChatConnectionTarget,
    connectionTargetId: String,
    threadContactId: String,
): FloatingChatMessage {
    val type = scrmFloatingMessageType(remote.messageType, remote.content)
    val text = scrmFloatingMessageText(type, remote.content)
    val system = remote.messageType == 10000 || remote.messageType == 10002
    return FloatingChatMessage(
        id = id,
        type = type,
        text = text,
        fromMe = fromMe,
        senderName = senderName,
        time = time,
        presentation = if (system) FloatingChatMessagePresentation.System else FloatingChatMessagePresentation.Bubble,
        connectionTarget = connectionTarget,
        connectionTargetId = connectionTargetId,
        threadContactId = threadContactId,
        detail = scrmFloatingMessageDetail(type, remote.content, remote.voiceText),
        resourceUrl = remote.media.firstOrNull()?.resolvedUrl
            ?: remote.extensions.firstNotNullOfOrNull { extension ->
                extension.value.takeIf { extension.key.lowercase() in ScrmMediaUrlKeys && it.isNotBlank() }
            }
            ?: scrmFloatingMessageUrl(remote.content),
        fileName = remote.media.firstOrNull()?.fileExtension?.takeIf { it.isNotBlank() },
        fileSizeLabel = remote.media.firstOrNull()?.fileSize?.takeIf { it > 0L }?.let(::scrmFormatFileSize),
        remoteMessageServerId = remote.messageServerId?.toString()
    )
}

/** Maps WeChat messageType values used by iOS to the Android read-only renderer. */
private fun scrmFloatingMessageType(messageType: Int, content: String): FloatingChatMessageType {
    val lower = content.lowercase()
    return when (messageType) {
        1 -> FloatingChatMessageType.Text
        3 -> FloatingChatMessageType.ImageThumbnail
        34 -> FloatingChatMessageType.Voice
        37, 42 -> FloatingChatMessageType.ContactLink
        43, 62 -> FloatingChatMessageType.VideoPreview
        47 -> FloatingChatMessageType.StickerGif
        48 -> FloatingChatMessageType.Location
        49 -> when {
            "<type>33</type>" in lower || "<type>36</type>" in lower -> FloatingChatMessageType.MiniProgramLink
            "<type>6</type>" in lower -> FloatingChatMessageType.FilePreview
            "<type>19</type>" in lower -> FloatingChatMessageType.ChatHistory
            "<type>2000</type>" in lower -> FloatingChatMessageType.Transfer
            "<type>2001</type>" in lower -> FloatingChatMessageType.RedPacket
            else -> FloatingChatMessageType.WebLink
        }
        50 -> FloatingChatMessageType.VoiceCall
        else -> FloatingChatMessageType.Text
    }
}

private fun scrmFloatingMessageText(type: FloatingChatMessageType, content: String): String {
    val extracted = scrmFloatingJsonText(content)
    if (type == FloatingChatMessageType.Text) return extracted ?: content.trim()
    if (!extracted.isNullOrBlank() && !extracted.startsWith("wxid_")) return extracted
    return when (type) {
        FloatingChatMessageType.ImageThumbnail, FloatingChatMessageType.CapturedPhoto -> "[图片]"
        FloatingChatMessageType.VideoPreview, FloatingChatMessageType.ChannelsVideo -> "[视频]"
        FloatingChatMessageType.Voice -> "[语音]"
        FloatingChatMessageType.StickerGif, FloatingChatMessageType.Emoji -> "[表情]"
        FloatingChatMessageType.Location, FloatingChatMessageType.LiveLocation -> "[位置]"
        FloatingChatMessageType.ContactLink, FloatingChatMessageType.GroupInvite -> "[名片]"
        FloatingChatMessageType.FilePreview -> "[文件]"
        FloatingChatMessageType.ChatHistory -> "[聊天记录]"
        FloatingChatMessageType.WebLink, FloatingChatMessageType.Article -> "[链接]"
        FloatingChatMessageType.VoiceCall -> "[语音通话]"
        FloatingChatMessageType.VideoCall -> "[视频通话]"
        FloatingChatMessageType.RedPacket -> "[红包]"
        FloatingChatMessageType.Transfer -> "[转账]"
        FloatingChatMessageType.Text -> "消息"
        else -> type.label
    }
}

private fun scrmFloatingMessageDetail(
    type: FloatingChatMessageType,
    content: String,
    voiceText: JsonElement?
): String? {
    val extracted = scrmFloatingJsonText(content)?.takeIf { it.isNotBlank() }
    val voice = voiceText?.scrmTextValue()?.takeIf { it.isNotBlank() }
    return extracted ?: when (type) {
        FloatingChatMessageType.Voice -> voice ?: "语音消息"
        FloatingChatMessageType.StickerGif -> "GIF 贴纸"
        FloatingChatMessageType.VoiceCall -> "通话记录"
        else -> null
    }
}

private val ScrmMediaUrlKeys = setOf("url", "mediaurl", "downloadurl", "fileurl", "imageurl", "videourl", "voiceurl", "path")

private fun scrmFormatFileSize(size: Long): String {
    return when {
        size >= 1024L * 1024L -> "%.1f MB".format(size.toDouble() / (1024L * 1024L))
        size >= 1024L -> "%.1f KB".format(size.toDouble() / 1024L)
        else -> "$size B"
    }
}

private fun scrmFloatingJsonText(content: String): String? {
    val start = content.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return null
    val element = runCatching { ScrmMessageJson.parseToJsonElement(content.substring(start)) }.getOrNull() ?: return null
    return element.scrmTextValue()
}

private fun JsonElement.scrmTextValue(): String? {
    if (this is JsonObject) {
        listOf("content", "text", "title", "description", "des", "displayName").forEach { key ->
            val value = this[key]?.jsonPrimitive?.contentOrNull?.trim()
            if (!value.isNullOrBlank()) return value
        }
        listOf("data", "payload", "body", "result").forEach { key ->
            this[key]?.scrmTextValue()?.takeIf { !it.isNullOrBlank() }?.let { return it }
        }
    }
    return null
}

private fun scrmFloatingMessageUrl(content: String): String? {
    val start = content.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return null
    val element = runCatching { ScrmMessageJson.parseToJsonElement(content.substring(start)) }.getOrNull() as? JsonObject
        ?: return null
    return listOf("url", "mediaUrl", "downloadUrl", "fileUrl", "imageUrl", "videoUrl", "voiceUrl", "Url")
        .firstNotNullOfOrNull { key -> element[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
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
        avatarUrl = normalizedRemoteImageUri(contact.displayAvatarUrl),
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
        avatarUrl = normalizedRemoteImageUri(chatRoom.displayAvatarUrl),
        groupMemberAvatarUrls = members
            .asSequence()
            .mapNotNull { member -> normalizedRemoteImageUri(member.displayAvatarUrl) }
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
        avatarUrl = normalizedRemoteImageUri(member.displayAvatarUrl),
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
            avatarUrl = normalizedRemoteImageUri(account.displayAvatarUrl) ?: derivedAvatarByWxid[weChatId]
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
            val avatar = normalizedRemoteImageUri(contact.displayAvatarUrl) ?: return@forEach
            avatars.putIfAbsent(wxid, avatar)
        }
        account.chatRoomMembers.values.flatten().forEach { member ->
            val wxid = member.memberWxid?.takeIf { it.isNotBlank() } ?: return@forEach
            val avatar = normalizedRemoteImageUri(member.displayAvatarUrl) ?: return@forEach
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

/**
 * SCRM 返回的联系人顺序就是悬浮聊天的展示顺序，按顺序轮换可避免相邻初始头像撞色。
 * 真实头像加载成功后会覆盖此填充色，因此这里只影响无头像或加载失败的兜底显示。
 */
private fun scrmApplyAvatarPalette(
    contacts: List<FloatingChatContact>,
    startPosition: Int = 0
): List<FloatingChatContact> {
    // 颜色必须绑定联系人稳定 ID；列表刷新、排序或分页不能改变同一联系人的颜色。
    return contacts.map { contact ->
        val color = scrmStableColor(contact.id)
        val members = contact.groupMemberContacts.map { member ->
            member.copy(avatarColor = scrmStableColor(member.id))
        }
        contact.copy(avatarColor = color, groupMemberContacts = members)
    }
}

private fun scrmAvatarVisibleEntryCount(contacts: List<FloatingChatContact>): Int {
    return contacts.size
}

internal fun scrmAvatarColorForPosition(position: Int): Long {
    return ScrmAvatarPalette[Math.floorMod(position, ScrmAvatarPalette.size)]
}

private fun scrmStableColor(key: String): Long {
    val position = (key.hashCode().toLong() and 0x7fffffff).toInt()
    return scrmAvatarColorForPosition(position)
}

private fun scrmEncodeIdPart(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name())
}

private fun scrmDecodeIdPart(value: String): String? {
    return runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrNull()
}
