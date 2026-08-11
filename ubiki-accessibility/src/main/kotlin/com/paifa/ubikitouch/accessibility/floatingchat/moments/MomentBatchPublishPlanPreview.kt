package com.paifa.ubikitouch.accessibility.floatingchat.moments

internal enum class MomentBatchPlanStatus {
    Draft,
    PendingConfirmation,
    Paused
}

internal data class MomentBatchPublishPlanDraft(
    val planName: String = "",
    val contentSummary: String = "",
    val targetSummary: String = "",
    val scheduledAt: String = "",
    val status: MomentBatchPlanStatus = MomentBatchPlanStatus.Draft,
    val estimatedImpactCount: Int? = null
) {
    fun toPreview(): MomentBatchPublishPlanPreview {
        val normalizedCount = estimatedImpactCount?.takeIf { it >= 0 }
        return MomentBatchPublishPlanPreview(
            planName = planName.trim().ifEmpty { "朋友圈批量发布草稿" },
            contentSummary = contentSummary.trim().ifEmpty { "未选择素材或文案" },
            targetSummary = targetSummary.trim().ifEmpty { "当前微信账号" },
            scheduledAt = scheduledAt.trim().ifEmpty { "待选择，未排期" },
            statusLabel = status.label,
            estimatedImpact = normalizedCount?.let { "预计影响 $it 个账号" } ?: "待接口计算",
            executionStatement = "仅生成预览，未创建、未执行"
        )
    }
}

internal data class MomentBatchPublishPlanPreview(
    val planName: String,
    val contentSummary: String,
    val targetSummary: String,
    val scheduledAt: String,
    val statusLabel: String,
    val estimatedImpact: String,
    val executionStatement: String
)

private val MomentBatchPlanStatus.label: String
    get() = when (this) {
        MomentBatchPlanStatus.Draft -> "草稿，待确认"
        MomentBatchPlanStatus.PendingConfirmation -> "待确认，尚未执行"
        MomentBatchPlanStatus.Paused -> "暂停，尚未执行"
    }

internal val MomentBatchPlanStatus.shortLabel: String
    get() = when (this) {
        MomentBatchPlanStatus.Draft -> "草稿"
        MomentBatchPlanStatus.PendingConfirmation -> "待确认"
        MomentBatchPlanStatus.Paused -> "暂停"
    }
