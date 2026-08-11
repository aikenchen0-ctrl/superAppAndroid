package com.paifa.ubikitouch.accessibility.floatingchat.moments

internal enum class MomentMaterialOperation { Copy, Archive }

internal data class MomentMaterialOperationPreviewDraft(
    val action: MomentMaterialOperation,
    val materialId: String,
    val materialName: String,
    val confirmationText: String = ""
) {
    fun toPreview(): MomentMaterialOperationPreview {
        val archiveConfirmed = action != MomentMaterialOperation.Archive || confirmationText.trim() == "归档素材"
        return MomentMaterialOperationPreview(
            actionLabel = if (action == MomentMaterialOperation.Copy) "复制素材" else "归档素材",
            materialSummary = "${materialName.ifBlank { "未命名素材" }} (#$materialId)",
            canGeneratePreview = materialId.isNotBlank() && archiveConfirmed,
            confirmationHint = if (archiveConfirmed) "范围待确认" else "请输入“归档素材”确认"
        )
    }
}

internal data class MomentMaterialOperationPreview(
    val actionLabel: String,
    val materialSummary: String,
    val canGeneratePreview: Boolean,
    val confirmationHint: String
)
