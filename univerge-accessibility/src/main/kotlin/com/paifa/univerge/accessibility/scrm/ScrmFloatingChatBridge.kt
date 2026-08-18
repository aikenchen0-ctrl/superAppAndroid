package com.paifa.univerge.accessibility.scrm

import com.paifa.univerge.accessibility.floatingchat.media.normalizedRemoteImageUri
import com.paifa.univerge.core.model.FloatingChatArticleItem
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.core.model.FloatingChatConnectionTarget
import com.paifa.univerge.core.model.FloatingChatFileFormat
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.core.model.FloatingChatMessagePresentation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
private const val ScrmFinderVideoMessageType = 754974769
private val ScrmFinderLiveMessageTypes = setOf(973078577, 975175729)

private data class ScrmFloatingArticlePayload(
    val senderNickname: String?,
    val items: List<FloatingChatArticleItem>
)

private data class ScrmFloatingFilePayload(
    val title: String,
    val description: String,
    val extension: String?,
    val resourceUrl: String?
)

private data class ScrmFloatingEnterpriseInvitePayload(
    val title: String,
    val description: String,
    val resourceUrl: String?,
    val source: String?
)

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
    val articlePayload = scrmFloatingArticlePayload(remote.content)
    val articleItems = articlePayload?.items.orEmpty()
    val enterpriseInvitePayload = scrmFloatingEnterpriseInvitePayload(remote.content)
        ?: remote.extensions
            .asSequence()
            .filter { extension ->
                extension.key.equals("resBody", ignoreCase = true) ||
                    extension.key.equals("messageContent", ignoreCase = true) ||
                    extension.key.equals("body", ignoreCase = true)
            }
            .mapNotNull { extension -> scrmFloatingEnterpriseInvitePayload(extension.value) }
            .firstOrNull()
    val filePayload = scrmFloatingFilePayload(remote.content)
        ?: remote.extensions
            .asSequence()
            .filter { extension ->
                extension.key.equals("resBody", ignoreCase = true) ||
                    extension.key.equals("messageContent", ignoreCase = true) ||
                    extension.key.equals("body", ignoreCase = true)
            }
            .mapNotNull { extension -> scrmFloatingFilePayload(extension.value) }
            .firstOrNull()
    val type = scrmFloatingMessageType(
        messageType = remote.messageType,
        content = remote.content,
        hasOfficialArticleItems = articleItems.isNotEmpty(),
        hasEnterpriseInvitePayload = enterpriseInvitePayload != null,
        // The backend may normalize the outer type (for example 6 or 74),
        // while retaining the canonical file fields in resBody.
        hasFilePayload = filePayload != null
    )
    val text = when {
        type == FloatingChatMessageType.Article -> scrmFloatingArticleTitle(articleItems)
        type == FloatingChatMessageType.EnterpriseInvite && enterpriseInvitePayload != null -> enterpriseInvitePayload.title
        type == FloatingChatMessageType.FilePreview && filePayload != null -> filePayload.title
        else -> scrmFloatingMessageText(type, remote.content)
    }
    val system = remote.messageType == 10000 || remote.messageType == 10002
    val mediaUrl = remote.media.firstOrNull()?.resolvedUrl
        ?: remote.extensions.firstNotNullOfOrNull { extension ->
            extension.value.takeIf { extension.key.lowercase() in ScrmMediaUrlKeys && it.isNotBlank() }
        }
    val thumbnailUrl = mediaUrl
        ?: articleItems.firstOrNull()?.let { it.bannerImageUrl ?: it.imageUrl }
        ?: scrmFloatingMessageThumbnailUrl(remote.content)
    // WeChat messageType=14/47 stores its only downloadable media address in resBody.Thumb.
    // Preserve it as the resource URL as well, so the same image is used by the bubble and preview flows.
    val resourceUrl = mediaUrl
        ?: articleItems.firstOrNull()?.detailUrl
        ?: enterpriseInvitePayload?.resourceUrl
        ?: filePayload?.resourceUrl
        ?: scrmFloatingMessageUrl(remote.content)
        ?: thumbnailUrl.takeIf { type == FloatingChatMessageType.StickerGif }
    val detail = when {
        type == FloatingChatMessageType.StickerGif -> remote.content.trim().takeIf { it.isNotBlank() }
        else -> enterpriseInvitePayload?.description?.takeIf { it.isNotBlank() }
            ?: scrmFloatingMessageDetail(type, remote.content, remote.voiceText)
    }
    scrmFloatingStickerDebugLog(
        remote = remote,
        resolvedType = type,
        text = text,
        detail = detail,
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        resourceUrl = resourceUrl
    )
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
        detail = detail,
        appName = enterpriseInvitePayload?.source ?: articlePayload?.senderNickname,
        articleItems = articleItems,
        resourceUrl = resourceUrl,
        thumbnailUrl = thumbnailUrl,
        fileName = filePayload?.title
            ?: remote.media.firstOrNull()?.fileExtension?.takeIf { it.isNotBlank() },
        fileFormat = filePayload?.extension?.let(::scrmFloatingFileFormat),
        fileSizeLabel = filePayload?.description?.takeIf { it.isNotBlank() }
            ?: remote.media.firstOrNull()?.fileSize?.takeIf { it > 0L }?.let(::scrmFormatFileSize),
        remoteMessageId = remote.messageId.takeIf { it > 0L },
        remoteMessageServerId = remote.messageServerId?.toString(),
        finderUserName = scrmFinderUserName(remote.content, remote.extensions),
        clientRequestId = remote.clientMessageId
    )
}

