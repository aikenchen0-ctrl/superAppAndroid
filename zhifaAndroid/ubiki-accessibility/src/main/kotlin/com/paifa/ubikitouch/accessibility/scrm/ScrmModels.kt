package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ScrmSendTextMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val content: String,
    val atIds: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(conversationId.isNotBlank()) { "conversationId 不能为空" }
        require(content.isNotBlank()) { "消息内容不能为空" }
        require(atIds == null || atIds.isNotBlank()) { "atIds 不能为空" }
    }
}

@Serializable
internal data class ScrmSendImageMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val imageUrl: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(imageUrl.isNotBlank()) { "imageUrl cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendVideoMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val videoUrl: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(videoUrl.isNotBlank()) { "videoUrl cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendVoiceMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val voiceUrl: String,
    val durationSeconds: Int
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(voiceUrl.isNotBlank()) { "voiceUrl cannot be blank" }
        require(durationSeconds > 0) { "durationSeconds must be greater than 0" }
    }
}

@Serializable
internal data class ScrmSendFileMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val fileName: String,
    val fileUrl: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(fileName.isNotBlank()) { "fileName cannot be blank" }
        require(fileUrl.isNotBlank()) { "fileUrl cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendLinkCardMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val url: String,
    val title: String,
    val description: String? = null,
    val thumb: String? = null,
    val appId: String? = null,
    val sourceName: String? = null,
    val source: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(url.isNotBlank()) { "url cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(description == null || description.isNotBlank()) { "description cannot be blank" }
        require(thumb == null || thumb.isNotBlank()) { "thumb cannot be blank" }
        require(appId == null || appId.isNotBlank()) { "appId cannot be blank" }
        require(sourceName == null || sourceName.isNotBlank()) { "sourceName cannot be blank" }
        require(source == null || source.isNotBlank()) { "source cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendNoteCardMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val title: String,
    val description: String? = null,
    val thumb: String? = null,
    val recordItem: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(description == null || description.isNotBlank()) { "description cannot be blank" }
        require(thumb == null || thumb.isNotBlank()) { "thumb cannot be blank" }
        require(recordItem.isNotBlank()) { "recordItem cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendQuoteMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val content: String,
    val quoteMsgSvrId: Long
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(conversationId.isNotBlank()) { "conversationId cannot be blank" }
        require(content.isNotBlank()) { "content cannot be blank" }
        require(quoteMsgSvrId > 0L) { "quoteMsgSvrId must be greater than 0" }
    }
}

internal data class ScrmMediaUploadRequest(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
) {
    init {
        require(fileName.isNotBlank()) { "fileName cannot be blank" }
        require(contentType.isNotBlank()) { "contentType cannot be blank" }
        require(bytes.isNotEmpty()) { "bytes cannot be empty" }
    }
}

internal object ScrmMomentAttachmentType {
    const val Text: Int = 0
    const val Image: Int = 2
    const val Video: Int = 3
}

@Serializable
internal data class ScrmMomentPostAttachment(
    val type: Int,
    val content: List<String>? = null
) {
    init {
        require(type in setOf(0, 2, 3, 4, 5, 6, 7)) { "unsupported moment attachment type" }
        require(content == null || content.all { it.isNotBlank() }) {
            "moment attachment content cannot contain blank values"
        }
    }
}

@Serializable
internal data class ScrmMomentPostPayload(
    val clientRequestId: String? = null,
    val weChatId: String? = null,
    val content: String? = null,
    val attachment: ScrmMomentPostAttachment? = null,
    val comment: String? = null,
    val sendSlow: Boolean = false,
    val extComment: List<String>? = null,
    val notiUsers: List<String>? = null
) {
    init {
        require(clientRequestId == null || clientRequestId.isNotBlank()) {
            "clientRequestId cannot be blank"
        }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(content == null || content.isNotBlank()) { "content cannot be blank" }
        require(comment == null || comment.isNotBlank()) { "comment cannot be blank" }
        require(extComment == null || extComment.all { it.isNotBlank() }) {
            "extComment cannot contain blank values"
        }
        require(notiUsers == null || notiUsers.all { it.isNotBlank() }) {
            "notiUsers cannot contain blank values"
        }
    }
}

@Serializable
internal data class ScrmPostMomentRequest(
    val payload: ScrmMomentPostPayload? = null,
    val clientRequestId: String? = null,
    val content: String? = null,
    val attachmentType: String? = null,
    val attachments: List<String>? = null,
    val comment: String? = null,
    val sendSlow: Boolean = false,
    val deviceUuid: String,
    val weChatId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(clientRequestId == null || clientRequestId.isNotBlank()) {
            "clientRequestId cannot be blank"
        }
        require(content == null || content.isNotBlank()) { "content cannot be blank" }
        require(attachmentType == null || attachmentType.isNotBlank()) {
            "attachmentType cannot be blank"
        }
        require(attachments == null || attachments.all { it.isNotBlank() }) {
            "attachments cannot contain blank values"
        }
        require(comment == null || comment.isNotBlank()) { "comment cannot be blank" }
        require(
            !content.isNullOrBlank() ||
                !attachments.isNullOrEmpty() ||
                payload?.content?.isNotBlank() == true ||
                payload?.attachment?.content?.isNotEmpty() == true
        ) {
            "moment content or attachment is required"
        }
    }
}

@Serializable
internal data class ScrmSyncMomentsRequest(
    val deviceUuid: String,
    val weChatId: String,
    val startTime: Long,
    val circleIds: List<Long>? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(startTime >= 0L) { "startTime cannot be negative" }
        require(circleIds == null || circleIds.all { it > 0L }) {
            "circleIds must be greater than 0"
        }
    }
}

