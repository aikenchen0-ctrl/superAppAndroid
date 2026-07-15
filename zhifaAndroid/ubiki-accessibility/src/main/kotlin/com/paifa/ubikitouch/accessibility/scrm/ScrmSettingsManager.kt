package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context
import com.paifa.ubikitouch.accessibility.BuildConfig

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

sealed interface ScrmAdminBootstrapResult {
    data class Success(
        val summary: ScrmSettingsSummary,
        val selectedAccount: ScrmAccountOption,
        val deviceCount: Int,
        val onlineDeviceCount: Int
    ) : ScrmAdminBootstrapResult

    data class Failure(
        val message: String,
        val summary: ScrmSettingsSummary? = null
    ) : ScrmAdminBootstrapResult
}

internal data class ScrmAutoBootstrapCredentials(
    val baseUrl: String,
    val username: String,
    val password: String
) {
    init {
        require(baseUrl.isNotBlank()) { "自动配置缺少 SCRM 服务地址" }
        require(username.isNotBlank()) { "自动配置缺少后台账号" }
        require(password.isNotBlank()) { "自动配置缺少后台密码" }
    }

    override fun toString(): String {
        return "ScrmAutoBootstrapCredentials(baseUrl=$baseUrl, username=****, password=****)"
    }
}

class ScrmSettingsManager(context: Context) {
    private val credentials = createAndroidScrmCredentialsStore(context)
    private val service = ScrmSettingsService(
        credentials = credentials,
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

    fun bootstrapWithAdminCredentials(
        baseUrl: String,
        username: String,
        password: String
    ): ScrmAdminBootstrapResult {
        return service.bootstrapWithAdminCredentials(baseUrl, username, password)
    }

    fun bootstrapWithBundledAdminCredentialsIfNeeded(): ScrmAdminBootstrapResult? {
        return try {
            service.bootstrapWithAdminCredentialsIfNeeded(bundledAutoBootstrapCredentials())
        } catch (error: IllegalArgumentException) {
            ScrmAdminBootstrapResult.Failure(error.message ?: "SCRM 自动配置无效")
        }
    }

    fun bootstrapWithBundledAdminCredentials(): ScrmAdminBootstrapResult? {
        return try {
            val autoCredentials = bundledAutoBootstrapCredentials() ?: return null
            service.bootstrapWithAdminCredentials(
                baseUrl = autoCredentials.baseUrl,
                username = autoCredentials.username,
                password = autoCredentials.password
            )
        } catch (error: IllegalArgumentException) {
            ScrmAdminBootstrapResult.Failure(error.message ?: "SCRM 自动配置无效")
        }
    }

    internal fun loadSelectedSession(): ScrmSelectedSession {
        val stored = credentials.load()
            ?: throw ScrmConfigurationException("请先保存 SCRM API 配置")
        val deviceUuid = stored.selectedDeviceUuid?.takeIf { it.isNotBlank() }
            ?: throw ScrmConfigurationException("请先在 SCRM 设置里选择在线设备")
        val weChatId = stored.selectedWeChatId?.takeIf { it.isNotBlank() }
            ?: throw ScrmConfigurationException("请先在 SCRM 设置里选择微信账号")
        val client = ScrmApiClient(ScrmApiConfig(stored.baseUrl, stored.apiKey))
        return ScrmSelectedSession(
            deviceUuid = deviceUuid,
            weChatId = weChatId,
            readApi = client,
            contactApi = client,
            chatRoomApi = client,
            messageApi = client,
            momentApi = client,
            taskApi = client
        )
    }

    internal fun loadSelectedSessionOrBootstrap(): ScrmSelectedSession {
        val existing = runCatching { loadSelectedSession() }
        existing.getOrNull()?.let { return it }
        val bootstrapResult = bootstrapWithBundledAdminCredentialsIfNeeded()
        when (bootstrapResult) {
            is ScrmAdminBootstrapResult.Success -> return loadSelectedSession()
            is ScrmAdminBootstrapResult.Failure -> throw ScrmConfigurationException(bootstrapResult.message)
            null -> throw existing.exceptionOrNull()
                ?: ScrmConfigurationException("请先配置 SCRM API")
        }
    }

    private fun bundledAutoBootstrapCredentials(): ScrmAutoBootstrapCredentials? {
        val baseUrl = BuildConfig.SCRM_AUTO_BASE_URL.trim()
        val username = BuildConfig.SCRM_AUTO_USERNAME.trim()
        val password = BuildConfig.SCRM_AUTO_PASSWORD
        if (baseUrl.isBlank() && username.isBlank() && password.isBlank()) {
            return null
        }
        return ScrmAutoBootstrapCredentials(
            baseUrl = baseUrl,
            username = username,
            password = password
        )
    }
}

internal data class ScrmSelectedSession(
    val deviceUuid: String,
    val weChatId: String,
    val readApi: ScrmReadApi,
    val contactApi: ScrmContactApi,
    val chatRoomApi: ScrmChatRoomApi,
    val messageApi: ScrmMessageApi,
    val momentApi: ScrmMomentApi,
    val taskApi: ScrmTaskApi
) {
    override fun toString(): String {
        return "ScrmSelectedSession(deviceUuid=${deviceUuid.redacted()}, " +
            "weChatId=${weChatId.redacted()})"
    }
}

internal class ScrmSettingsService(
    private val credentials: ScrmCredentialRepository,
    private val adminClientFactory: (String) -> ScrmAdminApi = { root -> ScrmAdminApiClient(root) },
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

    fun bootstrapWithAdminCredentials(
        baseUrl: String,
        username: String,
        password: String
    ): ScrmAdminBootstrapResult {
        return try {
            val normalizedRoot = normalizeScrmServerRoot(baseUrl)
            val adminApi = adminClientFactory(normalizedRoot)
            val session = adminApi.login(username, password)
            val createdKey = adminApi.createOpenApiKey(
                token = session.token,
                request = ScrmCreateOpenApiKeyRequest(
                    name = "只发 Android OpenAPI 联调",
                    remark = "Android 悬浮联系人 API 联调"
                )
            )
            val api = clientFactory(
                ScrmApiConfig(normalizedRoot, ScrmApiKey.from(createdKey.plainKey))
            )
            val devices = api.getDevices()
            val accounts = api.getWechatAccounts()
            val selectedAccount = selectOnlineWechatAccount(
                devices = devices,
                accounts = accounts
            )
            if (selectedAccount == null) {
                val summary = credentials.save(
                    baseUrl = normalizedRoot,
                    apiKeyInput = createdKey.plainKey
                ).toSummary()
                return ScrmAdminBootstrapResult.Failure(
                    message = "已创建 OpenAPI Key，但未发现可用的在线微信账号；请确认 smLern 设备在线后重新自动配置",
                    summary = summary
                )
            }

            val summary = credentials.save(
                baseUrl = normalizedRoot,
                apiKeyInput = createdKey.plainKey,
                selectedDeviceUuid = selectedAccount.deviceUuid,
                selectedWeChatId = selectedAccount.weChatId
            ).toSummary()
            ScrmAdminBootstrapResult.Success(
                summary = summary,
                selectedAccount = selectedAccount,
                deviceCount = devices.size,
                onlineDeviceCount = devices.count { it.isOnline }
            )
        } catch (error: ScrmException) {
            ScrmAdminBootstrapResult.Failure(error.toUserMessage())
        } catch (error: IllegalArgumentException) {
            ScrmAdminBootstrapResult.Failure(error.message ?: "SCRM 配置无效")
        }
    }

    fun bootstrapWithAdminCredentialsIfNeeded(
        autoCredentials: ScrmAutoBootstrapCredentials?
    ): ScrmAdminBootstrapResult? {
        if (autoCredentials == null) return null
        val existing = runCatching { credentials.load() }.getOrNull()
        val hasSelectedSession =
            !existing?.selectedDeviceUuid.isNullOrBlank() &&
                !existing?.selectedWeChatId.isNullOrBlank()
        if (hasSelectedSession) return null
        return bootstrapWithAdminCredentials(
            baseUrl = autoCredentials.baseUrl,
            username = autoCredentials.username,
            password = autoCredentials.password
        )
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

    private fun selectOnlineWechatAccount(
        devices: List<ScrmDevice>,
        accounts: List<ScrmWechatAccount>
    ): ScrmAccountOption? {
        val onlineDeviceIds = devices.asSequence()
            .filter { it.isOnline }
            .mapNotNull { it.uuid?.takeIf { uuid -> uuid.isNotBlank() } }
            .toSet()
        val accountOptions = accounts.mapNotNull { account ->
            val weChatId = account.wxid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val deviceUuid = account.clientUuid?.takeIf { it.isNotBlank() }
            ScrmAccountOption(
                weChatId = weChatId,
                nickname = account.nickname.orEmpty().ifBlank { "未命名账号" },
                deviceUuid = deviceUuid,
                isDeviceOnline = deviceUuid != null && onlineDeviceIds.contains(deviceUuid),
                accountStatus = account.accountStatus
            )
        }
        accountOptions.firstOrNull { it.deviceUuid != null && it.isDeviceOnline }?.let {
            return it
        }

        return devices.firstOrNull { device ->
            device.isOnline &&
                !device.uuid.isNullOrBlank() &&
                !device.weChatId.isNullOrBlank()
        }?.let { device ->
            val weChatId = requireNotNull(device.weChatId)
            ScrmAccountOption(
                weChatId = weChatId,
                nickname = weChatId,
                deviceUuid = requireNotNull(device.uuid),
                isDeviceOnline = true,
                accountStatus = null
            )
        }
    }

}

private data class ResolvedScrmConfig(
    val config: ScrmApiConfig,
    val selectedDeviceUuid: String?,
    val selectedWeChatId: String?
)

private fun String?.redacted(): String = if (this == null) "null" else "****"