/** Maps WeChat messageType values used by iOS to the Android read-only renderer. */
private fun scrmFloatingMessageType(
    messageType: Int,
    content: String,
    hasOfficialArticleItems: Boolean = false,
    hasEnterpriseInvitePayload: Boolean = false,
    hasFilePayload: Boolean = false
): FloatingChatMessageType {
    // Some history/change payloads omit the outer WeChat type or flatten it to text,
    // but retain the original sticker body. Md5 plus Thumb is the stable image-sticker
    // signature and must not be presented as JSON text.
    if (messageType in ScrmStickerFallbackMessageTypes && scrmFloatingStickerPayload(content)) {
        return FloatingChatMessageType.StickerGif
    }
    if (hasOfficialArticleItems) return FloatingChatMessageType.Article
    if (hasEnterpriseInvitePayload) return FloatingChatMessageType.EnterpriseInvite
    if (hasFilePayload) return FloatingChatMessageType.FilePreview
    val lower = content.lowercase()
    return when (messageType) {
        // Full WeChat outer types are required. Do not infer Finder messages from appmsg internals.
        ScrmFinderVideoMessageType -> FloatingChatMessageType.ChannelsVideo
        in ScrmFinderLiveMessageTypes -> FloatingChatMessageType.ChannelsLive
        1 -> FloatingChatMessageType.Text
        3 -> FloatingChatMessageType.ImageThumbnail
        34 -> FloatingChatMessageType.Voice
        37, 42 -> FloatingChatMessageType.ContactLink
        43, 62 -> FloatingChatMessageType.VideoPreview
        // SCRM sends saved/favorite emoji through the WeChat Emoji=14 message type.
        // Its resBody carries Md5/Thumb, so route it through the image sticker renderer.
        14, 47 -> FloatingChatMessageType.StickerGif
        48 -> FloatingChatMessageType.Location
        49 -> when {
            "<type>33</type>" in lower || "<type>36</type>" in lower -> FloatingChatMessageType.MiniProgramLink
            "<type>6</type>" in lower -> FloatingChatMessageType.FilePreview
            "<type>19</type>" in lower -> FloatingChatMessageType.ChatHistory
            "<type>2000</type>" in lower -> FloatingChatMessageType.Transfer
            "<type>2001</type>" in lower || "<type>2002</type>" in lower -> FloatingChatMessageType.RedPacket
            "<type>57</type>" in lower -> FloatingChatMessageType.Quote
            "<type>5</type>" in lower || "h5" in lower -> FloatingChatMessageType.WebLink
            else -> FloatingChatMessageType.WebLink
        }
        50 -> FloatingChatMessageType.VoiceCall
        // System/group-management notifications keep Text as their model type,
        // while presentation is switched to System above so they render without a bubble.
        else -> FloatingChatMessageType.Text
    }
}