@Serializable
internal data class ScrmSyncMomentMessagesRequest(
    val deviceUuid: String,
    val weChatId: String,
    val onlyComment: Boolean,
    val getAll: Boolean
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

@Serializable
internal data class ScrmMomentDetailRequest(
    val deviceUuid: String,
    val weChatId: String,
    val circleId: Long,
    val getBigMap: Boolean = false
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(circleId > 0L) { "circleId must be greater than 0" }
    }
}

@Serializable
internal data class ScrmMomentLikeRequest(
    val deviceUuid: String,
    val weChatId: String,
    val circleId: Long,
    val isCancel: Boolean
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(circleId > 0L) { "circleId must be greater than 0" }
    }
}

@Serializable
internal data class ScrmMomentCommentRequest(
    val deviceUuid: String,
    val weChatId: String,
    val circleId: Long,
    val toWeChatId: String? = null,
    val content: String,
    val replyCommentId: Long,
    val isResend: Boolean
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(circleId > 0L) { "circleId must be greater than 0" }
        require(toWeChatId == null || toWeChatId.isNotBlank()) { "toWeChatId cannot be blank" }
        require(content.isNotBlank()) { "content cannot be blank" }
        require(replyCommentId >= 0L) { "replyCommentId cannot be negative" }
    }
}

@Serializable
internal data class ScrmMomentCommentDeleteRequest(
    val deviceUuid: String,
    val weChatId: String,
    val circleId: Long,
    val commentId: Long,
    val publishTime: Long
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(circleId > 0L) { "circleId must be greater than 0" }
        require(commentId > 0L) { "commentId must be greater than 0" }
        require(publishTime >= 0L) { "publishTime cannot be negative" }
    }
}

internal data class ScrmMomentMaterialQuery(
    val tenantId: String? = null,
    val skip: Int = 0,
    val take: Int = 50
) {
    init {
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(skip >= 0) { "skip cannot be negative" }
        require(take > 0) { "take must be greater than 0" }
    }
}

