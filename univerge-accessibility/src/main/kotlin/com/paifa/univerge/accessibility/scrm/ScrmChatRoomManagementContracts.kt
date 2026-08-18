package com.paifa.univerge.accessibility.scrm

import kotlinx.serialization.Serializable

/**
 * 群管理扩展契约。全部方法都会下发 Android 任务，当前只供后续群详情/群设置 UI 对接。
 * UI 必须显示目标群和影响成员；建群、邀请、踢人、退出群和转让群主必须二次确认。
 */
internal interface ScrmChatRoomManagementApi {
    fun createChatRoomByFilter(request: ScrmCreateChatRoomByFilterRequest): ScrmTaskSubmissionResult
    fun agreeChatRoomInvite(request: ScrmAgreeChatRoomInviteRequest): ScrmTaskSubmissionResult
    fun approveChatRoomInvite(request: ScrmApproveChatRoomInviteRequest): ScrmTaskSubmissionResult
    fun pullChatRoomInvites(request: ScrmAccountMutationRequest): ScrmTaskSubmissionResult
    fun sendJielong(request: ScrmSendJielongRequest): ScrmTaskSubmissionResult
    fun joinChatRoomByQr(request: ScrmJoinChatRoomByQrRequest): ScrmTaskSubmissionResult
    fun addChatRoomManagers(request: ScrmChatRoomManagersRequest): ScrmTaskSubmissionResult
    fun removeChatRoomManagers(request: ScrmChatRoomManagersRequest): ScrmTaskSubmissionResult
    fun inviteChatRoomMembersByFilter(
        request: ScrmChatRoomMembersByFilterRequest
    ): ScrmTaskSubmissionResult

    fun kickChatRoomMembersByFilter(
        request: ScrmChatRoomMembersByFilterRequest
    ): ScrmTaskSubmissionResult

    fun setChatRoomNewMessageNotify(request: ScrmChatRoomSwitchRequest): ScrmTaskSubmissionResult
    fun setChatRoomRemark(request: ScrmChatRoomTextRequest): ScrmTaskSubmissionResult
    fun setChatRoomSavedToPhonebook(request: ScrmChatRoomSwitchRequest): ScrmTaskSubmissionResult
    fun setChatRoomSelfDisplayName(request: ScrmChatRoomTextRequest): ScrmTaskSubmissionResult
    fun setChatRoomTop(request: ScrmChatRoomSwitchRequest): ScrmTaskSubmissionResult
    fun transferChatRoomOwner(request: ScrmTransferChatRoomOwnerRequest): ScrmTaskSubmissionResult
    fun setChatRoomVerify(request: ScrmChatRoomSwitchRequest): ScrmTaskSubmissionResult
}

/** 按筛选建群前，UI 必须先展示命中好友；首次人工测试只使用两个测试好友。 */
@Serializable
internal data class ScrmCreateChatRoomByFilterRequest(
    val deviceUuid: String,
    val weChatId: String,
    val search: String? = null,
    val filterLabelIds: List<Int> = emptyList(),
    val filterLabelNames: List<String> = emptyList(),
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null,
    val maxCount: Int = 2
) {
    init {
        require(maxCount in 2..200) { "maxCount must be between 2 and 200" }
        require(filterLabelIds.all { it > 0 }) { "filterLabelIds must contain positive values" }
        require(filterLabelNames.all { it.isNotBlank() }) {
            "filterLabelNames cannot contain blank values"
        }
    }
}

@Serializable
internal data class ScrmAgreeChatRoomInviteRequest(
    val deviceUuid: String,
    val weChatId: String,
    val talker: String,
    val msgSvrId: Long,
    val msgContent: String
) {
    init {
        require(talker.isNotBlank()) { "talker cannot be blank" }
        require(msgSvrId > 0L) { "msgSvrId must be greater than 0" }
        require(msgContent.isNotBlank()) { "msgContent cannot be blank" }
    }
}

@Serializable
internal data class ScrmApproveChatRoomInviteRequest(
    val deviceUuid: String,
    val weChatId: String,
    val msgSvrId: Long,
    val roomId: String? = null,
    val msgContent: String,
    val msgId: Long = 0L
) {
    init {
        require(msgSvrId > 0L) { "msgSvrId must be greater than 0" }
        require(msgContent.isNotBlank()) { "msgContent cannot be blank" }
        require(msgId >= 0L) { "msgId cannot be negative" }
    }
}

@Serializable
internal data class ScrmSendJielongRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val content: String,
    val title: String? = null,
    val sample: String? = null,
    val memo: String? = null,
    val msgSvrId: Long = 0L
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(content.isNotBlank()) { "content cannot be blank" }
        require(msgSvrId >= 0L) { "msgSvrId cannot be negative" }
    }
}

@Serializable
internal data class ScrmJoinChatRoomByQrRequest(
    val deviceUuid: String,
    val weChatId: String,
    val qrUrl: String? = null,
    val qrContent: String? = null
) {
    init {
        require(!qrUrl.isNullOrBlank() || !qrContent.isNullOrBlank()) {
            "qrUrl or qrContent must be provided"
        }
    }
}

@Serializable
internal data class ScrmChatRoomManagersRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val memberWxids: List<String>
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(memberWxids.isNotEmpty() && memberWxids.size <= 3) {
            "memberWxids size must be 1..3"
        }
        require(memberWxids.all { it.isNotBlank() }) { "memberWxids cannot contain blank values" }
    }
}

/**
 * 筛选拉人/踢人共用请求。邀请时使用 skipExistingMembers；踢人时使用 onlyExistingMembers。
 * UI 必须先展示最终目标成员预览，首次人工测试 maxCount=1。
 */
@Serializable
internal data class ScrmChatRoomMembersByFilterRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val search: String? = null,
    val filterLabelIds: List<Int> = emptyList(),
    val filterLabelNames: List<String> = emptyList(),
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null,
    val skipExistingMembers: Boolean? = null,
    val onlyExistingMembers: Boolean? = null,
    val maxCount: Int = 1
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
        require(filterLabelIds.all { it > 0 }) { "filterLabelIds must contain positive values" }
    }
}

@Serializable
internal data class ScrmChatRoomSwitchRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val enabled: Boolean
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
    }
}

@Serializable
internal data class ScrmChatRoomTextRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val text: String
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
    }
}

/** 转让群主不可撤销；UI 必须显示新群主身份并要求二次确认。 */
@Serializable
internal data class ScrmTransferChatRoomOwnerRequest(
    val deviceUuid: String,
    val weChatId: String,
    val chatRoomId: String,
    val memberWxid: String
) {
    init {
        require(chatRoomId.isNotBlank()) { "chatRoomId cannot be blank" }
        require(memberWxid.isNotBlank()) { "memberWxid cannot be blank" }
    }
}