/** Parses the JSON file body returned by SCRM for WeChat file messages. */
private fun scrmFloatingFilePayload(content: String): ScrmFloatingFilePayload? {
    val root = scrmFloatingJsonElement(content) as? JsonObject ?: return null
    val title = root.scrmFilePrimitiveContent("Title")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val extension = root.scrmFilePrimitiveContent("FileExt")
        ?.trim()
        ?.trimStart('.')
        ?.takeIf(String::isNotBlank)
    val description = root.scrmFilePrimitiveContent("Des")?.trim().orEmpty()
    val typeString = root.scrmFilePrimitiveContent("TypeStr").orEmpty()
    val hasFileMarker = extension != null ||
        root.scrmFilePrimitiveContent("CdnFileType") != null ||
        root.scrmFilePrimitiveContent("Type") == "74" ||
        typeString.contains("文件")
    if (!hasFileMarker) return null
    val resourceUrl = listOf("Url", "url", "Thumb", "thumb")
        .firstNotNullOfOrNull { key ->
            root.scrmFilePrimitiveContent(key)?.trim()?.takeIf(String::isNotBlank)
        }
    return ScrmFloatingFilePayload(title, description, extension, resourceUrl)
}

/** Parses the WeCom enterprise invitation body carried by an app-message response. */
private fun scrmFloatingEnterpriseInvitePayload(content: String): ScrmFloatingEnterpriseInvitePayload? {
    val root = scrmFloatingJsonElement(content) as? JsonObject ?: return null
    val title = root.scrmFilePrimitiveContent("Title")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val source = root.scrmFilePrimitiveContent("Source")?.trim()
    val type = root.scrmFilePrimitiveContent("Type")?.trim()
    val resourceUrl = root.scrmFilePrimitiveContent("Url")
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val isEnterpriseInvite = source.equals("企业微信", ignoreCase = true) &&
        (type == "5" || resourceUrl?.contains("work.weixin.qq.com", ignoreCase = true) == true)
    if (!isEnterpriseInvite) return null
    return ScrmFloatingEnterpriseInvitePayload(
        title = title,
        description = root.scrmFilePrimitiveContent("Des")?.trim().orEmpty(),
        resourceUrl = resourceUrl,
        source = source
    )
}

private fun JsonObject.scrmFilePrimitiveContent(key: String): String? {
    return entries.firstOrNull { (name, value) ->
        name.equals(key, ignoreCase = true) && value is JsonPrimitive
    }?.value?.jsonPrimitive?.contentOrNull
}

private fun scrmFloatingFileFormat(extension: String): FloatingChatFileFormat? {
    return when (extension.lowercase()) {
        "txt" -> FloatingChatFileFormat.Txt
        "md", "markdown" -> FloatingChatFileFormat.Markdown
        "doc", "docx" -> FloatingChatFileFormat.Word
        "pdf" -> FloatingChatFileFormat.Pdf
        "zip" -> FloatingChatFileFormat.Zip
        else -> null
    }
}

/** Parses the structured official-account article list returned in a WeChat app message body. */
private fun scrmFloatingArticlePayload(content: String): ScrmFloatingArticlePayload? {
    if (!content.contains("\"appMessageItems\"")) return null
    val root = scrmFloatingJsonElement(content) as? JsonObject ?: return null
    val elements = root["appMessageItems"] as? JsonArray ?: return null
    val items = elements.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val title = item.scrmPrimitiveContent("title")?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        FloatingChatArticleItem(
            title = title,
            description = item.scrmPrimitiveContent("description").orEmpty(),
            detailUrl = item.scrmPrimitiveContent("detailUrl")?.takeIf(String::isNotBlank),
            bannerImageUrl = item.scrmPrimitiveContent("bannerImageUrl")?.takeIf(String::isNotBlank),
            imageUrl = item.scrmPrimitiveContent("imageUrl")?.takeIf(String::isNotBlank),
            itemType = item.scrmPrimitiveContent("itemType")?.toIntOrNull(),
            timestampSeconds = item.scrmPrimitiveContent("timestamp")?.toLongOrNull()
        )
    }
    if (items.isEmpty()) return null
    return ScrmFloatingArticlePayload(
        senderNickname = root.scrmPrimitiveContent("senderNickname")
            ?.trim()
            ?.takeIf(String::isNotBlank),
        items = items
    )
}

