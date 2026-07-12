package com.paifa.ubikitouch.accessibility.scrm

import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmApiClientTest {
    private val rawApiKey = "scrm_test_secret_1234"
    private val config = ScrmApiConfig(
        baseUrl = "https://api.example.com",
        apiKey = ScrmApiKey.from(rawApiKey)
    )

    @Test
    fun getMeInjectsApiKeyAndParsesIdentity() {
        val transport = RecordingTransport(
            ScrmHttpResponse(
                statusCode = 200,
                body =
                    """{"userId":"user-1","userName":"Tester","email":"t@example.com","authType":"ApiKey","openApiKeyId":7,"roles":["Admin"],"permissions":["messages.read"],"futureField":true}"""
            )
        )

        val me = ScrmApiClient(config, transport).getMe()

        assertEquals("user-1", me.userId)
        assertEquals("Tester", me.userName)
        assertEquals(7L, me.openApiKeyId)
        assertEquals(listOf("Admin"), me.roles)
        assertEquals("GET", transport.lastRequest?.method)
        assertEquals(
            "https://api.example.com/openapi/v1/me",
            transport.lastRequest?.url
        )
        assertEquals(rawApiKey, transport.lastRequest?.headers?.get("X-API-Key"))
        assertEquals("application/json", transport.lastRequest?.headers?.get("Accept"))
    }

    @Test
    fun getDevicesParsesRuntimeDeviceFields() {
        val transport = RecordingTransport(
            ok(
                """[{"uuid":"device-1","ownerId":"owner-1","isOnline":true,"status":2,"weChatId":"wxid_1","phoneBrand":"OnePlus","phoneModel":"Test","androidApi":35,"appPackageName":"com.tencent.mm","appVersion":"8.0","appVersionCode":100,"lastLoginAt":null,"updatedAt":"2026-07-12T10:00:00Z"}]"""
            )
        )

        val devices = ScrmApiClient(config, transport).getDevices()

        assertEquals(1, devices.size)
        assertEquals("device-1", devices.single().uuid)
        assertTrue(devices.single().isOnline)
        assertEquals(35, devices.single().androidApi)
        assertNull(devices.single().lastLoginAt)
    }

    @Test
    fun getWechatAccountsParsesAccountRoutingFields() {
        val transport = RecordingTransport(
            ok(
                """[{"wxid":"wxid_1","nickname":"测试账号","clientUuid":"device-1","ownerId":"owner-1","accountStatus":1,"lastOnlineAt":"2026-07-12T10:00:00Z"}]"""
            )
        )

        val accounts = ScrmApiClient(config, transport).getWechatAccounts()

        assertEquals(1, accounts.size)
        assertEquals("wxid_1", accounts.single().wxid)
        assertEquals("测试账号", accounts.single().nickname)
        assertEquals("device-1", accounts.single().clientUuid)
    }

    @Test
    fun getQuickStartUsesSelectedRouteAndParsesDiscoverySummary() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "success": true,
                  "apiVersion": "v1",
                  "serverTime": "2026-07-12T10:00:00Z",
                  "landingStatus": "ready",
                  "landingStatusText": "Ready",
                  "userId": "user-1",
                  "userName": "Tester",
                  "authType": "ApiKey",
                  "openApiKeyId": 7,
                  "roles": ["Admin"],
                  "permissions": ["messages.read"],
                  "deviceCount": 1,
                  "weChatAccountCount": 1,
                  "devicePreviewLimit": 10,
                  "weChatAccountPreviewLimit": 10,
                  "devices": [],
                  "weChatAccounts": [],
                  "selectedDeviceUuid": "device-1",
                  "selectedWeChatId": "wxid_1",
                  "capabilitiesUrl": "/openapi/v1/capabilities",
                  "testPlanUrl": "/openapi/v1/test-plan",
                  "nextBlockers": [],
                  "nextRecommendedActions": ["test"],
                  "recommendedChecks": [],
                  "warnings": []
                }
                """.trimIndent()
            )
        )

        val result = ScrmApiClient(config, transport).getQuickStart(
            deviceUuid = "device 1",
            weChatId = "wxid+1",
            scope = "p0",
            includeBlocked = false
        )

        assertTrue(result.success)
        assertEquals("device-1", result.selectedDeviceUuid)
        assertEquals(1, result.deviceCount)
        assertEquals(
            "https://api.example.com/openapi/v1/quick-start" +
                "?deviceUuid=device%201&weChatId=wxid%2B1&scope=p0&includeBlocked=false",
            transport.lastRequest?.url
        )
    }

    @Test
    fun getCapabilitiesParsesStatusAndBlockers() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "deviceUuid": "device-1",
                  "weChatId": "wxid_1",
                  "deviceAccessible": true,
                  "accountAccessible": true,
                  "hasRuntimeSnapshot": true,
                  "runtimeSnapshotSource": "android",
                  "runtimeSnapshotReceivedAt": "2026-07-12T10:00:00Z",
                  "capabilities": [{
                    "code": "message.text",
                    "name": "文本消息",
                    "group": "messages",
                    "groupName": "消息",
                    "status": "ready",
                    "settingKey": "allowText",
                    "runtimeEffectiveKey": "allowTextEffective",
                    "requiredPermission": "messages.send",
                    "httpMethod": "POST",
                    "route": "/openapi/v1/messages/text",
                    "settingsPresetUrl": null,
                    "minimalTestHint": "send one message",
                    "serverConfigured": true,
                    "permissionAllowed": true,
                    "assetAllowed": true,
                    "runtimeEffective": true,
                    "serverWouldAllow": true,
                    "readyForTest": true,
                    "requiresRuntimeSnapshot": true,
                    "requiresSettingsPush": false,
                    "requiresAndroidEffective": true,
                    "blockers": [],
                    "recommendedActions": [],
                    "nextStep": "test"
                  }],
                  "totalCount": 1,
                  "readyCount": 1,
                  "blockedCount": 0,
                  "unknownRuntimeCount": 0,
                  "pausedCount": 0,
                  "groups": [{
                    "group": "messages",
                    "groupName": "消息",
                    "totalCount": 1,
                    "readyCount": 1,
                    "pausedCount": 0,
                    "blockedCount": 0,
                    "unknownRuntimeCount": 0
                  }],
                  "recommendedChecks": [],
                  "warnings": []
                }
                """.trimIndent()
            )
        )

        val result = ScrmApiClient(config, transport).getCapabilities("device-1", "wxid_1")

        assertEquals(1, result.readyCount)
        assertEquals("message.text", result.capabilities.orEmpty().single().code)
        assertTrue(result.capabilities.orEmpty().single().readyForTest)
        assertEquals("messages", result.groups.orEmpty().single().group)
        assertEquals(
            "https://api.example.com/openapi/v1/capabilities" +
                "?deviceUuid=device-1&weChatId=wxid_1",
            transport.lastRequest?.url
        )
    }

    @Test
    fun sendTextPostsStrictJsonAndParsesAcceptedTask() {
        val transport = RecordingTransport(
            ok(
                """{"taskId":42,"success":true,"message":"accepted","data":{"clientRequestId":"client-1"},"taskResultUrl":"/openapi/v1/tasks/42","recentTaskResultsUrl":"/openapi/v1/tasks/recent"}"""
            )
        )

        val task = ScrmApiClient(config, transport).sendText(
            ScrmSendTextMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                content = "你好，稍后给你报价",
                atIds = "wxid_member_1,wxid_member_2"
            )
        )

        val request = requireNotNull(transport.lastRequest)
        val body = requireNotNull(request.body)
        val bodyJson = Json.parseToJsonElement(body).jsonObject
        assertEquals(42L, task.taskId)
        assertEquals(true, task.success)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.com/openapi/v1/messages/text", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(rawApiKey, request.headers["X-API-Key"])
        assertEquals("device-1", bodyJson.getValue("deviceUuid").jsonPrimitive.content)
        assertEquals("wxid_account", bodyJson.getValue("weChatId").jsonPrimitive.content)
        assertEquals("wxid_friend", bodyJson.getValue("conversationId").jsonPrimitive.content)
        assertEquals("你好，稍后给你报价", bodyJson.getValue("content").jsonPrimitive.content)
        assertEquals(
            "wxid_member_1,wxid_member_2",
            bodyJson.getValue("atIds").jsonPrimitive.content
        )
        assertFalse(body.contains(rawApiKey))
        assertFalse(request.toString().contains(rawApiKey))
        assertFalse(request.toString().contains("wxid_friend"))
        assertFalse(request.toString().contains("稍后给你报价"))
    }

    @Test
    fun missingApiKeyFailsBeforeTransportIsCalled() {
        val transport = RecordingTransport(ok("{}"))
        val client = ScrmApiClient(
            config = ScrmApiConfig(baseUrl = "https://api.example.com", apiKey = null),
            transport = transport
        )

        val result = runCatching { client.getMe() }

        assertTrue(result.exceptionOrNull() is ScrmConfigurationException)
        assertNull(transport.lastRequest)
    }

    @Test
    fun statusCodesMapToActionableApiErrors() {
        val cases = listOf(
            401 to ScrmAuthenticationException::class.java,
            403 to ScrmPermissionException::class.java,
            429 to ScrmRateLimitException::class.java,
            503 to ScrmServerException::class.java
        )

        cases.forEach { (status, expectedType) ->
            val response = ScrmHttpResponse(
                statusCode = status,
                body = """{"message":"request failed"}""",
                headers = if (status == 429) mapOf("Retry-After" to "12") else emptyMap()
            )
            val result = runCatching {
                ScrmApiClient(config, RecordingTransport(response)).getMe()
            }

            assertTrue("HTTP $status", expectedType.isInstance(result.exceptionOrNull()))
            assertFalse(result.exceptionOrNull().toString().contains(rawApiKey))
            if (status == 429) {
                assertEquals(
                    12L,
                    (result.exceptionOrNull() as ScrmRateLimitException).retryAfterSeconds
                )
            }
        }
    }

    @Test
    fun nonJsonSuccessResponseFailsExplicitly() {
        val result = runCatching {
            ScrmApiClient(config, RecordingTransport(ok("service is warming up"))).getMe()
        }

        assertTrue(result.exceptionOrNull() is ScrmInvalidResponseException)
        assertFalse(result.exceptionOrNull().toString().contains(rawApiKey))
    }

    @Test
    fun timeoutIsMappedWithoutLeakingCredentials() {
        val transport = ThrowingTransport(SocketTimeoutException("timed out"))

        val result = runCatching { ScrmApiClient(config, transport).getMe() }

        assertTrue(result.exceptionOrNull() is ScrmTimeoutException)
        assertFalse(result.exceptionOrNull().toString().contains(rawApiKey))
    }

    private fun ok(body: String): ScrmHttpResponse {
        return ScrmHttpResponse(
            statusCode = 200,
            body = body,
            headers = mapOf("Content-Type" to "application/json")
        )
    }

    private class RecordingTransport(
        private val response: ScrmHttpResponse
    ) : ScrmHttpTransport {
        var lastRequest: ScrmHttpRequest? = null
            private set

        override fun execute(request: ScrmHttpRequest): ScrmHttpResponse {
            lastRequest = request
            return response
        }
    }

    private class ThrowingTransport(
        private val error: Exception
    ) : ScrmHttpTransport {
        override fun execute(request: ScrmHttpRequest): ScrmHttpResponse {
            throw error
        }
    }
}
