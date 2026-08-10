package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.moments.scrmMomentAvatarColor

private const val LocalScrmMomentPostIdPrefix = "local-scrm-moment:"
private const val CurrentUserMomentLikeName = "\u6211"

internal fun localScrmMomentPostForSubmittedDraft(
    clientRequestId: String,
    weChatId: String,
    content: String,
    media: AppMomentMedia?,
    createdAt: Long = System.currentTimeMillis()
): AppMomentPost {
    val trimmedContent = content.trim()
    require(clientRequestId.isNotBlank()) { "clientRequestId cannot be blank" }
    require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    require(trimmedContent.isNotBlank() || media != null) { "moment content or media is required" }
    return AppMomentPost(
        id = "$LocalScrmMomentPostIdPrefix$clientRequestId",
        author = CurrentUserMomentLikeName,
        content = trimmedContent,
        time = "\u521a\u521a",
        avatarText = CurrentUserMomentLikeName,
        avatarColor = scrmMomentAvatarColor(weChatId),
        media = media,
        comments = emptyList(),
        createdAt = createdAt
    )
}