private fun scrmFloatingArticleTitle(items: List<FloatingChatArticleItem>): String {
    val firstTitle = items.firstOrNull()?.title.orEmpty()
    return if (items.size > 1) "$firstTitle 等 ${items.size} 篇" else firstTitle
}

private fun JsonObject.scrmPrimitiveContent(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
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
    if (type == FloatingChatMessageType.Voice) {
        return voice ?: extracted ?: "语音消息"
    }
    return extracted ?: when (type) {
        FloatingChatMessageType.StickerGif -> "GIF 贴纸"
        FloatingChatMessageType.VoiceCall -> "通话记录"
        else -> null
    }
}

private val ScrmMediaUrlKeys = setOf("url", "mediaurl", "downloadurl", "fileurl", "imageurl", "videourl", "voiceurl", "path")
private val ScrmStickerThumbnailKeys = setOf("thumb", "thumbnail", "thumbnailurl", "thumburl")
private val ScrmStickerMd5Keys = setOf("md5", "emoticonmd5")
private val ScrmStickerFallbackMessageTypes = setOf(0, 1)
private const val ScrmStickerDebugHost = "vweixinf.tc.qq.com"
private const val ScrmStickerDebugTag = "[ScrmStickerDebug]"

/** 仅诊断腾讯图片表情包：输出原始 body、路由类型和最终媒体字段，方便定位渲染链路。 */
private fun scrmFloatingStickerDebugLog(
    remote: ScrmChatMessage,
    resolvedType: FloatingChatMessageType,
    text: String,
    detail: String?,
    mediaUrl: String?,
    thumbnailUrl: String?,
    resourceUrl: String?
) {
    val rawContent = remote.content
    if (!rawContent.contains(ScrmStickerDebugHost, ignoreCase = true)) return
    println(
        buildString {
            appendLine("$ScrmStickerDebugTag matchedHost=$ScrmStickerDebugHost")
            appendLine(
                "messageId=${remote.messageId} messageServerId=${remote.messageServerId} " +
                    "conversationId=${remote.conversationId} chatType=${remote.chatType} " +
                    "messageType=${remote.messageType} direction=${remote.direction}"
            )
            appendLine("senderWxid=${remote.senderWxid} receiverWxid=${remote.receiverWxid}")
            appendLine("rawContentLength=${rawContent.length}")
            appendLine("rawContent=$rawContent")
            appendLine("media=${remote.media}")
            appendLine("extensions=${remote.extensions}")
            appendLine("stickerPayload=${scrmFloatingStickerPayload(rawContent)}")
            appendLine("resolvedType=${resolvedType.name}")
            appendLine("text=$text")
            appendLine("detail=${detail.orEmpty()}")
            appendLine("mediaUrl=${mediaUrl.orEmpty()}")
            appendLine("thumbnailUrl=${thumbnailUrl.orEmpty()}")
            appendLine("resourceUrl=${resourceUrl.orEmpty()}")
        }
    )
}

private fun scrmFormatFileSize(size: Long): String {
    return when {
        size >= 1024L * 1024L -> "%.1f MB".format(size.toDouble() / (1024L * 1024L))
        size >= 1024L -> "%.1f KB".format(size.toDouble() / 1024L)
        else -> "$size B"
    }
}

