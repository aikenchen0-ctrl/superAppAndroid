package com.paifa.univerge.accessibility.floatingchat.moments

internal data class MomentMaterialPublishPreviewDraft(
    val materialId: String = "",
    val contentSummary: String = "",
    val targetSummary: String = "",
    val scheduledAt: String = ""
) {
    fun toPreview(): MomentMaterialPublishPreview {
        val normalizedMaterialId = materialId.trim()
        return MomentMaterialPublishPreview(
            materialSummary = normalizedMaterialId.ifEmpty { "未选择" }.let { "素材 ID：$it" },
            contentSummary = contentSummary.trim().ifEmpty { "使用素材原始文案" },
            targetSummary = targetSummary.trim().ifEmpty { "当前微信账号" },
            scheduledAt = scheduledAt.trim().ifEmpty { "立即发布（仅预览）" },
            canGeneratePreview = normalizedMaterialId.isNotEmpty(),
            validationMessage = if (normalizedMaterialId.isEmpty()) "请先填写素材 ID" else "范围待确认",
            executionStatement = "仅生成素材发布预览，未复制、未发布"
        )
    }
}

internal data class MomentMaterialPublishPreview(
    val materialSummary: String,
    val contentSummary: String,
    val targetSummary: String,
    val scheduledAt: String,
    val canGeneratePreview: Boolean,
    val validationMessage: String,
    val executionStatement: String
)
