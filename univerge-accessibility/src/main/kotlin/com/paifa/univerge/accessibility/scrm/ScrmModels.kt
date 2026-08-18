package com.paifa.univerge.accessibility.scrm

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ScrmSendLuckyMoneyRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val friendId: String? = null,
    val moneyFen: Int? = null,
    val number: Int? = null,
    val paymentPassword: String? = null,
    val wish: String? = null
)

@Serializable
internal data class ScrmTakeLuckyMoneyByMessageRequest(
    val deviceUuid: String? = null,
    val messageId: Long? = null,
    val refuse: Boolean? = null
)

@Serializable
internal data class ScrmRedPacketQueryByMessageRequest(
    val deviceUuid: String? = null,
    val messageId: Long? = null
)

@Serializable
internal data class ScrmSendRemittanceRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val friendId: String? = null,
    val roomId: String? = null,
    val moneyFen: Int? = null,
    val paymentPassword: String? = null,
    val memo: String? = null
)

@Serializable
internal data class ScrmTakeTransferByMessageRequest(
    val deviceUuid: String? = null,
    val messageId: Long? = null,
    val refuse: Boolean? = null
)

@Serializable
internal data class ScrmWalletBalanceRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val flag: Int? = null
)

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
    val notiUsers: List<String>? = null,
    val visible: ScrmMomentVisibilityPayload? = null,
    val poi: ScrmMomentPoi? = null
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

/** iOS 朋友圈发布请求中的位置对象，字段名与 OpenAPI moments 接口保持一致。 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class ScrmMomentPoi(
    val name: String? = null,
    val city: String? = null,
    val address: String? = null,
    @EncodeDefault
    val lat: Double = 0.0,
    @EncodeDefault
    val lng: Double = 0.0,
    @EncodeDefault
    val poiId: String = ""
) {
    init {
        require(name == null || name.isNotBlank()) { "poi name cannot be blank" }
        require(city == null || city.isNotBlank()) { "poi city cannot be blank" }
        require(address == null || address.isNotBlank()) { "poi address cannot be blank" }
    }
}

/** iOS payload.visible 的数值枚举及其可选好友列表。 */
@Serializable
internal data class ScrmMomentVisibilityPayload(
    val type: Int,
    val friends: List<String>? = null
) {
    init {
        require(type in 0..3) { "unsupported moment visibility type" }
        require(friends == null || friends.all { it.isNotBlank() }) {
            "moment visibility friends cannot contain blank values"
        }
    }
}

/**
 * 朋友圈发表页使用的真实请求选项。公开、私密、部分可见和不给谁看均映射到 iOS 的 API 值。
 * 测试流程：在发表页切换范围、选择好友、填写提醒和位置，确认请求 JSON 同时包含顶层及 payload 字段。
 */
internal enum class ScrmMomentVisibility(
    val label: String,
    val apiValue: String,
    val payloadType: Int
) {
    Public("公开", "public", 0),
    Private("私密", "private", 1),
    PartVisible("部分可见", "partVisible", 2),
    NotVisible("不给谁看", "notVisible", 3)
}

internal data class ScrmMomentPublishOptions(
    val visibility: ScrmMomentVisibility = ScrmMomentVisibility.Public,
    val selectedFriendWxids: List<String> = emptyList(),
    val remindWxids: List<String> = emptyList(),
    val poi: ScrmMomentPoi? = null
) {
    init {
        require(selectedFriendWxids.all { it.isNotBlank() }) {
            "selectedFriendWxids cannot contain blank values"
        }
        require(remindWxids.all { it.isNotBlank() }) {
            "remindWxids cannot contain blank values"
        }
    }

    val normalizedSelectedFriendWxids: List<String>
        get() = selectedFriendWxids.distinct()

    val normalizedRemindWxids: List<String>
        get() = remindWxids.distinct()
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
    val notiUsers: List<String>? = null,
    val visibleType: String? = null,
    val friendWxids: List<String>? = null,
    val invisibleFriendWxids: List<String>? = null,
    val poi: ScrmMomentPoi? = null,
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
        require(notiUsers == null || notiUsers.all { it.isNotBlank() }) {
            "notiUsers cannot contain blank values"
        }
        require(visibleType == null || visibleType in setOf("public", "private", "partVisible", "notVisible")) {
            "unsupported moment visibility value"
        }
        require(friendWxids == null || friendWxids.all { it.isNotBlank() }) {
            "friendWxids cannot contain blank values"
        }
        require(invisibleFriendWxids == null || invisibleFriendWxids.all { it.isNotBlank() }) {
            "invisibleFriendWxids cannot contain blank values"
        }
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

/** 对应 OpenAPI copy-finder-material 请求，preferredType 使用 live 创建直播素材。 */
@Serializable
internal data class ScrmMomentCopyFinderMaterialRequest(
    val deviceUuid: String,
    val weChatId: String,
    val snsId: Long,
    val preferredType: String,
    val materialName: String? = null,
    val tenantId: String? = null,
    val enableImmediately: Boolean = true
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(snsId > 0L) { "snsId must be greater than 0" }
        require(preferredType == "live" || preferredType == "dynamic") {
            "preferredType must be live or dynamic"
        }
        require(materialName == null || materialName.isNotBlank()) { "materialName cannot be blank" }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId cannot be blank" }
    }
}

