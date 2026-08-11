package com.paifa.ubikitouch.accessibility.floatingchat.moments

internal data class MomentMaterialCreatePreviewDraft(
    val name: String = "",
    val category: String = "",
    val content: String = ""
) {
    fun toPreview(): MomentMaterialCreatePreview = MomentMaterialCreatePreview(
        name = name.trim().ifEmpty { "未命名素材" },
        category = category.trim().ifEmpty { "未分类" },
        content = content.trim(),
        canGeneratePreview = content.isNotBlank()
    )
}

internal data class MomentMaterialCreatePreview(
    val name: String,
    val category: String,
    val content: String,
    val canGeneratePreview: Boolean
)