@Serializable
internal data class ScrmMomentMaterialCreateRequest(
    val payload: ScrmMomentPostPayload? = null,
    val clientRequestId: String? = null,
    val content: String? = null,
    val attachmentType: String? = null,
    val attachments: List<String>? = null,
    val appMsgJsonData: String? = null,
    val linkUrl: String? = null,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkThumb: String? = null,
    val linkAppId: String? = null,
    val linkSourceName: String? = null,
    val linkSource: String? = null,
    val comment: String? = null,
    val extComments: List<String>? = null,
    val notiUsers: List<String>? = null,
    val sendSlow: Boolean = false,
    val visibleType: String? = null,
    val labelNames: List<String>? = null,
    val labelName: String? = null,
    val friendWxids: List<String>? = null,
    val invisibleFriendWxids: List<String>? = null,
    val tenantId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val enableImmediately: Boolean = false,
    val antiFoldStrategyJson: String? = null,
    val variableSchemaJson: String? = null
) {
    init {
        require(clientRequestId == null || clientRequestId.isNotBlank()) {
            "clientRequestId cannot be blank"
        }
        require(content == null || content.isNotBlank()) { "content cannot be blank" }
        require(attachmentType == null || attachmentType.isNotBlank()) {
            "attachmentType cannot be blank"
        }
        require(attachments == null || attachments.all { it.isNotBlank() }) {
            "attachments cannot contain blank values"
        }
        require(comment == null || comment.isNotBlank()) { "comment cannot be blank" }
        require(extComments == null || extComments.all { it.isNotBlank() }) {
            "extComments cannot contain blank values"
        }
        require(notiUsers == null || notiUsers.all { it.isNotBlank() }) {
            "notiUsers cannot contain blank values"
        }
        require(labelNames == null || labelNames.all { it.isNotBlank() }) {
            "labelNames cannot contain blank values"
        }
        require(friendWxids == null || friendWxids.all { it.isNotBlank() }) {
            "friendWxids cannot contain blank values"
        }
        require(invisibleFriendWxids == null || invisibleFriendWxids.all { it.isNotBlank() }) {
            "invisibleFriendWxids cannot contain blank values"
        }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(name == null || name.isNotBlank()) { "name cannot be blank" }
        require(category == null || category.isNotBlank()) { "category cannot be blank" }
    }
}

@Serializable
internal data class ScrmMomentMaterialUpdateRequest(
    val payload: ScrmMomentPostPayload? = null,
    val clientRequestId: String? = null,
    val content: String? = null,
    val attachmentType: String? = null,
    val attachments: List<String>? = null,
    val comment: String? = null,
    val extComments: List<String>? = null,
    val notiUsers: List<String>? = null,
    val sendSlow: Boolean = false,
    val visibleType: String? = null,
    val labelNames: List<String>? = null,
    val labelName: String? = null,
    val friendWxids: List<String>? = null,
    val invisibleFriendWxids: List<String>? = null,
    val tenantId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val enableImmediately: Boolean = false,
    val antiFoldStrategyJson: String? = null,
    val variableSchemaJson: String? = null
)

@Serializable
internal data class ScrmMomentMaterialCopyRequest(
    val name: String? = null,
    val enableImmediately: Boolean = false
) {
    init {
        require(name == null || name.isNotBlank()) { "name cannot be blank" }
    }
}

@Serializable
internal data class ScrmMomentMaterialControlRequest(
    val reason: String? = null
) {
    init {
        require(reason == null || reason.isNotBlank()) { "reason cannot be blank" }
    }
}

@Serializable
internal data class ScrmMomentMaterial(
    val id: Long,
    val tenantId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val status: Int = 0,
    val statusName: String? = null,
    val contentHash: String? = null,
    val attachmentHash: String? = null,
    val commentHash: String? = null,
    val attachmentType: Int = 0,
    val attachmentCount: Int = 0,
    val extCommentCount: Int = 0,
    val sendSlow: Boolean = false,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: contentHash?.takeIf { it.isNotBlank() }
            ?: "素材 #$id"
}

