package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context

data class ScrmSettingsSummary(
    val isConfigured: Boolean,
    val baseUrl: String,
    val maskedApiKey: String?,
    val selectedDeviceUuid: String? = null,
    val selectedWeChatId: String? = null
) {
    override fun toString(): String {
        return "ScrmSettingsSummary(isConfigured=$isConfigured, baseUrl=$baseUrl, " +
            "maskedApiKey=$maskedApiKey, selectedDeviceUuid=${selectedDeviceUuid.redacted()}, " +
            "selectedWeChatId=${selectedWeChatId.redacted()})"
    }
}

data class ScrmAccountOption(
    val weChatId: String,
    val nickname: String,
    val deviceUuid: String?,
    val isDeviceOnline: Boolean,
    val accountStatus: Int?
) {
    override fun toString(): String {
        return "ScrmAccountOption(nickname=$nickname, deviceUuid=${deviceUuid.redacted()}, " +
            "weChatId=${weChatId.redacted()}, isDeviceOnline=$isDeviceOnline, " +
            "accountStatus=$accountStatus)"
    }
}

data class ScrmCapabilityGroupOption(
    val groupName: String,
    val totalCount: Int,
    val readyCount: Int,
    val blockedCount: Int,
    val pausedCount: Int,
    val unknownRuntimeCount: Int
)

sealed interface ScrmConnectionTestResult {
    data class Success(
        val userName: String?,
        val deviceCount: Int,
        val onlineDeviceCount: Int,
        val accounts: List<ScrmAccountOption>,
        val selectedDeviceUuid: String?,
        val selectedWeChatId: String?,
        val readyCapabilityCount: Int?,
        val blockedCapabilityCount: Int?,
        val unknownRuntimeCapabilityCount: Int?,
        val pausedCapabilityCount: Int?,
        val capabilityGroups: List<ScrmCapabilityGroupOption>,
        val warnings: List<String>
    ) : ScrmConnectionTestResult {
        override fun toString(): String {
            return "Success(deviceCount=$deviceCount, onlineDeviceCount=$onlineDeviceCount, " +
                "accountCount=${accounts.size}, selectedDeviceUuid=${selectedDeviceUuid.redacted()}, " +
                "selectedWeChatId=${selectedWeChatId.redacted()}, " +
                "readyCapabilityCount=$readyCapabilityCount, " +
                "blockedCapabilityCount=$blockedCapabilityCount, " +
                "unknownRuntimeCapabilityCount=$unknownRuntimeCapabilityCount, " +
                "pausedCapabilityCount=$pausedCapabilityCount, " +
                "capabilityGroupCount=${capabilityGroups.size}, " +
                "warningCount=${warnings.size})"
        }
    }

    data class Failure(val message: String) : ScrmConnectionTestResult
}

class ScrmSettingsManager(context: Context) {
    private val service = ScrmSettingsService(
        credentials = createAndroidScrmCredentialsStore(context),
        clientFactory = { config -> ScrmApiClient(config) }
    )

    fun loadSummary(): ScrmSettingsSummary = service.loadSummary()

    fun save(baseUrl: String, apiKeyInput: String): ScrmSettingsSummary {
        return service.save(baseUrl, apiKeyInput)
    }

    fun clear(): ScrmSettingsSummary = service.clear()

    fun selectAccount(deviceUuid: String, weChatId: String): ScrmSettingsSummary {
        return service.selectAccount(deviceUuid, weChatId)
    }

    fun testConnection(baseUrl: String, apiKeyInput: String): ScrmConnectionTestResult {
        return service.testConnection(baseUrl, apiKeyInput)
    }
}

