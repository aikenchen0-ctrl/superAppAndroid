package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.moments.scrmMomentAvatarColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.*

private const val ScrmMomentPostIdPrefix = "scrm-moment:"

internal fun scrmMomentPostsFromTaskData(data: JsonElement?): List<AppMomentPost> {
    return collectScrmMomentObjects(data)
        .mapNotNull(::scrmMomentPostFromObject)
        .distinctBy { post -> post.id }
}

private fun collectScrmMomentObjects(element: JsonElement?): List<JsonObject> {
    return when (element) {
        is JsonArray -> element.flatMap(::collectScrmMomentObjects)
        is JsonObject -> {
            if (element.isScrmMomentObject()) {
                listOf(element)
            } else {
                val preferred = listOf(
                    "moments",
                    "items",
                    "records",
                    "list",
                    "data",
                    "circleList",
                    "momentList",
                    "feeds"
                )
                val preferredObjects = preferred
                    .mapNotNull { key -> element[key] }
                    .flatMap(::collectScrmMomentObjects)
                if (preferredObjects.isNotEmpty()) {
                    preferredObjects
                } else {
                    element.values.flatMap(::collectScrmMomentObjects)
                }
            }
        }

        else -> emptyList()
    }
}

private fun JsonObject.isScrmMomentObject(): Boolean {
    val hasCircleId = longValue("circleId", "circleID", "id", "snsId") != null
    val hasMomentBody = stringValue(
        "content",
        "text",
        "momentContent",
        "contentText",
        "desc",
        "description"
    ) != null ||
        stringListValue("images", "imageUrls", "picUrls", "pictures", "attachments").isNotEmpty() ||
        stringValue("videoUrl", "video", "videoPath") != null ||
        this["comments"] != null ||
        !momentLinkUrl().isNullOrBlank() ||
        !momentLinkTitle().isNullOrBlank()
    return hasCircleId && hasMomentBody
}

private fun scrmMomentPostFromObject(value: JsonObject): AppMomentPost? {
    val circleId = value.longValue("circleId", "circleID", "id", "snsId") ?: return null
    val author = value.stringValue(
        "nickname",
        "nickName",
        "author",
        "authorName",
        "displayName",
        "userName",
        "name",
        "wxName",
        "wxid"
    ) ?: "Unknown"
    val authorWxId = value.stringValue(
        "authorWxid",
        "authorWxId",
        "authorWxID",
        "wxid",
        "userName"
    )
    val content = value.stringValue(
        "content",
        "text",
        "momentContent",
        "contentText",
        "desc",
        "description"
    ).orEmpty()
    val publishTime = value.longValue("publishTime", "createTime", "createdTime", "timestamp")
    val linkUrl = value.momentLinkUrl()
    val linkTitle = value.momentLinkTitle()
    val imageUrl = value.stringListValue(
        "images",
        "imageUrls",
        "picUrls",
        "pictures",
        "attachments",
        "mediaUrls"
    ).firstOrNull()
    val videoUrl = value.stringValue("videoUrl", "video", "videoPath")
        ?: value.stringListValue("videos", "videoUrls").firstOrNull()
    val media = when {
        !videoUrl.isNullOrBlank() -> AppMomentMedia(
            kind = MomentMediaKind.Video,
            uri = videoUrl,
            previewUri = value.stringValue("thumbUrl", "coverUrl", "previewUrl"),
            widthDp = 168,
            heightDp = 220,
            label = "video"
        )

        !imageUrl.isNullOrBlank() -> AppMomentMedia(
            kind = MomentMediaKind.Image,
            uri = imageUrl,
            previewUri = imageUrl,
            widthDp = 168,
            heightDp = 168,
            label = "image"
        )

        !linkUrl.isNullOrBlank() -> AppMomentMedia(
            kind = MomentMediaKind.Link,
            uri = linkUrl,
            widthDp = 260,
            heightDp = 72,
            label = linkTitle ?: "链接"
        )

        else -> null
    }
    return AppMomentPost(
        id = "$ScrmMomentPostIdPrefix$circleId",
        author = author,
        content = content,
        time = value.stringValue("time", "displayTime", "publishTimeText", "createdAt")
            ?: scrmMomentDisplayTime(publishTime),
        avatarText = author.take(2),
        avatarColor = scrmMomentAvatarColor(circleId.toString()),
        media = media,
        linkTitle = linkTitle,
        linkUrl = linkUrl,
        sourceLabel = value.stringValue("linkSourceName", "sourceName", "sourceLabel"),
        likedBy = value.stringListValue("likedBy", "likes", "likeUsers", "praiseUsers", "praiseList")
            .distinct(),
        comments = value.momentComments(),
        authorWxId = authorWxId,
        circleId = circleId,
        publishTime = publishTime,
        createdAt = scrmMomentCreatedAt(publishTime)
    )
}

