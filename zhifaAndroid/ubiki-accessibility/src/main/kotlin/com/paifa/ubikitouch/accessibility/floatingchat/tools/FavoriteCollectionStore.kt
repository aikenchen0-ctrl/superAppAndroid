package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.content.Context
import android.content.SharedPreferences
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Properties

internal data class FavoriteCollectionItem(
    val messageId: String,
    val type: FloatingChatMessageType,
    val title: String,
    val description: String,
    val source: String
)

internal const val FavoriteCollectionMaxCount = 80

internal fun favoriteCollectionItems(
    messages: List<FloatingChatMessage>,
    favoriteMessageIds: Map<String, Boolean>,
    favoriteMediaIds: Map<String, Boolean>,
    storedItems: List<FavoriteCollectionItem>
): List<FavoriteCollectionItem> {
    val refreshedStoredItems = refreshFavoriteCollectionSourcesFromMessages(
        items = storedItems,
        messages = messages
    )
    val explicitFavorites = messages.filter { message ->
        favoriteMessageIds[message.id] == true || favoriteMediaIds[message.id] == true
    }.map { message -> message.toFavoriteCollectionItem() }
    val seededFavorites = if (refreshedStoredItems.isEmpty() && explicitFavorites.isEmpty()) {
        messages.filter { message ->
            message.resourceUrl != null ||
                message.type == FloatingChatMessageType.ImageThumbnail ||
                message.type == FloatingChatMessageType.VideoPreview ||
                message.type == FloatingChatMessageType.FilePreview ||
                message.type == FloatingChatMessageType.ContactLink ||
                message.type == FloatingChatMessageType.MiniProgramLink
        }.take(6).map { message -> message.toFavoriteCollectionItem() }
    } else {
        emptyList()
    }
    return mergeFavoriteCollectionItems(refreshedStoredItems + explicitFavorites + seededFavorites)
}

internal fun refreshFavoriteCollectionSourcesFromMessages(
    items: List<FavoriteCollectionItem>,
    messages: List<FloatingChatMessage>
): List<FavoriteCollectionItem> {
    if (items.isEmpty() || messages.isEmpty()) return items
    val messagesById = messages.associateBy { message -> message.id }
    return items.map { item ->
        val currentMessage = messagesById[item.messageId] ?: return@map item
        item.copy(source = currentMessage.toFavoriteCollectionItem().source)
    }
}

internal fun updateFavoriteCollectionItems(
    context: Context,
    items: MutableList<FavoriteCollectionItem>,
    message: FloatingChatMessage,
    favorite: Boolean
) {
    val nextItems = if (favorite) {
        mergeFavoriteCollectionItems(listOf(message.toFavoriteCollectionItem()) + items)
            .take(FavoriteCollectionMaxCount)
    } else {
        items.filterNot { item -> item.messageId == message.id }
    }
    items.clear()
    items.addAll(nextItems)
    saveFavoriteCollectionItems(context, nextItems)
}

internal fun mergeFavoriteCollectionItems(
    items: List<FavoriteCollectionItem>
): List<FavoriteCollectionItem> {
    val seen = linkedSetOf<String>()
    return items.filter { item -> seen.add(item.messageId) }
}

internal fun selectedFavoriteCollectionItems(
    items: List<FavoriteCollectionItem>,
    selectedItemIds: Map<String, Boolean>
): List<FavoriteCollectionItem> {
    return items.filter { item -> selectedItemIds[item.messageId] == true }
}

internal fun favoriteCollectionItemsAfterRemove(
    items: List<FavoriteCollectionItem>,
    removedItem: FavoriteCollectionItem
): List<FavoriteCollectionItem> {
    return mergeFavoriteCollectionItems(
        items.filterNot { item -> item.messageId == removedItem.messageId }
    ).take(FavoriteCollectionMaxCount)
}

internal fun loadFavoriteCollectionItems(context: Context): List<FavoriteCollectionItem> {
    val stored = favoriteCollectionPrefs(context).getString(KEY_FAVORITE_COLLECTION_ITEMS, null)
        ?: return emptyList()
    return parseFavoriteCollectionItems(stored)
}

internal fun saveFavoriteCollectionItems(context: Context, items: List<FavoriteCollectionItem>) {
    favoriteCollectionPrefs(context)
        .edit()
        .putString(KEY_FAVORITE_COLLECTION_ITEMS, serializeFavoriteCollectionItems(items))
        .apply()
}

