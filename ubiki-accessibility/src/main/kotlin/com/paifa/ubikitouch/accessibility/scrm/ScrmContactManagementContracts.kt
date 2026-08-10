package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.Serializable

/**
 * 悬浮聊天联系人管理扩展契约。
 *
 * 这些方法当前只提供给后续 UI 对接，不会由悬浮聊天自动调用。POST/PUT/DELETE 都会修改
 * 服务端或微信端状态，UI 必须展示目标账号、目标联系人、影响范围和二次确认。
 */
internal interface ScrmContactManagementApi {
    fun saveContactLabel(request: ScrmSaveContactLabelRequest): ScrmTaskSubmissionResult
    fun syncContactLabels(request: ScrmAccountMutationRequest): ScrmTaskSubmissionResult
    fun deleteContactLabel(request: ScrmDeleteContactLabelRequest): ScrmTaskSubmissionResult
    fun setContactLabelsByFilter(
        request: ScrmBatchSetContactLabelsByFilterRequest
    ): ScrmBatchSetContactLabelsResponse

    fun getContactManagementPage(
        query: ScrmContactManagementQuery = ScrmContactManagementQuery()
    ): ScrmContactManagementPage

    fun saveCustomerProfileDraft(
        request: ScrmSaveCustomerProfileDraftRequest
    ): ScrmCustomerProfileDraft

    fun saveCustomerProfile(
        contactId: Int,
        request: ScrmSaveCustomerProfileRequest
    ): ScrmCustomerProfile

    fun deleteContactFriend(request: ScrmDeleteContactFriendRequest): ScrmTaskSubmissionResult
    fun setFriendPermission(request: ScrmSetFriendPermissionRequest): ScrmTaskSubmissionResult
    fun setFriendPermissionsBatch(
        request: ScrmBatchSetFriendPermissionRequest
    ): ScrmBatchSetFriendPermissionResponse

    fun setFriendPermissionsByFilter(
        request: ScrmBatchSetFriendPermissionByFilterRequest
    ): ScrmBatchSetFriendPermissionResponse

    fun modifyFriendProfile(request: ScrmModifyFriendProfileRequest): ScrmTaskSubmissionResult
    fun refreshFriendInfo(request: ScrmRefreshFriendInfoRequest): ScrmTaskSubmissionResult
}

@Serializable
internal data class ScrmAccountMutationRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null
) {
    init {
        require(deviceUuid == null || deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

/** 待 UI 对接：创建/重命名标签及调整成员均会修改微信标签。 */
@Serializable
internal data class ScrmSaveContactLabelRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val labelName: String,
    val labelId: Int = 0,
    val addList: String? = null,
    val delList: String? = null
) {
    init {
        require(labelName.isNotBlank()) { "labelName cannot be blank" }
        require(labelId >= 0) { "labelId cannot be negative" }
        require(addList == null || addList.isNotBlank()) { "addList cannot be blank" }
        require(delList == null || delList.isNotBlank()) { "delList cannot be blank" }
    }
}

/** 待 UI 对接：删除标签不可撤销，UI 必须显示标签名称并要求二次确认。 */
@Serializable
internal data class ScrmDeleteContactLabelRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val labelId: Int
) {
    init {
        require(labelId > 0) { "labelId must be greater than 0" }
    }
}

/**
 * 待 UI 对接：按筛选批量打标签。首次人工测试必须 maxCount=1、mergeExisting=true，
 * 并先展示筛选命中数量；禁止用替换模式或空标签集合测试生产联系人。
 */
@Serializable
internal data class ScrmBatchSetContactLabelsByFilterRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val labelIds: List<Int> = emptyList(),
    val labelNames: List<String> = emptyList(),
    val search: String? = null,
    val filterLabelIds: List<Int> = emptyList(),
    val filterLabelNames: List<String> = emptyList(),
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null,
    val mergeExisting: Boolean = true,
    val maxCount: Int = 200
) {
    init {
        require(labelIds.all { it > 0 }) { "labelIds must contain positive values" }
        require(labelNames.all { it.isNotBlank() }) { "labelNames cannot contain blank values" }
        require(filterLabelIds.all { it > 0 }) { "filterLabelIds must contain positive values" }
        require(filterLabelNames.all { it.isNotBlank() }) {
            "filterLabelNames cannot contain blank values"
        }
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
    }
}