private fun scrmFloatingJsonText(content: String): String? {
    return scrmFloatingJsonElement(content)?.scrmTextValue()
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

private fun scrmFinderUserName(content: String, extensions: List<ScrmChatExtension>): String? {
    extensions.firstNotNullOfOrNull { extension ->
        extension.value.trim().takeIf {
            extension.key.equals("sphUserName", ignoreCase = true) && it.isNotEmpty()
        }
    }?.let { return it }

    val start = content.indexOfFirst { it == '{' || it == '[' }
    val jsonUserName = if (start >= 0) {
        runCatching { ScrmMessageJson.parseToJsonElement(content.substring(start)) }.getOrNull()
            ?.scrmFinderUserNameValue()
    } else {
        null
    }
    return jsonUserName
}

private fun JsonElement.scrmFinderUserNameValue(): String? {
    if (this !is JsonObject) return null
    entries.firstNotNullOfOrNull { (key, value) ->
        if (key.equals("sphUserName", ignoreCase = true)) {
            value.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        } else {
            null
        }
    }?.let { return it }
    return values.firstNotNullOfOrNull(JsonElement::scrmFinderUserNameValue)
}

private fun scrmFloatingMessageUrl(content: String): String? {
    val element = scrmFloatingJsonElement(content) as? JsonObject
        ?: return null
    return listOf("url", "mediaUrl", "downloadUrl", "fileUrl", "imageUrl", "videoUrl", "voiceUrl", "Url")
        .firstNotNullOfOrNull { key -> element[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
}

/** Extracts the WeChat sticker preview URL without treating the JSON as visible message text. */
private fun scrmFloatingMessageThumbnailUrl(content: String): String? {
    return scrmFloatingJsonElement(content)?.let { element ->
        scrmFloatingJsonUrlValue(element, ScrmStickerThumbnailKeys)
    }
}

private fun scrmFloatingStickerPayload(content: String): Boolean {
    val element = scrmFloatingJsonElement(content) ?: return false
    return scrmFloatingJsonUrlValue(element, ScrmStickerThumbnailKeys) != null &&
        scrmFloatingJsonStringValue(element, ScrmStickerMd5Keys) != null
}

/**
 * Extracts the first complete JSON object/array from a response body.
 * Some copied WeChat bodies append the Thumb URL after the JSON, so parsing the
 * entire suffix would reject an otherwise valid sticker payload.
 */
private fun scrmFloatingJsonElement(content: String): JsonElement? {
    val start = content.indexOfFirst { it == '{' || it == '[' }
    if (start < 0) return null

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until content.length) {
        when (val character = content[index]) {
            '"' -> {
                if (!escaped) inString = !inString
                escaped = false
            }
            '\\' -> {
                if (inString) escaped = !escaped else escaped = false
            }
            else -> {
                if (inString) {
                    escaped = false
                    continue
                }
                when (character) {
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) {
                            val json = content.substring(start, index + 1)
                            return runCatching { ScrmMessageJson.parseToJsonElement(json) }.getOrNull()
                        }
                    }
                }
            }
        }
    }
    return null
}

private fun scrmFloatingJsonStringValue(element: JsonElement, keys: Set<String>): String? {
    if (element is JsonObject) {
        element.entries.firstNotNullOfOrNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull
                ?.takeIf { key.lowercase() in keys && it.isNotBlank() }
        }?.let { return it }
        element.values.firstNotNullOfOrNull { child ->
            scrmFloatingJsonStringValue(child, keys)
        }?.let { return it }
    }
    if (element is JsonArray) {
        element.firstNotNullOfOrNull { child ->
            scrmFloatingJsonStringValue(child, keys)
        }?.let { return it }
    }
    return null
}

private fun scrmFloatingJsonUrlValue(element: JsonElement, keys: Set<String>): String? {
    if (element is JsonObject) {
        element.entries.firstNotNullOfOrNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull
                ?.takeIf { key.lowercase() in keys && it.isNotBlank() }
        }?.let { return it }
        element.values.firstNotNullOfOrNull { child ->
            scrmFloatingJsonUrlValue(child, keys)
        }?.let { return it }
    }
    if (element is JsonArray) {
        element.firstNotNullOfOrNull { child ->
            scrmFloatingJsonUrlValue(child, keys)
        }?.let { return it }
    }
    return null
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
        online = contact.isBlocked == 0,
        isFriend = contact.isFriend == 1
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