@Serializable
internal data class ScrmMomentMaterialDetail(
    val id: Long,
    val tenantId: String? = null,
    val name: String? = null,
    val category: String? = null,
    val status: Int = 0,
    val statusName: String? = null,
    val contentHash: String? = null,
    val attachmentHash: String? = null,
    val commentHash: String? = null,
    val attachmentType: Int = 0,
    val attachmentCount: Int = 0,
    val extCommentCount: Int = 0,
    val sendSlow: Boolean = false,
    val template: ScrmMomentPostPayload? = null,
    val antiFoldStrategyJson: String? = null,
    val variableSchemaJson: String? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: contentHash?.takeIf { it.isNotBlank() }
            ?: "素材 #$id"
}

@Serializable
internal data class ScrmMediaUploadResponse(
    val success: Boolean,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val storedFileName: String? = null,
    val mediaType: String? = null,
    val extension: String? = null,
    val contentType: String? = null,
    val fileSize: Long = 0L,
    val warnings: List<String>? = null,
    val message: String? = null
)

@Serializable
internal data class ScrmVoiceUploadResponse(
    val success: Boolean,
    val voiceUrl: String? = null,
    val originalUrl: String? = null,
    val previewUrl: String? = null,
    val previewMediaPath: String? = null,
    val durationSeconds: Int = 0,
    val format: String? = null,
    val wireFormat: String? = null,
    val headerHex: String? = null,
    val headerAscii: String? = null,
    val amrFrameDurationSeconds: Int = 0,
    val pcPlaybackNote: String? = null,
    val originalFormat: String? = null,
    val previewFormat: String? = null,
    val fileSize: Long = 0L,
    val originalFileSize: Long = 0L,
    val warnings: List<String>? = null,
    val message: String? = null
)

internal data class ScrmContactQuery(
    val weChatId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 100,
    val search: String? = null,
    val includeDeleted: Boolean = false,
    val onlyFriends: Boolean = true,
    val includeProfile: Boolean = false
) {
    init {
        require(page > 0) { "page 必须大于 0" }
        require(pageSize in 1..200) { "pageSize 必须在 1 到 200 之间" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(search == null || search.isNotBlank()) { "search 不能为空" }
    }
}

internal data class ScrmChatRoomQuery(
    val weChatId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 100,
    val search: String? = null,
    val includeDeleted: Boolean = false
) {
    init {
        require(page > 0) { "page 蹇呴』澶т簬 0" }
        require(pageSize in 1..200) { "pageSize 蹇呴』鍦?1 鍒?200 涔嬮棿" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId 涓嶈兘涓虹┖" }
        require(search == null || search.isNotBlank()) { "search 涓嶈兘涓虹┖" }
    }
}

internal data class ScrmChatRoomMemberQuery(
    val weChatId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 200,
    val search: String? = null,
    val includeDeleted: Boolean = false
) {
    init {
        require(page > 0) { "page must be greater than 0" }
        require(pageSize in 1..200) { "pageSize must be between 1 and 200" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(search == null || search.isNotBlank()) { "search cannot be blank" }
    }
}

@Serializable
internal data class ScrmContactPage(
    val items: List<ScrmContact> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 100
)

@Serializable
internal data class ScrmChatRoomPage(
    val items: List<ScrmChatRoom> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 100
)

@Serializable
internal data class ScrmChatRoomMemberPage(
    val items: List<ScrmChatRoomMember> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 200
)

@Serializable
internal data class ScrmContact(
    val id: Int,
    val ownerWxid: String? = null,
    val wxid: String? = null,
    val friendNo: String? = null,
    val nickname: String? = null,
    val remarks: String? = null,
    val avatar: String? = null,
    val labelIds: List<Int>? = null,
    val source: String? = null,
    val sourceExt: String? = null,
    val contactType: Int = 0,
    val wechatCreateTime: Long = 0L,
    val verifyFlag: Int = 0,
    val friendPermissionMask: Int = 0,
    val friendPermissionSynced: Boolean = false,
    val isFriend: Int = 0,
    val isBlocked: Int = 0,
    val isStarred: Int = 0,
    val isDeleted: Boolean = false,
    val lastInteractionTime: String? = null,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = remarks?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: friendNo?.takeIf { it.isNotBlank() }
            ?: wxid?.takeIf { it.isNotBlank() }
            ?: "未知联系人"
}

@Serializable
internal data class ScrmChatRoom(
    val id: Int,
    val ownerWxid: String? = null,
    val chatRoomId: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val ownerMemberWxid: String? = null,
    val memberCount: Int = 0,
    val groupStatus: Int = 0,
    val isDeleted: Boolean = false,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: chatRoomId?.takeIf { it.isNotBlank() }
            ?: "Unknown group"
}

@Serializable
internal data class ScrmChatRoomMember(
    val id: Int,
    val chatRoomId: String? = null,
    val memberWxid: String? = null,
    val nickname: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val remarks: String? = null,
    val memberRole: Int = 0,
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val updatedAt: String? = null
) {
    val displayNameValue: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: remarks?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: memberWxid?.takeIf { it.isNotBlank() }
            ?: "Unknown member"
}

@Serializable
internal data class ScrmCreateChatRoomRequest(
    val deviceUuid: String,
    val weChatId: String,
    val memberWxids: List<String>
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(memberWxids.isNotEmpty()) { "memberWxids cannot be empty" }
        require(memberWxids.all { it.isNotBlank() }) { "memberWxids cannot contain blank values" }
    }
}

@Serializable
internal data class ScrmChatRoomMemberMutationRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val memberWxids: List<String>
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(memberWxids.isNotEmpty()) { "memberWxids cannot be empty" }
        require(memberWxids.all { it.isNotBlank() }) { "memberWxids cannot contain blank values" }
    }
}

