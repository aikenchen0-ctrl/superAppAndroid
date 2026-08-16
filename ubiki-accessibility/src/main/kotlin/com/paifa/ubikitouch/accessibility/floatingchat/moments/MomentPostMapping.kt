package com.paifa.ubikitouch.accessibility.floatingchat.moments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.paifa.ubikitouch.accessibility.AppMomentComment
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.AppMomentPost
import com.paifa.ubikitouch.accessibility.MomentMediaKind
import com.paifa.ubikitouch.accessibility.data.LocalMomentComment
import com.paifa.ubikitouch.accessibility.data.LocalMomentPost
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatVisibilityScope

internal fun AppMomentPost.toFloatingChatMediaMessage(): FloatingChatMessage? {
    val media = media ?: return null
    val type = when (media.kind) {
        MomentMediaKind.Image -> FloatingChatMessageType.ImageThumbnail
        MomentMediaKind.Video -> FloatingChatMessageType.VideoPreview
        MomentMediaKind.Link -> return null
    }
    return FloatingChatMessage(
        id = "moment-preview-$id",
        type = type,
        text = content,
        fromMe = false,
        senderName = author,
        time = time,
        presentation = FloatingChatMessagePresentation.MediaStandalone,
        connectionTarget = FloatingChatConnectionTarget.None,
        thumbnailOrientation = media.orientation,
        mediaAspectRatio = media.aspectRatio,
        thumbnailUrl = media.previewUri ?: media.uri,
        resourceUrl = media.uri ?: media.previewUri,
        mediaMimeType = if (media.kind == MomentMediaKind.Video) "video/mp4" else "image/jpeg",
        visibility = FloatingChatVisibilityScope.Public,
        accessState = FloatingChatAccessState.Visible
    )
}

internal fun AppMomentPost.toLocalMomentPost(): LocalMomentPost {
    return LocalMomentPost(
        postId = id,
        accountId = accountId,
        author = author,
        content = content,
        displayTime = time,
        avatarText = avatarText,
        avatarColor = avatarColor.toArgb().toLong(),
        mediaKind = media?.kind?.name,
        mediaUri = media?.uri,
        mediaPreviewUri = media?.previewUri,
        mediaOrientation = media?.orientation?.name,
        mediaAspectRatio = media?.aspectRatio,
        mediaWidthDp = media?.widthDp,
        mediaHeightDp = media?.heightDp,
        mediaColor = media?.color?.toArgb()?.toLong(),
        mediaLabel = media?.label,
        linkTitle = linkTitle,
        linkUrl = linkUrl,
        sourceLabel = sourceLabel,
        likedBy = likedBy,
        comments = comments.map { comment ->
            LocalMomentComment(
                author = comment.author,
                text = comment.text,
                id = comment.id,
                authorWxId = comment.authorWxId,
                replyTo = comment.replyTo,
                replyCommentId = comment.replyCommentId
            )
        },
        createdAt = createdAt,
        authorWxId = authorWxId,
        circleId = circleId,
        publishTime = publishTime
    )
}

internal fun LocalMomentPost.toAppMomentPost(): AppMomentPost {
    return AppMomentPost(
        id = postId,
        accountId = accountId,
        author = author,
        content = content,
        time = displayTime,
        avatarText = avatarText,
        avatarColor = Color(avatarColor),
        media = mediaKind?.let { kind ->
            AppMomentMedia(
                kind = enumValueOrDefault(kind, MomentMediaKind.Link),
                uri = mediaUri,
                previewUri = mediaPreviewUri,
                orientation = mediaOrientation?.let { enumValueOrDefault(it, FloatingChatThumbnailOrientation.Vertical) }
                    ?: FloatingChatThumbnailOrientation.Vertical,
                aspectRatio = mediaAspectRatio,
                widthDp = mediaWidthDp ?: 88,
                heightDp = mediaHeightDp ?: 88,
                color = Color(mediaColor ?: 0xFF9B5353),
                label = mediaLabel
            )
        },
        linkTitle = linkTitle,
        linkUrl = linkUrl,
        sourceLabel = sourceLabel,
        likedBy = likedBy,
        comments = comments.map { comment ->
            AppMomentComment(
                author = comment.author,
                text = comment.text,
                id = comment.id,
                authorWxId = comment.authorWxId,
                replyTo = comment.replyTo,
                replyCommentId = comment.replyCommentId
            )
        },
        authorWxId = authorWxId,
        circleId = circleId,
        publishTime = publishTime,
        createdAt = createdAt
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
