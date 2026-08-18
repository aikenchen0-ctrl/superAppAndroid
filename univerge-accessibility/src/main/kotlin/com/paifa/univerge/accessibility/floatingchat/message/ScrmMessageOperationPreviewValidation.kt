package com.paifa.univerge.accessibility.floatingchat.message

internal data class ScrmMessageOperationPreviewValidation(
    val error: String? = null,
    val summary: String = ""
) {
    val isValid: Boolean
        get() = error == null
}

/** UI 预览阶段的参数校验；只校验输入，不触发任何 SCRM 请求。 */
internal fun validateScrmMessageOperationPreview(
    operation: String,
    messageId: Long?,
    deviceUuid: String?,
    weChatId: String?,
    targetConversationId: String,
    mediaId: Int?
): ScrmMessageOperationPreviewValidation {
    if (messageId == null || messageId <= 0L) {
        return ScrmMessageOperationPreviewValidation(error = "缺少有效远端消息 ID")
    }
    if (deviceUuid.isNullOrBlank() || weChatId.isNullOrBlank()) {
        return ScrmMessageOperationPreviewValidation(error = "缺少当前账号 SCRM 路由")
    }
    if (operation == "Forward" && targetConversationId.isBlank()) {
        return ScrmMessageOperationPreviewValidation(error = "请先填写目标会话 ID")
    }
    if (operation == "DownloadMedia" && (mediaId == null || mediaId <= 0)) {
        return ScrmMessageOperationPreviewValidation(error = "请先填写媒体资源 ID")
    }
    return ScrmMessageOperationPreviewValidation(summary = "参数已校验，可组装请求；不会发送")
}