internal data class ScrmContactManagementQuery(
    val offset: Int = 0,
    val limit: Int = 100,
    val search: String? = null,
    val weChatId: String? = null,
    val blockedOnly: Boolean = false
) {
    init {
        require(offset >= 0) { "offset cannot be negative" }
        require(limit in 1..200) { "limit must be between 1 and 200" }
        require(search == null || search.isNotBlank()) { "search cannot be blank" }
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
    }
}

@Serializable
internal data class ScrmContactManagementPage(
    val items: List<ScrmContactManagementItem> = emptyList(),
    val totalCount: Int = 0,
    val offset: Int = 0,
    val limit: Int = 100
)

@Serializable
internal data class ScrmContactManagementItem(
    val id: Int = 0,
    val ownerWxid: String? = null,
    val wxid: String? = null,
    val friendNo: String? = null,
    val nickname: String? = null,
    val remarks: String? = null,
    val avatar: String? = null,
    val lastInteractionTime: String? = null,
    val updatedAt: String? = null,
    val accountNickname: String? = null,
    val accountMobilePhone: String? = null,
    val deviceUuid: String? = null,
    val deviceOnline: Boolean = false
)

/** 只写 SCRM 草稿，不触发 Android；待“加好友前置画像”UI 对接。 */
@Serializable
internal data class ScrmSaveCustomerProfileDraftRequest(
    val weChatId: String,
    val targetKey: String,
    val normalizedTargetKey: String? = null,
    val targetKind: String? = null,
    val addMode: String? = null,
    val taskId: Long = 0L,
    val verifyMessageSummary: String? = null,
    val remark: String? = null,
    val labelText: String? = null,
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val sourceDetail: String? = null,
    val profileKey: String? = null,
    val purchaseHistory: String? = null,
    val socialAccounts: String? = null,
    val faceImageUrl: String? = null,
    val notes: String? = null,
    val mappedLabelIds: List<Int> = emptyList(),
    val mappedLabelNames: List<String> = emptyList()
) {
    init {
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(targetKey.isNotBlank()) { "targetKey cannot be blank" }
        require(taskId >= 0L) { "taskId cannot be negative" }
        require(mappedLabelIds.all { it > 0 }) { "mappedLabelIds must contain positive values" }
    }
}

@Serializable
internal data class ScrmCustomerProfileDraft(
    val id: Int = 0,
    val ownerWxid: String? = null,
    val targetKey: String? = null,
    val normalizedTargetKey: String? = null,
    val targetKind: String? = null,
    val addMode: String? = null,
    val taskId: Long = 0L,
    val verifyMessageSummary: String? = null,
    val remark: String? = null,
    val labelText: String? = null,
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val sourceDetail: String? = null,
    val profileKey: String? = null,
    val purchaseHistory: String? = null,
    val socialAccounts: String? = null,
    val faceImageUrl: String? = null,
    val notes: String? = null,
    val mappedLabelIds: List<Int> = emptyList(),
    val status: String? = null,
    val appliedContactId: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 只写 SCRM 自有画像，不修改微信资料；待联系人画像编辑 UI 对接。 */
@Serializable
internal data class ScrmSaveCustomerProfileRequest(
    val weChatId: String? = null,
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val sourceDetail: String? = null,
    val profileKey: String? = null,
    val purchaseHistory: String? = null,
    val socialAccounts: String? = null,
    val faceImageUrl: String? = null,
    val notes: String? = null,
    val mappedLabelIds: List<Int> = emptyList(),
    val mappedLabelNames: List<String> = emptyList()
) {
    init {
        require(weChatId == null || weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(mappedLabelIds.all { it > 0 }) { "mappedLabelIds must contain positive values" }
        require(mappedLabelNames.all { it.isNotBlank() }) {
            "mappedLabelNames cannot contain blank values"
        }
    }
}

/** 待 UI 对接：删除好友不可撤销，必须显示 contactId/好友名称并二次确认。 */
internal data class ScrmDeleteContactFriendRequest(
    val contactId: Int,
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val friendId: String? = null
) {
    init {
        require(contactId > 0) { "contactId must be greater than 0" }
    }
}

/** 待 UI 对接：朋友圈权限会修改微信好友真实关系，禁止后台自动应用。 */
@Serializable
internal data class ScrmSetFriendPermissionRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val contactId: Int? = null,
    val friendId: String? = null,
    val permissionMask: Int? = null,
    val onlyChat: Boolean? = null,
    val notSeeFriendMoments: Boolean? = null,
    val notLetFriendSeeMyMoments: Boolean? = null
) {
    init {
        require((contactId ?: 0) > 0 || !friendId.isNullOrBlank()) {
            "contactId or friendId must be provided"
        }
        require(permissionMask == null || permissionMask in setOf(0, 1, 2, 3, 8)) {
            "permissionMask must be one of 0, 1, 2, 3 or 8"
        }
    }
}

@Serializable
internal data class ScrmBatchSetFriendPermissionRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val friendIds: List<String> = emptyList(),
    val permissionMask: Int? = null,
    val onlyChat: Boolean? = null,
    val notSeeFriendMoments: Boolean? = null,
    val notLetFriendSeeMyMoments: Boolean? = null,
    val maxCount: Int = 200
) {
    init {
        require(friendIds.all { it.isNotBlank() }) { "friendIds cannot contain blank values" }
        validateFriendPermissionMask(permissionMask)
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
    }
}