internal fun favoriteCollectionSerializationRoundTrips(): Boolean {
    val item = FavoriteCollectionItem(
        messageId = "message-1",
        type = FloatingChatMessageType.ImageThumbnail,
        title = "图片收藏",
        description = "content://media/external/images/media/42",
        source = "陈晨 · 10:20"
    )
    val decoded = parseFavoriteCollectionItems(serializeFavoriteCollectionItems(listOf(item))).singleOrNull()
    return decoded?.messageId == item.messageId &&
        decoded.type == item.type &&
        decoded.title == item.title &&
        decoded.description == item.description &&
        decoded.source == item.source
}

internal fun favoriteCollectionClickOpensContentPreview(): Boolean = true

internal fun favoriteCollectionClickSendsToChat(): Boolean = false

internal fun favoriteCollectionPreviewTimestampLabel(): String = "收藏"

internal fun favoriteCollectionItemUsesMediaPreview(item: FavoriteCollectionItem): Boolean {
    return item.type == FloatingChatMessageType.ImageThumbnail ||
        item.type == FloatingChatMessageType.VideoPreview
}

internal fun favoriteCollectionPreviewMessage(item: FavoriteCollectionItem): FloatingChatMessage {
    val title = normalizeFavoriteCollectionText(item.title)
    val description = normalizeFavoriteCollectionText(item.description)
    val source = normalizeFavoriteCollectionText(item.source)
    val url = description.takeIf { value -> value.isNotBlank() }
    return FloatingChatMessage(
        id = "favorite-preview-${item.messageId}",
        type = item.type,
        text = title,
        fromMe = false,
        senderName = source,
        time = favoriteCollectionPreviewTimestampLabel(),
        presentation = if (favoriteCollectionItemUsesMediaPreview(item)) {
            FloatingChatMessagePresentation.MediaStandalone
        } else {
            FloatingChatMessagePresentation.SpecialCard
        },
        connectionTarget = FloatingChatConnectionTarget.None,
        detail = description,
        quoteAuthor = source.takeIf { value -> value.isNotBlank() },
        quoteText = description.takeIf { value -> value.isNotBlank() },
        cardName = title.takeIf { value -> item.type == FloatingChatMessageType.ContactLink && value.isNotBlank() },
        cardSubtitle = description.takeIf { value -> item.type == FloatingChatMessageType.ContactLink && value.isNotBlank() },
        appName = title.takeIf { value -> item.type == FloatingChatMessageType.MiniProgramLink && value.isNotBlank() },
        locationTitle = title.takeIf { value ->
            (item.type == FloatingChatMessageType.Location || item.type == FloatingChatMessageType.InlineLocation) &&
                value.isNotBlank()
        },
        locationAddress = description.takeIf { value ->
            (item.type == FloatingChatMessageType.Location || item.type == FloatingChatMessageType.InlineLocation) &&
                value.isNotBlank()
        },
        resourceUrl = url,
        fileName = title.takeIf { value -> item.type == FloatingChatMessageType.FilePreview && value.isNotBlank() },
        filePreviewLines = if (item.type == FloatingChatMessageType.FilePreview && description.isNotBlank()) {
            description.lines().take(4)
        } else {
            emptyList()
        },
        thumbnailUrl = url.takeIf { favoriteCollectionItemUsesMediaPreview(item) }
    )
}

internal fun favoriteToolOpensInAppCollectionPage(): Boolean = true

internal fun favoritePageShowsSavedMessagesLinksImagesVideos(): Boolean = true

internal fun favoriteCollectionPersistsSavedItems(): Boolean = true

internal fun favoriteCollectionRestoresSavedItemsBeforeCurrentSession(): Boolean = true

internal fun favoriteToolDirectlySendsSimulatedMessage(): Boolean = false

private fun FloatingChatMessage.toFavoriteCollectionItem(): FavoriteCollectionItem {
    val title = when (type) {
        FloatingChatMessageType.ImageThumbnail -> "图片收藏"
        FloatingChatMessageType.VideoPreview -> "视频收藏"
        FloatingChatMessageType.FilePreview -> fileName ?: text
        FloatingChatMessageType.ContactLink -> cardName ?: text
        FloatingChatMessageType.MiniProgramLink -> appName ?: text
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> locationTitle ?: text
        else -> text.ifBlank { type.label }
    }
    return FavoriteCollectionItem(
        messageId = id,
        type = type,
        title = title,
        description = favoriteDescription(),
        source = "${senderName} · ${time}"
    )
}

