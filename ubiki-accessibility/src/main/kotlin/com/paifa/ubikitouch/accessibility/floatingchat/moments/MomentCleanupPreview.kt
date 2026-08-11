package com.paifa.ubikitouch.accessibility.floatingchat.moments

internal enum class MomentCleanupAction {
    DeleteMoment,
    RefreshFriends
}

internal data class MomentCleanupPreviewDraft(
    val action: MomentCleanupAction = MomentCleanupAction.DeleteMoment,
    val momentId: String = "",
    val targetSummary: String = "",
    val affectedCount: Int? = null,
    val confirmationText: String = ""
) {
    fun toPreview(): MomentCleanupPreview {
        val normalizedMomentId = momentId.trim()
        val normalizedCount = affectedCount?.takeIf { it >= 0 }
        val isDelete = action == MomentCleanupAction.DeleteMoment
        val isDeleteConfirmed = normalizedMomentId.isNotEmpty() && confirmationText.trim() == DeleteMomentConfirmationText
        return MomentCleanupPreview(
            actionLabel = action.label,
            targetSummary = targetSummary.trim().ifEmpty { "当前微信账号" },
            targetItemSummary = if (isDelete) {
                normalizedMomentId.ifEmpty { "未填写" }.let { "动态 ID：$it" }
            } else {
                "好友朋友圈：待刷新"
            },
            impactSummary = normalizedCount?.let {
                if (isDelete) "影响动态数：$it" else "预计刷新好友数：$it"
            } ?: if (isDelete) "影响动态数：待接口核对" else "预计刷新好友数：待接口计算",
            confirmationHint = if (isDelete && !isDeleteConfirmed) "请输入“删除动态”确认" else "范围已确认",
            canGeneratePreview = !isDelete || isDeleteConfirmed,
            executionStatement = if (isDelete) "仅生成删除预览，未删除" else "仅生成刷新预览，未刷新"
        )
    }
}

internal data class MomentCleanupPreview(
    val actionLabel: String,
    val targetSummary: String,
    val targetItemSummary: String,
    val impactSummary: String,
    val confirmationHint: String,
    val canGeneratePreview: Boolean,
    val executionStatement: String
)

internal const val DeleteMomentConfirmationText = "删除动态"

internal val MomentCleanupAction.label: String
    get() = when (this) {
        MomentCleanupAction.DeleteMoment -> "删除朋友圈动态"
        MomentCleanupAction.RefreshFriends -> "刷新好友朋友圈"
    }