@Serializable
internal data class ScrmSyncChatRoomsRequest(
    val deviceUuid: String,
    val weChatId: String,
    val flag: Int = 0
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

@Serializable
internal data class ScrmRefreshChatRoomRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val flag: Int = 0
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
    }
}

@Serializable
internal data class ScrmRenameChatRoomRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val name: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(name.isNotBlank()) { "name cannot be blank" }
    }
}

@Serializable
internal data class ScrmSetChatRoomNoticeRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val notice: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(notice.isNotBlank()) { "notice cannot be blank" }
    }
}

@Serializable
internal data class ScrmChatRoomActionRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
    }
}

@Serializable
internal data class ScrmSyncContactsRequest(
    val deviceUuid: String,
    val weChatId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
    }
}

@Serializable
internal data class ScrmAddFriendRequest(
    val deviceUuid: String,
    val weChatId: String,
    val friendWxid: String,
    val message: String? = null,
    val remark: String? = null,
    val label: String? = null,
    val scene: Int = 0,
    val permission: Int = 0
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(friendWxid.isNotBlank()) { "friendWxid 不能为空" }
    }
}

@Serializable
internal data class ScrmFindContactRequest(
    val deviceUuid: String,
    val weChatId: String,
    val content: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(content.isNotBlank()) { "content cannot be blank" }
    }
}

@Serializable
internal data class ScrmAddFriendsByPhoneRequest(
    val deviceUuid: String,
    val weChatId: String,
    val phones: List<String>,
    val message: String? = null,
    val remark: String? = null,
    val label: String? = null,
    val permission: Int = 0
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(phones.isNotEmpty()) { "phones cannot be empty" }
        require(phones.all { it.isNotBlank() }) { "phones cannot contain blank values" }
        require(message == null || message.isNotBlank()) { "message cannot be blank" }
        require(remark == null || remark.isNotBlank()) { "remark cannot be blank" }
        require(label == null || label.isNotBlank()) { "label cannot be blank" }
    }
}