/** 对应 OpenAPI copy-finder-material 响应，服务端不会把失败伪装为可发布素材。 */
@Serializable
internal data class ScrmMomentCopyFinderMaterialResult(
    val success: Boolean = false,
    val message: String? = null,
    val material: ScrmMomentMaterial? = null,
    val detectedType: String? = null,
    val detectedShape: String? = null,
    val publishReady: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val safeSummary: String? = null
)

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
        get() = scrmReadableText(name)?.takeIf { it.isNotBlank() }
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
        get() = scrmReadableText(name)?.takeIf { it.isNotBlank() }
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

internal data class ScrmContactWxidQuery(
    val weChatId: String? = null,
    val search: String? = null,
    val includeDeleted: Boolean = false,
    val onlyFriends: Boolean = true,
    val labelIds: String? = null,
    val labelNames: String? = null,
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null
) {
    init {
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(search == null || search.isNotBlank()) { "search cannot be blank" }
        require(labelIds == null || labelIds.isNotBlank()) { "labelIds cannot be blank" }
        require(labelNames == null || labelNames.isNotBlank()) { "labelNames cannot be blank" }
        require(customerLevel == null || customerLevel.isNotBlank()) { "customerLevel cannot be blank" }
        require(sourceChannel == null || sourceChannel.isNotBlank()) { "sourceChannel cannot be blank" }
        require(profileKey == null || profileKey.isNotBlank()) { "profileKey cannot be blank" }
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
        require(page > 0) { "page 必须大于 0" }
        require(pageSize in 1..200) { "pageSize 必须在 1 到 200 之间" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId 涓嶈兘涓虹┖" }
        require(search == null || search.isNotBlank()) { "search 涓嶈兘涓虹┖" }
    }
}

internal data class ScrmGroupInvitationQuery(
    val weChatId: String? = null,
    val chatRoomId: String? = null,
    val count: Int = 50,
    val pendingOnly: Boolean = true
) {
    init {
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(chatRoomId == null || chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(count in 1..200) { "count must be between 1 and 200" }
    }
}

@Serializable
internal data class ScrmGroupInvitationMember(
    val userName: String? = null,
    val nickName: String? = null,
    val avatar: String? = null
)

@Serializable
internal data class ScrmGroupInvitation(
    val id: Int = 0,
    val weChatId: String? = null,
    val chatRoomId: String? = null,
    val inviter: String? = null,
    val inviteName: String? = null,
    val reason: String? = null,
    val msgId: Long = 0L,
    val msgSvrId: Long = 0L,
    val updateTime: Long = 0L,
    val taskId: Long = 0L,
    val status: Int = 0,
    val source: String? = null,
    val invitationTime: String? = null,
    val updatedAt: String? = null,
    val invited: List<ScrmGroupInvitationMember> = emptyList()
)

internal data class ScrmCommonChatRoomQuery(
    val weChatId: String? = null,
    val page: Int = 1,
    val pageSize: Int = 100,
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
internal data class ScrmContactWxidList(
    val weChatId: String? = null,
    val wxids: List<String> = emptyList(),
    val count: Int = 0
)

/**
 * 人工测试专用：labelIds/labelNames 表示目标好友的新完整标签集合，传空集合即清空标签。
 * 测试前记录原标签；仅在 Web 调试面板和测试手机上发起一次请求，随后用详情读取接口回读确认。
 */
@Serializable
internal data class ScrmSetContactLabelsRequest(
    val deviceUuid: String,
    val weChatId: String,
    val contactId: Int? = null,
    val friendId: String? = null,
    val labelIds: List<Int> = emptyList(),
    val labelNames: List<String> = emptyList()
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require((contactId ?: 0) > 0 || !friendId.isNullOrBlank()) {
            "contactId or friendId must be provided"
        }
        require(contactId == null || contactId > 0) { "contactId must be greater than 0" }
        require(labelIds.all { it > 0 }) { "labelIds must contain positive values" }
        require(labelNames.all { it.isNotBlank() }) { "labelNames cannot contain blank values" }
    }
}

/**
 * 批量标签写入请求，仅供人工联调入口使用。
 *
 * 人工测试前先用 GET /contacts/{contactId}/detail 记录原标签，并且首次只选择一个测试好友、
 * mergeExisting=true、maxCount=1。mergeExisting=false 会用传入集合完整替换现有标签，
 * 空标签集合可能清空生产联系人标签，禁止把这两种组合用于生产联系人测试。
 */
@Serializable
internal data class ScrmBatchSetContactLabelsRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val friendIds: List<String> = emptyList(),
    val labelIds: List<Int> = emptyList(),
    val labelNames: List<String> = emptyList(),
    val mergeExisting: Boolean = true,
    val maxCount: Int = 200
) {
    init {
        require(deviceUuid == null || deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(friendIds.all { it.isNotBlank() }) { "friendIds cannot contain blank values" }
        require(labelIds.all { it > 0 }) { "labelIds must contain positive values" }
        require(labelNames.all { it.isNotBlank() }) { "labelNames cannot contain blank values" }
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
    }
}

@Serializable
internal data class ScrmBatchSetContactLabelsResponse(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val requestedCount: Int = 0,
    val acceptedCount: Int = 0,
    val successCount: Int = 0,
    val unknownCount: Int = 0,
    val failCount: Int = 0,
    val effectiveLabelIds: List<Int> = emptyList(),
    val mergeExisting: Boolean = true,
    val items: List<ScrmBatchTaskItemResult> = emptyList()
)

@Serializable
internal data class ScrmBatchTaskItemResult(
    val targetId: String? = null,
    val success: Boolean = false,
    val status: String? = null,
    val resultUnknown: Boolean = false,
    val resultCode: String? = null,
    val taskId: Long? = null,
    val message: String? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null,
    val data: JsonElement? = null
)

@Serializable
internal data class ScrmChatRoomPage(
    val items: List<ScrmChatRoom> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 100
)

@Serializable
internal data class ScrmCommonChatRoomPage(
    val items: List<ScrmCommonChatRoom> = emptyList(),
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

/** Read-only SCRM chat bootstrap; it mirrors the iOS conversation summary boundary. */
@Serializable
internal data class ScrmChatBootstrap(
    val accountId: String? = null,
    val deviceUuid: String? = null,
    val baselineSequence: Long = 0L,
    val conversations: List<ScrmChatConversationSummary> = emptyList()
)

@Serializable
internal data class ScrmChatConversationSummary(
    val id: Long = 0L,
    val conversationWxid: String? = null,
    val conversationType: Int = 0,
    val displayName: String? = null,
    val displayAvatar: String? = null,
    val unreadCount: Int = 0,
    val messageCount: Int = 0,
    val isPinned: Int = 0,
    val isMuted: Int = 0,
    val lastMessageContent: String? = null,
    val lastMessageTime: String? = null,
    val updatedAt: String? = null
)

@Serializable
internal data class ScrmChatHistory(
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val items: List<ScrmChatMessage> = emptyList()
) {
    val messages: List<ScrmChatMessage>
        get() = items
}

@Serializable
internal data class ScrmChatChanges(
    val afterSequence: Long = 0L,
    val nextSequence: Long = 0L,
    val headSequence: Long = 0L,
    val minAvailableSequence: Long = 0L,
    val hasMore: Boolean = false,
    val items: List<ScrmChatChangeItem> = emptyList()
)

@Serializable
internal data class ScrmChatChangeItem(
    val sequence: Long = 0L,
    val message: ScrmChatMessage? = null
)

@Serializable
internal data class ScrmChatMessage(
    val messageId: Long = 0L,
    val messageServerId: Long? = null,
    val conversationId: Long? = null,
    val senderWxid: String? = null,
    val receiverWxid: String? = null,
    val chatType: Int = 0,
    val messageType: Int = 0,
    val content: String = "",
    val direction: Int = 0,
    val localMessageId: String? = null,
    val clientMessageId: String? = null,
    val sentAt: String? = null,
    val receivedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isRevoked: Boolean = false,
    val isDeleted: Boolean = false,
    val media: List<ScrmChatMedia> = emptyList(),
    val extensions: List<ScrmChatExtension> = emptyList(),
    val voiceText: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
internal data class ScrmChatMedia(
    val mediaType: Int = 0,
    val fileSize: Long = 0L,
    val fileExtension: String = "",
    val uploadStatus: Int = 0,
    val downloadUrl: String = "",
    val mediaUrl: String = "",
    val fileUrl: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val voiceUrl: String = "",
    val url: String = "",
    val path: String = ""
) {
    val resolvedUrl: String?
        get() = listOf(downloadUrl, mediaUrl, fileUrl, imageUrl, videoUrl, voiceUrl, url, path)
            .firstOrNull { it.isNotBlank() }
}

@Serializable
internal data class ScrmChatExtension(
    val key: String = "",
    val value: String = ""
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
    val updatedAt: String? = null,
    val avatarUrl: String? = null,
    val headImgUrl: String? = null,
    val headimgurl: String? = null,
    val imageUrl: String? = null
) {
    val displayAvatarUrl: String?
        get() = scrmDisplayAvatarUrl(avatar, avatarUrl, headImgUrl, headimgurl, imageUrl)

    val displayName: String
        get() = remarks?.takeIf { it.isNotBlank() }
            ?: nickname?.takeIf { it.isNotBlank() }
            ?: friendNo?.takeIf { it.isNotBlank() }
            ?: wxid?.takeIf { it.isNotBlank() }
            ?: "未知联系人"
}

/** Read-only SCRM customer profile; it does not represent WeChat contact metadata. */
internal data class ScrmCustomerProfile(
    val id: Int = 0,
    val contactId: Int = 0,
    val ownerWxid: String? = null,
    val friendWxid: String? = null,
    val displayName: String? = null,
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val sourceDetail: String? = null,
    val profileKey: String? = null,
    val purchaseHistory: String? = null,
    val socialAccounts: String? = null,
    val faceImageUrl: String? = null,
    val notes: String? = null,
    val phone: String? = null,
    val mappedLabelIds: List<Int> = emptyList(),
    val mappedLabelNames: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** A read-only contact page snapshot assembled from SCRM's persisted data. */
internal data class ScrmContactDetail(
    val contact: ScrmContact? = null,
    val customerProfile: ScrmCustomerProfile? = null,
    val labels: List<ScrmContactLabel> = emptyList(),
    val commonChatRooms: List<ScrmCommonChatRoom> = emptyList(),
    val relationLogs: List<ScrmContactRelationLog> = emptyList()
)

@Serializable
internal data class ScrmContactLabel(
    val id: Int = 0,
    val ownerWxid: String? = null,
    val wechatAccountId: Long = 0L,
    val labelId: Int = 0,
    val tagName: String? = null,
    val tagColor: String? = null,
    val tagDescription: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isDeleted: Boolean = false,
    val customerProfileCount: Int = 0,
    val contactCount: Int = 0
)

@Serializable
internal data class ScrmCommonChatRoom(
    val id: Int = 0,
    val ownerWxid: String? = null,
    val friendWxid: String? = null,
    val chatRoomId: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val ownerMemberWxid: String? = null,
    val memberCount: Int = 0,
    val friendDisplayName: String? = null,
    val friendMemberRole: Int = 0,
    val friendIsOwner: Boolean = false,
    val friendIsAdmin: Boolean = false,
    val updatedAt: String? = null
)

@Serializable
internal data class ScrmContactRelationLog(
    val id: Int = 0,
    val contactId: Int = 0,
    val changeType: String? = null,
    val actionText: String? = null,
    val ownerWxid: String? = null,
    val friendWxid: String? = null,
    val oldIsDeleted: Boolean = false,
    val oldIsFriend: Int = 0,
    val oldIsBlocked: Int = 0,
    val newIsDeleted: Boolean = false,
    val newIsFriend: Int = 0,
    val newIsBlocked: Int = 0,
    val reason: Int = 0,
    val sourceNotice: String? = null,
    val createdAt: String? = null
)

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
    val updatedAt: String? = null,
    val avatarUrl: String? = null,
    val headImgUrl: String? = null,
    val headimgurl: String? = null,
    val imageUrl: String? = null
) {
    val displayAvatarUrl: String?
        get() = scrmDisplayAvatarUrl(avatar, avatarUrl, headImgUrl, headimgurl, imageUrl)

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
    val updatedAt: String? = null,
    val avatarUrl: String? = null,
    val headImgUrl: String? = null,
    val headimgurl: String? = null,
    val imageUrl: String? = null
) {
    val displayAvatarUrl: String?
        get() = scrmDisplayAvatarUrl(avatar, avatarUrl, headImgUrl, headimgurl, imageUrl)

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

/** 对应 POST /openapi/v1/friend-requests/pull，仅下发 Android 拉取任务。 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class ScrmPullFriendRequestsRequest(
    val deviceUuid: String,
    val weChatId: String,
    @EncodeDefault
    val startTime: Long = 0,
    @EncodeDefault
    val onlyNew: Boolean = false,
    @EncodeDefault
    val getAll: Boolean = true
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
    }
}

@Serializable(with = ScrmFriendRequestOperationSerializer::class)
internal enum class ScrmFriendRequestOperation(val wireValue: Int) {
    Reject(2),
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
    val avatar: String? = null,
    val avatarUrl: String? = null,
    val headImgUrl: String? = null,
    val headimgurl: String? = null,
    val imageUrl: String? = null
) {
    val displayAvatarUrl: String?
        get() = scrmDisplayAvatarUrl(avatar, avatarUrl, headImgUrl, headimgurl, imageUrl)
}

internal fun scrmDisplayAvatarUrl(vararg candidates: String?): String? {
    return candidates.firstOrNull { candidate -> !candidate.isNullOrBlank() }?.trim()
}

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