internal class ScrmSettingsService(
    private val credentials: ScrmCredentialRepository,
    private val clientFactory: (ScrmApiConfig) -> ScrmReadApi
) {
    fun loadSummary(): ScrmSettingsSummary {
        return credentials.load()?.toSummary() ?: ScrmSettingsSummary(
            isConfigured = false,
            baseUrl = "",
            maskedApiKey = null
        )
    }

    fun save(baseUrl: String, apiKeyInput: String): ScrmSettingsSummary {
        val existing = credentials.load()
        val keyValue = apiKeyInput.trim().ifEmpty {
            existing?.apiKey?.headerValue()
                ?: throw ScrmConfigurationException("请输入 SCRM API Key")
        }
        return credentials.save(
            baseUrl = baseUrl,
            apiKeyInput = keyValue,
            selectedDeviceUuid = existing?.selectedDeviceUuid,
            selectedWeChatId = existing?.selectedWeChatId
        ).toSummary()
    }

    fun clear(): ScrmSettingsSummary {
        credentials.clear()
        return ScrmSettingsSummary(isConfigured = false, baseUrl = "", maskedApiKey = null)
    }

    fun selectAccount(deviceUuid: String, weChatId: String): ScrmSettingsSummary {
        require(deviceUuid.isNotBlank()) { "deviceUuid 不能为空" }
        require(weChatId.isNotBlank()) { "weChatId 不能为空" }
        val existing = credentials.load()
            ?: throw ScrmConfigurationException("请先保存 SCRM 配置")
        return credentials.save(
            baseUrl = existing.baseUrl,
            apiKeyInput = existing.apiKey.headerValue(),
            selectedDeviceUuid = deviceUuid,
            selectedWeChatId = weChatId
        ).toSummary()
    }

    fun testConnection(baseUrl: String, apiKeyInput: String): ScrmConnectionTestResult {
        return try {
            val resolved = resolveConfig(baseUrl, apiKeyInput)
            val api = clientFactory(resolved.config)
            val me = api.getMe()
            val devices = api.getDevices()
            val accounts = api.getWechatAccounts()
            val quickStart = api.getQuickStart(
                deviceUuid = resolved.selectedDeviceUuid,
                weChatId = resolved.selectedWeChatId
            )
            val selectedDeviceUuid = quickStart.selectedDeviceUuid?.takeIf { it.isNotBlank() }
            val selectedWeChatId = quickStart.selectedWeChatId?.takeIf { it.isNotBlank() }
            val capabilities = if (selectedDeviceUuid != null && selectedWeChatId != null) {
                api.getCapabilities(selectedDeviceUuid, selectedWeChatId)
            } else {
                null
            }
            ScrmConnectionTestResult.Success(
                userName = me.userName,
                deviceCount = devices.size,
                onlineDeviceCount = devices.count { it.isOnline },
                accounts = accounts.mapNotNull { account ->
                    val weChatId = account.wxid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ScrmAccountOption(
                        weChatId = weChatId,
                        nickname = account.nickname.orEmpty().ifBlank { "未命名账号" },
                        deviceUuid = account.clientUuid,
                        isDeviceOnline = devices.any {
                            it.uuid == account.clientUuid && it.isOnline
                        },
                        accountStatus = account.accountStatus
                    )
                },
                selectedDeviceUuid = selectedDeviceUuid,
                selectedWeChatId = selectedWeChatId,
                readyCapabilityCount = capabilities?.readyCount,
                blockedCapabilityCount = capabilities?.blockedCount,
                unknownRuntimeCapabilityCount = capabilities?.unknownRuntimeCount,
                pausedCapabilityCount = capabilities?.pausedCount,
                capabilityGroups = capabilities?.groups.orEmpty().map { group ->
                    ScrmCapabilityGroupOption(
                        groupName = group.groupName.orEmpty().ifBlank {
                            group.group.orEmpty().ifBlank { "未分组" }
                        },
                        totalCount = group.totalCount,
                        readyCount = group.readyCount,
                        blockedCount = group.blockedCount,
                        pausedCount = group.pausedCount,
                        unknownRuntimeCount = group.unknownRuntimeCount
                    )
                },
                warnings = quickStart.warnings.orEmpty() + capabilities?.warnings.orEmpty()
            )
        } catch (error: ScrmException) {
            ScrmConnectionTestResult.Failure(error.toUserMessage())
        } catch (error: IllegalArgumentException) {
            ScrmConnectionTestResult.Failure(error.message ?: "SCRM 配置无效")
        }
    }

    private fun resolveConfig(baseUrl: String, apiKeyInput: String): ResolvedScrmConfig {
        val existing = credentials.load()
        val effectiveBaseUrl = baseUrl.trim().ifEmpty {
            existing?.baseUrl ?: throw ScrmConfigurationException("请输入 SCRM 服务地址")
        }
        val enteredApiKey = apiKeyInput.trim().takeIf { it.isNotEmpty() }
        val apiKey = enteredApiKey?.let(ScrmApiKey::from)
            ?: existing?.apiKey
            ?: throw ScrmConfigurationException("请输入 SCRM API Key")
        val config = ScrmApiConfig(effectiveBaseUrl, apiKey)
        val canReuseSelection = enteredApiKey == null && existing?.baseUrl == config.baseUrl
        return ResolvedScrmConfig(
            config = config,
            selectedDeviceUuid = existing?.selectedDeviceUuid.takeIf { canReuseSelection },
            selectedWeChatId = existing?.selectedWeChatId.takeIf { canReuseSelection }
        )
    }

    private fun ScrmStoredCredentials.toSummary(): ScrmSettingsSummary {
        return ScrmSettingsSummary(
            isConfigured = true,
            baseUrl = baseUrl,
            maskedApiKey = apiKey.masked,
            selectedDeviceUuid = selectedDeviceUuid,
            selectedWeChatId = selectedWeChatId
        )
    }

}

private data class ResolvedScrmConfig(
    val config: ScrmApiConfig,
    val selectedDeviceUuid: String?,
    val selectedWeChatId: String?
)

private fun String?.redacted(): String = if (this == null) "null" else "****"