@Serializable
internal data class ScrmSendFriendVerifyRequest(
    val deviceUuid: String,
    val weChatId: String,
    val friendId: String,
    val message: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(friendId.isNotBlank()) { "friendId cannot be blank" }
        require(message == null || message.isNotBlank()) { "message cannot be blank" }
    }
}

@Serializable
internal data class ScrmDeleteFriendRequest(
    val deviceUuid: String,
    val weChatId: String,
    val friendId: String
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(friendId.isNotBlank()) { "friendId 不能为空" }
    }
}

@Serializable
internal data class ScrmFriendRequest(
    val id: Int,
    val ownerWxid: String? = null,
    val wechatAccountId: Long = 0L,
    val requestWxid: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val gender: Int? = null,
    val region: String? = null,
    val source: String? = null,
    val requestMessage: String? = null,
    val status: Int = 0,
    val requestTime: String? = null,
    val responseTime: String? = null,
    val responseMessage: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: requestWxid?.takeIf { it.isNotBlank() }
            ?: "好友申请"
}

@Serializable
internal data class ScrmHandleFriendRequestRequest(
    val deviceUuid: String,
    val weChatId: String,
    val friendId: String,
    val friendNick: String? = null,
    val remark: String? = null,
    val replyMsg: String? = null,
    val addWithWW: Boolean = false,
    val onlyWW: Boolean = false,
    val permission: Int = 0,
    val operation: ScrmFriendRequestOperation
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(friendId.isNotBlank()) { "friendId 不能为空" }
    }
}

@Serializable(with = ScrmFriendRequestOperationSerializer::class)
internal enum class ScrmFriendRequestOperation(val wireValue: Int) {
    Reject(0),
    Accept(1)
}

private object ScrmFriendRequestOperationSerializer : KSerializer<ScrmFriendRequestOperation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScrmFriendRequestOperation", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ScrmFriendRequestOperation) {
        encoder.encodeInt(value.wireValue)
    }

    override fun deserialize(decoder: Decoder): ScrmFriendRequestOperation {
        val wireValue = decoder.decodeInt()
        return ScrmFriendRequestOperation.entries.firstOrNull { it.wireValue == wireValue }
            ?: ScrmFriendRequestOperation.Reject
    }
}

@Serializable
internal data class ScrmTaskSubmissionResult(
    val taskId: Long,
    val success: Boolean,
    val message: String? = null,
    val data: JsonElement? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null
) {
    init {
        require(taskId > 0) { "taskId 必须大于 0" }
    }

    override fun toString(): String {
        return "ScrmTaskSubmissionResult(taskId=$taskId, success=$success, " +
            "hasMessage=${message != null}, hasData=${data != null})"
    }
}

@Serializable
internal data class ScrmMe(
    val userId: String? = null,
    val userName: String? = null,
    val email: String? = null,
    val authType: String? = null,
    val openApiKeyId: Long? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null
)

@Serializable
internal data class ScrmDevice(
    val uuid: String? = null,
    val ownerId: String? = null,
    val isOnline: Boolean,
    val status: Int,
    val weChatId: String? = null,
    val phoneBrand: String? = null,
    val phoneModel: String? = null,
    val androidApi: Int,
    val appPackageName: String? = null,
    val appVersion: String? = null,
    val appVersionCode: Int,
    val lastLoginAt: String? = null,
    val updatedAt: String
)

@Serializable
internal data class ScrmWechatAccount(
    val wxid: String? = null,
    val nickname: String? = null,
    val clientUuid: String? = null,
    val ownerId: String? = null,
    val accountStatus: Int? = null,
    val lastOnlineAt: String? = null,
    val avatar: String? = null
)