private fun JsonObject.momentComments(): List<AppMomentComment> {
    val comments = listOf("comments", "commentList", "replyList")
        .firstNotNullOfOrNull { key -> this[key] as? JsonArray }
        ?: return emptyList()
    return comments.mapNotNull { element ->
        when (element) {
            is JsonObject -> {
                val author = element.stringValue(
                    "nickname",
                    "nickName",
                    "author",
                    "authorName",
                    "displayName",
                    "fromNickname",
                    "name",
                    "wxid"
                ) ?: "Comment"
                val content = element.stringValue("content", "text", "comment", "commentText")
                    ?: return@mapNotNull null
                if (isScrmMomentSyntheticStatusComment(content)) return@mapNotNull null
                AppMomentComment(
                    author = author,
                    text = content,
                    id = element.longValue("commentId", "commentID", "id", "snsCommentId"),
                    authorWxId = element.stringValue(
                        "fromWxid",
                        "fromWeChatId",
                        "authorWxid",
                        "authorWxId",
                        "wxid"
                    ),
                    replyTo = element.stringValue("replyTo", "toNickname", "toNickName"),
                    replyCommentId = element.longValue("replyCommentId", "replyId", "toCommentId")
                )
            }

            else -> element.primitiveText()?.let { text ->
                if (isScrmMomentSyntheticStatusComment(text)) return@let null
                AppMomentComment(author = "Comment", text = text)
            }
        }
    }
}

private fun isScrmMomentSyntheticStatusComment(text: String): Boolean {
    return text.trim().lowercase(Locale.ROOT) == "sns_send_ok"
}

private fun JsonObject.momentLinkUrl(): String? {
    return stringValue(
        "linkUrl",
        "linkURL",
        "LinkUrl",
        "LinkURL",
        "link",
        "href",
        "url",
        "Url",
        "sourceUrl",
        "pageUrl",
        "weAppUrl"
    ) ?: momentNestedString(
        "url",
        "href",
        "linkUrl",
        "linkURL",
        "pageUrl",
        "weAppUrl"
    )
}

private fun JsonObject.momentLinkTitle(): String? {
    return stringValue(
        "linkTitle",
        "LinkTitle",
        "title",
        "Title",
        "appName",
        "sourceName",
        "weAppTitle"
    ) ?: momentNestedString(
        "title",
        "linkTitle",
        "appName",
        "sourceName",
        "weAppTitle"
    )
}

private fun JsonObject.momentNestedString(vararg keys: String): String? {
    return listOf("linkInfo", "appMsg", "link").firstNotNullOfOrNull { objectKey ->
        (this[objectKey] as? JsonObject)?.stringValue(*keys)
    }
}

internal fun JsonObject.stringValue(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.primitiveText()
    }
}

internal fun JsonObject.longValue(vararg keys: String): Long? {
    return keys.firstNotNullOfOrNull { key ->
        val element = this[key] ?: return@firstNotNullOfOrNull null
        runCatching { element.jsonPrimitive.longOrNull }.getOrNull()
            ?: element.primitiveText()?.toLongOrNull()
    }
}

internal fun JsonObject.stringListValue(vararg keys: String): List<String> {
    return keys.firstNotNullOfOrNull { key ->
        val values = this[key]?.toUsefulStringList().orEmpty()
        values.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

internal fun JsonElement.toUsefulStringList(): List<String> {
    return when (this) {
        is JsonArray -> mapNotNull { element -> element.usefulText() }
        else -> usefulText()?.let(::listOf).orEmpty()
    }
}

internal fun JsonElement.usefulText(): String? {
    primitiveText()?.let { return it }
    val objectValue = this as? JsonObject ?: return null
    return objectValue.stringValue(
        "url",
        "fileUrl",
        "imageUrl",
        "videoUrl",
        "thumbUrl",
        "previewUrl",
        "content",
        "nickname",
        "nickName",
        "displayName",
        "name",
        "wxid"
    )
}

internal fun JsonElement.primitiveText(): String? {
    return runCatching { jsonPrimitive.contentOrNull }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != "null" }
}

private fun scrmMomentDisplayTime(epochSecondsOrMillis: Long?): String {
    val millis = scrmMomentCreatedAt(epochSecondsOrMillis)
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun scrmMomentCreatedAt(epochSecondsOrMillis: Long?): Long {
    val value = epochSecondsOrMillis ?: return System.currentTimeMillis()
    return if (value in 1L..9_999_999_999L) value * 1_000L else value
}

