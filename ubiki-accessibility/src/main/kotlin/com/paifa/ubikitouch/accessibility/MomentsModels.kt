package com.paifa.ubikitouch.accessibility

import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation

internal enum class MomentMediaKind {
    Image,
    Video,
    Link
}

internal data class AppMomentPost(
    val id: String = "moment-${System.nanoTime()}",
    val accountId: String = "",
    val author: String,
    val content: String,
    val time: String,
    val avatarText: String = author.take(2),
    val avatarColor: Color = Color(0xFF6D8190),
    val media: AppMomentMedia? = null,
    val linkTitle: String? = null,
    val linkUrl: String? = null,
    val sourceLabel: String? = null,
    val likedBy: List<String> = emptyList(),
    val comments: List<AppMomentComment> = emptyList(),
    val authorWxId: String? = null,
    val circleId: Long? = null,
    val publishTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

internal data class AppMomentMedia(
    val kind: MomentMediaKind,
    val uri: String? = null,
    val previewUri: String? = null,
    val orientation: FloatingChatThumbnailOrientation = FloatingChatThumbnailOrientation.Vertical,
    val aspectRatio: Float? = null,
    val widthDp: Int = 88,
    val heightDp: Int = 88,
    val color: Color = Color(0xFF9B5353),
    val label: String? = null
)

internal data class AppMomentComment(
    val author: String,
    val text: String,
    val id: Long? = null,
    val authorWxId: String? = null,
    val replyTo: String? = null,
    val replyCommentId: Long? = null
)

internal fun momentPostsForAccount(
    posts: List<AppMomentPost>,
    accountId: String
): List<AppMomentPost> = posts.filter { post -> post.accountId == accountId }
