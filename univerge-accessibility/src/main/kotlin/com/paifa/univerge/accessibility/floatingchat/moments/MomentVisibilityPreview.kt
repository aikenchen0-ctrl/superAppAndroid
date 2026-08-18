package com.paifa.univerge.accessibility.floatingchat.moments

internal enum class MomentVisibilityScope {
    AllFriends,
    ExcludeSelectedFriends,
    OnlySelectedFriends
}

internal data class MomentVisibilityPreviewDraft(
    val visibility: MomentVisibilityScope = MomentVisibilityScope.AllFriends,
    val targetSummary: String = "",
    val affectedAccountCount: Int? = null,
    val sticky: Boolean = false
) {
    fun toPreview(): MomentVisibilityPreview {
        val normalizedCount = affectedAccountCount?.takeIf { it >= 0 }
        return MomentVisibilityPreview(
            visibilityLabel = visibility.label,
            targetSummary = targetSummary.trim().ifEmpty { "当前微信账号" },
            affectedSummary = normalizedCount?.let { "影响账号数：$it" } ?: "影响账号数：待接口计算",
            stickyLabel = if (sticky) "置顶" else "不置顶",
            requiresConfirmation = true,
            executionStatement = "待确认，未执行"
        )
    }
}

internal data class MomentVisibilityPreview(
    val visibilityLabel: String,
    val targetSummary: String,
    val affectedSummary: String,
    val stickyLabel: String,
    val requiresConfirmation: Boolean,
    val executionStatement: String
)

internal val MomentVisibilityScope.label: String
    get() = when (this) {
        MomentVisibilityScope.AllFriends -> "所有朋友可见"
        MomentVisibilityScope.ExcludeSelectedFriends -> "不给指定朋友看"
        MomentVisibilityScope.OnlySelectedFriends -> "仅指定朋友可见"
    }
