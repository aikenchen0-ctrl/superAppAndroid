package com.paifa.ubikitouch.accessibility.data

internal data class LocalChatThread(
    val threadId: String,
    val kind: String,
    val title: String? = null,
    val remoteConversationId: String? = null,
    val accountWeChatId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

internal data class LocalChatMessage(
    val messageId: String,
    val threadId: String,
    val senderId: String? = null,
    val senderName: String,
    val messageType: String,
    val body: String,
    val createdAt: Long,
    val displayTime: String? = null,
    val isFromMe: Boolean,
    val kind: String,
    val presentation: String,
    val connectionTarget: String,
    val connectionTargetId: String? = null,
    val detail: String? = null,
    val quoteAuthor: String? = null,
    val quoteText: String? = null,
    val cardKind: String? = null,
    val cardName: String? = null,
    val cardSubtitle: String? = null,
    val appName: String? = null,
    val locationTitle: String? = null,
    val locationAddress: String? = null,
    val resourceUrl: String? = null,
    val fileName: String? = null,
    val fileFormat: String? = null,
    val fileSizeLabel: String? = null,
    val filePreviewLines: String? = null,
    val visibility: String? = null,
    val accessState: String? = null,
    val thumbnailOrientation: String? = null,
    val mediaAspectRatio: Float? = null,
    val thumbnailUrl: String? = null,
    val mediaDurationMs: Int? = null,
    val mediaMimeType: String? = null,
    val inlineTokens: String? = null,
    val metadataJson: String? = null,
    val remoteMessageServerId: String? = null,
    val remoteTaskId: Long? = null,
    val sendState: String = "LOCAL_ONLY",
    val sendErrorCode: String? = null,
    val sendErrorMessage: String? = null,
    val clientRequestId: String? = null
)

internal data class LocalChatFile(
    val fileId: String,
    val contentKey: String,
    val uri: String,
    val previewUri: String? = null,
    val mimeType: String? = null,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long,
    val metadataJson: String? = null
)

internal data class LocalChatMessageFileRef(
    val messageId: String,
    val fileId: String,
    val role: String,
    val position: Int
)

internal data class LocalChatMessageAttachment(
    val file: LocalChatFile,
    val ref: LocalChatMessageFileRef
)

internal data class LocalMomentPost(
    val postId: String,
    val author: String,
    val content: String,
    val displayTime: String,
    val avatarText: String,
    val avatarColor: Long,
    val mediaKind: String? = null,
    val mediaUri: String? = null,
    val mediaPreviewUri: String? = null,
    val mediaOrientation: String? = null,
    val mediaAspectRatio: Float? = null,
    val mediaWidthDp: Int? = null,
    val mediaHeightDp: Int? = null,
    val mediaColor: Long? = null,
    val mediaLabel: String? = null,
    val linkTitle: String? = null,
    val sourceLabel: String? = null,
    val likedBy: List<String> = emptyList(),
    val comments: List<LocalMomentComment> = emptyList(),
    val createdAt: Long
)

internal data class LocalMomentComment(
    val author: String,
    val text: String
)

internal data class LocalContactProfile(
    val accountId: String,
    val contactId: String,
    val remark: String = "",
    val tags: String = "",
    val memo: String = "",
    val friendCircleVisible: Boolean = true,
    val onlyChat: Boolean = false,
    val phone: String? = null,
    val source: String? = null,
    val addedTime: String? = null,
    val commonGroupCount: Int? = null,
    val updatedAt: Long
)

internal data class LocalGroupProfile(
    val accountId: String,
    val groupId: String,
    val groupName: String = "",
    val remark: String = "",
    val announcement: String = "",
    val myNickname: String = "",
    val mute: Boolean = false,
    val pinned: Boolean = false,
    val saveToContacts: Boolean = false,
    val showMemberNicknames: Boolean = true,
    val showMemberAvatars: Boolean = true,
    val backgroundLabel: String = "",
    val updatedAt: Long
)
