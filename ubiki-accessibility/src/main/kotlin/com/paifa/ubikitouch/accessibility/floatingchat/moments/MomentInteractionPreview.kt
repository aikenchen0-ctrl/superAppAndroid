package com.paifa.ubikitouch.accessibility.floatingchat.moments

internal enum class MomentInteractionAction {
    FetchUnread,
    MarkAllRead
}

internal data class MomentInteractionPreviewDraft(
    val action: MomentInteractionAction = MomentInteractionAction.FetchUnread,
    val targetSummary: String = "",
    val unreadCount: Int? = null
) {
    fun toPreview(): MomentInteractionPreview {
        val normalizedCount = unreadCount?.takeIf { it >= 0 }
        return MomentInteractionPreview(
            actionLabel = action.label,
            targetSummary = targetSummary.trim().ifEmpty { "当前微信账号" },
            unreadSummary = normalizedCount?.let { "未读数量：$it" } ?: "未读数量：待接口读取",
            impactSummary = action.impactSummary(normalizedCount),
            requiresConfirmation = action == MomentInteractionAction.MarkAllRead,
            executionStatement = if (action == MomentInteractionAction.MarkAllRead) {
                "待确认，未执行"
            } else {
                "仅生成预览，未读取、未修改"
            }
        )
    }
}

internal data class MomentInteractionPreview(
    val actionLabel: String,
    val targetSummary: String,
    val unreadSummary: String,
    val impactSummary: String,
    val requiresConfirmation: Boolean,
    val executionStatement: String
)

internal val MomentInteractionAction.label: String
    get() = when (this) {
        MomentInteractionAction.FetchUnread -> "读取未读状态"
        MomentInteractionAction.MarkAllRead -> "标记全部已读"
    }

private fun MomentInteractionAction.impactSummary(unreadCount: Int?): String = when (this) {
    MomentInteractionAction.FetchUnread -> "仅查看互动未读状态"
    MomentInteractionAction.MarkAllRead -> unreadCount?.let { "将 $it 条互动标记为已读" } ?: "将标记目标范围内的未读互动为已读"
}
