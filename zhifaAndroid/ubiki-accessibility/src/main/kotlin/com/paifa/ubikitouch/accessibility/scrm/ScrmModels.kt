package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ScrmSendTextMessageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val conversationId: String,
    val content: String,
    val atIds: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        require(conversationId.isNotBlank()) { "conversationId 不能为空" }
        require(content.isNotBlank()) { "消息内容不能为空" }
        require(atIds == null || atIds.isNotBlank()) { "atIds 不能为空" }
    }
}

@Serializable
internal data class ScrmTaskSubmissionResult(
    val taskId: Long,
    val success: Boolean,
    val message: String? = null,
    val data: JsonElement? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null
) {
    init {
        require(taskId > 0) { "taskId 必须大于 0" }
    }

    override fun toString(): String {
        return "ScrmTaskSubmissionResult(taskId=$taskId, success=$success, " +
            "hasMessage=${message != null}, hasData=${data != null})"
    }
}

@Serializable
internal data class ScrmMe(
    val userId: String? = null,
    val userName: String? = null,
    val email: String? = null,
    val authType: String? = null,
    val openApiKeyId: Long? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null
)

@Serializable
internal data class ScrmDevice(
    val uuid: String? = null,
    val ownerId: String? = null,
    val isOnline: Boolean,
    val status: Int,
    val weChatId: String? = null,
    val phoneBrand: String? = null,
    val phoneModel: String? = null,
    val androidApi: Int,
    val appPackageName: String? = null,
    val appVersion: String? = null,
    val appVersionCode: Int,
    val lastLoginAt: String? = null,
    val updatedAt: String
)

@Serializable
internal data class ScrmWechatAccount(
    val wxid: String? = null,
    val nickname: String? = null,
    val clientUuid: String? = null,
    val ownerId: String? = null,
    val accountStatus: Int? = null,
    val lastOnlineAt: String? = null
)

@Serializable
internal data class ScrmQuickStart(
    val success: Boolean,
    val apiVersion: String? = null,
    val serverTime: String,
    val landingStatus: String? = null,
    val landingStatusText: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val authType: String? = null,
    val openApiKeyId: Long? = null,
    val roles: List<String>? = null,
    val permissions: List<String>? = null,
    val deviceCount: Int,
    val weChatAccountCount: Int,
    val devicePreviewLimit: Int,
    val weChatAccountPreviewLimit: Int,
    val devices: List<ScrmDevice>? = null,
    val weChatAccounts: List<ScrmWechatAccount>? = null,
    val selectedDeviceUuid: String? = null,
    val selectedWeChatId: String? = null,
    val capabilitiesUrl: String? = null,
    val testPlanUrl: String? = null,
    val taskResultUrlTemplate: String? = null,
    val recentTaskResultsUrl: String? = null,
    val voiceEnvironmentUrl: String? = null,
    val cardTemplatesUrl: String? = null,
    val swaggerDocsUrl: String? = null,
    val openApiJsonUrl: String? = null,
    val openApiGuideUrl: String? = null,
    val openApiKeysUrl: String? = null,
    val openApiDocsHint: String? = null,
    val nextStep: String? = null,
    val nextCurlExample: String? = null,
    val nextSettingsPresetUrl: String? = null,
    val nextBlockers: List<String>? = null,
    val nextRecommendedActions: List<String>? = null,
    val recommendedChecks: List<String>? = null,
    val warnings: List<String>? = null
)

@Serializable
internal data class ScrmCapabilityStatus(
    val code: String? = null,
    val name: String? = null,
    val group: String? = null,
    val groupName: String? = null,
    val status: String? = null,
    val settingKey: String? = null,
    val runtimeEffectiveKey: String? = null,
    val requiredPermission: String? = null,
    val httpMethod: String? = null,
    val route: String? = null,
    val settingsPresetUrl: String? = null,
    val minimalTestHint: String? = null,
    val serverConfigured: Boolean,
    val permissionAllowed: Boolean,
    val assetAllowed: Boolean,
    val runtimeEffective: Boolean? = null,
    val serverWouldAllow: Boolean,
    val readyForTest: Boolean,
    val requiresRuntimeSnapshot: Boolean,
    val requiresSettingsPush: Boolean,
    val requiresAndroidEffective: Boolean,
    val blockers: List<String>? = null,
    val recommendedActions: List<String>? = null,
    val nextStep: String? = null
)

@Serializable
internal data class ScrmCapabilityGroup(
    val group: String? = null,
    val groupName: String? = null,
    val totalCount: Int,
    val readyCount: Int,
    val pausedCount: Int,
    val blockedCount: Int,
    val unknownRuntimeCount: Int
)

@Serializable
internal data class ScrmCapabilities(
    val deviceUuid: String? = null,
    val weChatId: String? = null,
    val deviceAccessible: Boolean,
    val accountAccessible: Boolean,
    val hasRuntimeSnapshot: Boolean,
    val runtimeSnapshotSource: String? = null,
    val runtimeSnapshotReceivedAt: String? = null,
    val capabilities: List<ScrmCapabilityStatus>? = null,
    val totalCount: Int,
    val readyCount: Int,
    val blockedCount: Int,
    val unknownRuntimeCount: Int,
    val pausedCount: Int,
    val groups: List<ScrmCapabilityGroup>? = null,
    val recommendedChecks: List<String>? = null,
    val warnings: List<String>? = null
)

@Serializable
internal data class ScrmTaskResult(
    val taskId: Long,
    val success: Boolean,
    val status: String? = null,
    val resultUnknown: Boolean,
    val resultCode: String? = null,
    val message: String? = null,
    val deviceUuid: String? = null,
    val connectionIdHash: String? = null,
    val receivedAt: String,
    val rawHidden: Boolean,
    val data: JsonElement? = null,
    val taskResultUrl: String? = null,
    val recentTaskResultsUrl: String? = null,
    val nextStep: String? = null
) {
    override fun toString(): String {
        return "ScrmTaskResult(taskId=$taskId, success=$success, status=$status, " +
            "resultUnknown=$resultUnknown, resultCode=$resultCode, rawHidden=$rawHidden, " +
            "hasData=${data != null})"
    }
}

@Serializable
internal data class ScrmRecentTaskResults(
    val deviceUuid: String? = null,
    val count: Int,
    val items: List<ScrmTaskResult>? = null,
    val warnings: List<String>? = null
)