@Serializable
internal data class ScrmQuickStart(
    val success: Boolean,
    val apiVersion: String? = null,
    val serverTime: String,
    val landingStatus: String? = null,
    val landingStatusText: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val authType: String? = null,
    val openApiKeyId: Long? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null,
    val deviceCount: Int,
    val weChatAccountCount: Int,
    val devicePreviewLimit: Int,
    val weChatAccountPreviewLimit: Int,
    val devices: List<ScrmDevice>? = null,
    val weChatAccounts: List<ScrmWechatAccount>? = null,
    val selectedDeviceUuid: String? = null,
    val selectedWeChatId: String? = null,
    val capabilitiesUrl: String? = null,
    val testPlanUrl: String? = null,
    val taskResultUrlTemplate: String? = null,
    val recentTaskResultsUrl: String? = null,
    val voiceEnvironmentUrl: String? = null,
    val cardTemplatesUrl: String? = null,
    val swaggerDocsUrl: String? = null,
    val openApiJsonUrl: String? = null,
    val openApiGuideUrl: String? = null,
    val openApiKeysUrl: String? = null,
    val openApiDocsHint: String? = null,
    val nextStep: String? = null,
    val nextCurlExample: String? = null,
    val nextSettingsPresetUrl: String? = null,
    val nextBlockers: List<String>? = null,
    val nextRecommendedActions: List<String>? = null,
    val recommendedChecks: List<String>? = null,
    val warnings: List<String>? = null
)

@Serializable
internal data class ScrmCapabilityStatus(
    val code: String? = null,
    val name: String? = null,
    val group: String? = null,
    val groupName: String? = null,
    val status: String? = null,
    val settingKey: String? = null,
    val runtimeEffectiveKey: String? = null,
    val requiredPermission: String? = null,
    val httpMethod: String? = null,
    val route: String? = null,
    val settingsPresetUrl: String? = null,
    val minimalTestHint: String? = null,
    val serverConfigured: Boolean,
    val permissionAllowed: Boolean,
    val assetAllowed: Boolean,
    val runtimeEffective: Boolean? = null,
    val serverWouldAllow: Boolean,
    val readyForTest: Boolean,
    val requiresRuntimeSnapshot: Boolean,
    val requiresSettingsPush: Boolean,
    val requiresAndroidEffective: Boolean,
    val blockers: List<String>? = null,
    val recommendedActions: List<String>? = null,
    val nextStep: String? = null
)

@Serializable
internal data class ScrmCapabilityGroup(
    val group: String? = null,
    val groupName: String? = null,
    val totalCount: Int,
    val readyCount: Int,
    val pausedCount: Int,
    val blockedCount: Int,
    val unknownRuntimeCount: Int
)

@Serializable
internal data class ScrmCapabilities(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val deviceAccessible: Boolean,
    val accountAccessible: Boolean,
    val hasRuntimeSnapshot: Boolean,
    val runtimeSnapshotSource: String? = null,
    val runtimeSnapshotReceivedAt: String? = null,
    val capabilities: List<ScrmCapabilityStatus>? = null,
    val totalCount: Int,
    val readyCount: Int,
    val blockedCount: Int,
    val unknownRuntimeCount: Int,
    val pausedCount: Int,
    val groups: List<ScrmCapabilityGroup>? = null,
    val recommendedChecks: List<String>? = null,
    val warnings: List<String>? = null
)

@Serializable
internal data class ScrmTaskResult(
    val taskId: Long,
    val success: Boolean,
    val status: String? = null,
    val resultUnknown: Boolean,
    val resultCode: String? = null,
    val message: String? = null,
    val deviceUuid: String? = null,
    val connectionIdHash: String? = null,
    val receivedAt: String,
    val rawHidden: Boolean,
    val data: JsonElement? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null,
    val nextStep: String? = null
) {
    override fun toString(): String {
        return "ScrmTaskResult(taskId=$taskId, success=$success, status=$status, " +
            "resultUnknown=$resultUnknown, resultCode=$resultCode, rawHidden=$rawHidden, " +
            "hasData=${data != null})"
    }
}

@Serializable
internal data class ScrmRecentTaskResults(
    val deviceUuid: String? = null,
    val count: Int,
    val items: List<ScrmTaskResult>? = null,
    val warnings: List<String>? = null
)