@Serializable
internal data class ScrmBatchSetFriendPermissionByFilterRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val search: String? = null,
    val filterLabelIds: List<Int> = emptyList(),
    val filterLabelNames: List<String> = emptyList(),
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val profileKey: String? = null,
    val profileOnly: Boolean? = null,
    val permissionMask: Int? = null,
    val onlyChat: Boolean? = null,
    val notSeeFriendMoments: Boolean? = null,
    val notLetFriendSeeMyMoments: Boolean? = null,
    val maxCount: Int = 200
) {
    init {
        require(filterLabelIds.all { it > 0 }) { "filterLabelIds must contain positive values" }
        require(filterLabelNames.all { it.isNotBlank() }) {
            "filterLabelNames cannot contain blank values"
        }
        validateFriendPermissionMask(permissionMask)
        require(maxCount in 1..200) { "maxCount must be between 1 and 200" }
    }
}

@Serializable
internal data class ScrmBatchSetFriendPermissionResponse(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val requestedCount: Int = 0,
    val acceptedCount: Int = 0,
    val successCount: Int = 0,
    val unknownCount: Int = 0,
    val failCount: Int = 0,
    val permissionMask: Int = 0,
    val items: List<ScrmBatchTaskItemResult> = emptyList()
)

/** 待 UI 对接：备注、描述和电话是微信真实资料变更，清空操作必须显式展示。 */
@Serializable
internal data class ScrmModifyFriendProfileRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val contactId: Int? = null,
    val friendId: String? = null,
    val memo: String? = null,
    val desc: String? = null,
    val phone: String? = null,
    val delFlag: Int = 0,
    val clearMemo: Boolean = false,
    val clearDesc: Boolean = false,
    val clearPhone: Boolean = false
) {
    init {
        require((contactId ?: 0) > 0 || !friendId.isNullOrBlank()) {
            "contactId or friendId must be provided"
        }
        require(delFlag in 0..7) { "delFlag must be between 0 and 7" }
    }
}

/** 只刷新资料快照，不修改资料；待联系人详情页的显式刷新入口对接。 */
@Serializable
internal data class ScrmRefreshFriendInfoRequest(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val contactId: Int? = null,
    val friendId: String? = null,
    val chatroom: String? = null,
    val ticket: String? = null
) {
    init {
        require((contactId ?: 0) > 0 || !friendId.isNullOrBlank()) {
            "contactId or friendId must be provided"
        }
    }
}

private fun validateFriendPermissionMask(permissionMask: Int?) {
    require(permissionMask == null || permissionMask in setOf(0, 1, 2, 3, 8)) {
        "permissionMask must be one of 0, 1, 2, 3 or 8"
    }
}
