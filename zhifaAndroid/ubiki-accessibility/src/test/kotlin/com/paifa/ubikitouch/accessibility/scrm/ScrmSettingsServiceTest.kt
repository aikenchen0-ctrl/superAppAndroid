package com.paifa.ubikitouch.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmSettingsServiceTest {
    @Test
    fun savingBlankKeyReusesExistingEncryptedCredential() {
        val repository = InMemoryCredentialRepository(
            ScrmStoredCredentials(
                baseUrl = "https://old.example.com/openapi/v1",
                apiKey = ScrmApiKey.from("scrm_existing_1234")
            )
        )
        val service = ScrmSettingsService(repository) { error("not used") }

        val summary = service.save("https://new.example.com", "")

        assertTrue(summary.isConfigured)
        assertEquals("https://new.example.com/openapi/v1", summary.baseUrl)
        assertEquals("****1234", summary.maskedApiKey)
        assertEquals("scrm_existing_1234", repository.savedKeyInput)
    }

    @Test
    fun selectingAccountPersistsRealRoutingContext() {
        val repository = InMemoryCredentialRepository(
            ScrmStoredCredentials(
                baseUrl = "https://api.example.com/openapi/v1",
                apiKey = ScrmApiKey.from("scrm_existing_1234")
            )
        )
        val service = ScrmSettingsService(repository) { error("not used") }

        val summary = service.selectAccount("device-1", "wxid_1")

        assertEquals("device-1", summary.selectedDeviceUuid)
        assertEquals("wxid_1", summary.selectedWeChatId)
        assertEquals("device-1", repository.savedDeviceUuid)
        assertEquals("wxid_1", repository.savedWeChatId)
    }

    @Test
    fun connectionTestDiscoversRouteAndCapabilityWithoutSavingCandidate() {
        val repository = InMemoryCredentialRepository()
        val api = FakeReadApi()
        val service = ScrmSettingsService(repository) { api }

        val result = service.testConnection(
            baseUrl = "https://api.example.com",
            apiKeyInput = "scrm_candidate_5678"
        )

        assertTrue(result is ScrmConnectionTestResult.Success)
        result as ScrmConnectionTestResult.Success
        assertEquals("Tester", result.userName)
        assertEquals(2, result.deviceCount)
        assertEquals(1, result.onlineDeviceCount)
        assertEquals(1, result.accounts.size)
        assertEquals("测试账号", result.accounts.single().nickname)
        assertEquals("device-1", result.selectedDeviceUuid)
        assertEquals("wxid_1", result.selectedWeChatId)
        assertEquals(8, result.readyCapabilityCount)
        assertEquals(2, result.blockedCapabilityCount)
        assertEquals(1, result.pausedCapabilityCount)
        assertEquals("消息", result.capabilityGroups.single().groupName)
        assertEquals(5, result.capabilityGroups.single().readyCount)
        assertEquals("device-1" to "wxid_1", api.capabilityRoute)
        assertFalse(repository.saveCalled)
    }

    @Test
    fun connectionTestCanUseSavedKeyWhenInputIsBlank() {
        val repository = InMemoryCredentialRepository(
            ScrmStoredCredentials(
                baseUrl = "https://api.example.com/openapi/v1",
                apiKey = ScrmApiKey.from("scrm_existing_1234")
            )
        )
        var clientKey: String? = null
        val service = ScrmSettingsService(repository) { config ->
            clientKey = config.apiKey?.headerValue()
            FakeReadApi()
        }

        val result = service.testConnection("https://api.example.com", "")

        assertTrue(result is ScrmConnectionTestResult.Success)
        assertEquals("scrm_existing_1234", clientKey)
    }

    @Test
    fun candidateCredentialsDoNotReuseAccountSelectionFromSavedEnvironment() {
        val repository = InMemoryCredentialRepository(
            ScrmStoredCredentials(
                baseUrl = "https://old.example.com/openapi/v1",
                apiKey = ScrmApiKey.from("scrm_existing_1234"),
                selectedDeviceUuid = "old-device",
                selectedWeChatId = "old-wxid"
            )
        )
        val api = FakeReadApi()
        val service = ScrmSettingsService(repository) { api }

        val result = service.testConnection(
            baseUrl = "https://new.example.com",
            apiKeyInput = "scrm_candidate_5678"
        )

        assertTrue(result is ScrmConnectionTestResult.Success)
        assertEquals(null to null, api.quickStartRoute)
    }

    @Test
    fun knownApiErrorsBecomeExplicitUserFacingFailures() {
        val service = ScrmSettingsService(InMemoryCredentialRepository()) {
            object : ScrmReadApi {
                override fun getMe(): ScrmMe {
                    throw ScrmAuthenticationException("unauthorized")
                }

                override fun getDevices(): List<ScrmDevice> = error("not reached")
                override fun getWechatAccounts(): List<ScrmWechatAccount> = error("not reached")
                override fun getQuickStart(
                    deviceUuid: String?,
                    weChatId: String?,
                    scope: String,
                    includeBlocked: Boolean
                ): ScrmQuickStart = error("not reached")

                override fun getCapabilities(deviceUuid: String, weChatId: String): ScrmCapabilities =
                    error("not reached")
            }
        }

        val result = service.testConnection(
            "https://api.example.com",
            "scrm_invalid_1234"
        )

        assertEquals(
            ScrmConnectionTestResult.Failure("API Key 无效或已停用"),
            result
        )
    }

    private class InMemoryCredentialRepository(
        private var credentials: ScrmStoredCredentials? = null
    ) : ScrmCredentialRepository {
        var saveCalled: Boolean = false
            private set
        var savedKeyInput: String? = null
            private set
        var savedDeviceUuid: String? = null
            private set
        var savedWeChatId: String? = null
            private set

        override fun save(
            baseUrl: String,
            apiKeyInput: String,
            selectedDeviceUuid: String?,
            selectedWeChatId: String?
        ): ScrmStoredCredentials {
            saveCalled = true
            savedKeyInput = apiKeyInput
            savedDeviceUuid = selectedDeviceUuid
            savedWeChatId = selectedWeChatId
            return ScrmStoredCredentials(
                baseUrl = ScrmApiConfig(baseUrl, ScrmApiKey.from(apiKeyInput)).baseUrl,
                apiKey = ScrmApiKey.from(apiKeyInput),
                selectedDeviceUuid = selectedDeviceUuid,
                selectedWeChatId = selectedWeChatId
            ).also { credentials = it }
        }

        override fun load(): ScrmStoredCredentials? = credentials

        override fun clear() {
            credentials = null
        }
    }

    private class FakeReadApi : ScrmReadApi {
        var capabilityRoute: Pair<String, String>? = null
            private set
        var quickStartRoute: Pair<String?, String?>? = null
            private set

        override fun getMe(): ScrmMe {
            return ScrmMe(userId = "user-1", userName = "Tester")
        }

        override fun getDevices(): List<ScrmDevice> {
            return listOf(
                device(uuid = "device-1", online = true),
                device(uuid = "device-2", online = false)
            )
        }

        override fun getWechatAccounts(): List<ScrmWechatAccount> {
            return listOf(
                ScrmWechatAccount(
                    wxid = "wxid_1",
                    nickname = "测试账号",
                    clientUuid = "device-1",
                    accountStatus = 1
                )
            )
        }

        override fun getQuickStart(
            deviceUuid: String?,
            weChatId: String?,
            scope: String,
            includeBlocked: Boolean
        ): ScrmQuickStart {
            quickStartRoute = deviceUuid to weChatId
            return ScrmQuickStart(
                success = true,
                serverTime = "2026-07-12T10:00:00Z",
                deviceCount = 2,
                weChatAccountCount = 1,
                devicePreviewLimit = 10,
                weChatAccountPreviewLimit = 10,
                selectedDeviceUuid = "device-1",
                selectedWeChatId = "wxid_1"
            )
        }

        override fun getCapabilities(deviceUuid: String, weChatId: String): ScrmCapabilities {
            capabilityRoute = deviceUuid to weChatId
            return ScrmCapabilities(
                deviceAccessible = true,
                accountAccessible = true,
                hasRuntimeSnapshot = true,
                totalCount = 12,
                readyCount = 8,
                blockedCount = 2,
                unknownRuntimeCount = 2,
                pausedCount = 1,
                groups = listOf(
                    ScrmCapabilityGroup(
                        group = "messages",
                        groupName = "消息",
                        totalCount = 10,
                        readyCount = 5,
                        pausedCount = 1,
                        blockedCount = 2,
                        unknownRuntimeCount = 2
                    )
                )
            )
        }

        private fun device(uuid: String, online: Boolean): ScrmDevice {
            return ScrmDevice(
                uuid = uuid,
                isOnline = online,
                status = if (online) 1 else 0,
                androidApi = 35,
                appVersionCode = 1,
                updatedAt = "2026-07-12T10:00:00Z"
            )
        }
    }
}