private fun FloatingChatMessage.favoriteDescription(): String {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> resourceUrl ?: thumbnailUrl ?: "本地图片"
        FloatingChatMessageType.VideoPreview -> resourceUrl ?: thumbnailUrl ?: "本地视频"
        FloatingChatMessageType.FilePreview -> filePreviewLines.firstOrNull() ?: resourceUrl ?: text
        FloatingChatMessageType.ContactLink -> cardSubtitle ?: resourceUrl ?: text
        FloatingChatMessageType.MiniProgramLink -> detail ?: resourceUrl ?: text
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> locationAddress ?: resourceUrl ?: text
        else -> quoteText ?: detail ?: resourceUrl ?: text
    }
}

private fun favoriteCollectionPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(FAVORITE_COLLECTION_PREFS, Context.MODE_PRIVATE)
}

private fun serializeFavoriteCollectionItems(items: List<FavoriteCollectionItem>): String {
    val properties = Properties()
    val cappedItems = items.take(FavoriteCollectionMaxCount)
    properties.setProperty("count", cappedItems.size.toString())
    cappedItems.forEachIndexed { index, item ->
        val prefix = "item.$index."
        properties.setProperty(prefix + "messageId", item.messageId)
        properties.setProperty(prefix + "type", item.type.name)
        properties.setProperty(prefix + "title", item.title)
        properties.setProperty(prefix + "description", item.description)
        properties.setProperty(prefix + "source", item.source)
    }
    val writer = StringWriter()
    properties.store(writer, null)
    return writer.toString()
}

private fun parseFavoriteCollectionItems(stored: String): List<FavoriteCollectionItem> {
    return runCatching {
        val properties = Properties()
        properties.load(StringReader(stored))
        val count = properties.getProperty("count")?.toIntOrNull()?.coerceIn(0, FavoriteCollectionMaxCount) ?: 0
        buildList {
            for (index in 0 until count) {
                val prefix = "item.$index."
                val type = properties.getProperty(prefix + "type")
                    .takeIf { value -> value.isNotBlank() }
                    ?.let { value -> runCatching { FloatingChatMessageType.valueOf(value) }.getOrNull() }
                    ?: FloatingChatMessageType.Text
                val messageId = properties.getProperty(prefix + "messageId")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: continue
                val title = properties.getProperty(prefix + "title")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: type.label
                add(
                    FavoriteCollectionItem(
                        messageId = messageId,
                        type = type,
                        title = normalizeFavoriteCollectionText(title),
                        description = normalizeFavoriteCollectionText(properties.getProperty(prefix + "description").orEmpty()),
                        source = properties.getProperty(prefix + "source")
                            ?.takeIf { value -> value.isNotBlank() }
                            ?.let { value -> normalizeFavoriteCollectionText(value) }
                            ?: "收藏"
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun normalizeFavoriteCollectionText(value: String): String {
    if (!looksLikeLegacyFavoriteMojibake(value)) return value
    val repaired = decodeLegacyFavoriteMojibake(value) ?: return value
    return if (repaired.isNotBlank() && repaired != value) repaired else value
}

private fun looksLikeLegacyFavoriteMojibake(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.any { char -> char == '\uFFFD' || char == '\u20AC' || char.code in 0xE000..0xF8FF }) {
        return true
    }
    val markerCount = value.count { char -> FavoriteLegacyMojibakeMarkers.indexOf(char) >= 0 }
    return markerCount >= 2
}

private fun decodeLegacyFavoriteMojibake(value: String): String? {
    val bytes = runCatching { value.toByteArray(FavoriteLegacyMojibakeCharset) }.getOrNull() ?: return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
}

private const val FAVORITE_COLLECTION_PREFS = "floating_chat_favorite_collection"
private const val KEY_FAVORITE_COLLECTION_ITEMS = "favorite_collection_items"
private val FavoriteLegacyMojibakeCharset: Charset = Charset.forName("GB18030")
private const val FavoriteLegacyMojibakeMarkers =
    "\u93c0\u60f0\u68cc\u93c2\u56e8\u6e70\u5a11\u581f\u4f05\u9365\u5267\u5896\u9471\u5a42\u3049" +
        "\u9365\u5267\u5896\u6924\u572d\u6d30\u6769\u6d98\u5bb3\u6d63\u66e1\u5acd\u8def" +
        "\u675e\u5f42\u934f\u62bd\u68f4\u9352\u72bb\u6ace\u5bb8\u67e5\u832c\u7ca1\u7039\u5c7e\u579a" +
        "\u9428\u52eb\u56e8\u6fa7\u55d8\u7b1f\u6d7c\u6c33\u7efe\u7455\u4f8a\u7d30"
