package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmAdminApiClientTest {
    @Test
    fun loginPostsBackendCredentialsToAdminAuthEndpoint() {
        val transport = RecordingTransport(
            ok("""{"data":{"token":"jwt_secret_token","user":{"email":"admin@example.com"}}}""")
        )

        val session = ScrmAdminApiClient(
            serverBaseUrl = "https://api.example.com/openapi/v1",
            transport = transport
        ).login(
            username = "admin@example.com",
            password = "admin-password"
        )

        val request = requireNotNull(transport.lastRequest)
        val body = Json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals("jwt_secret_token", session.token)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.com/api/auth/login", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("admin@example.com", body.getValue("username").jsonPrimitive.content)
        assertEquals("admin-password", body.getValue("password").jsonPrimitive.content)
        assertFalse(request.toString().contains("admin-password"))
    }

    @Test
    fun createOpenApiKeyUsesBearerTokenAndReturnsPlainKeyOnce() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "item": {
                    "id": 10,
                    "name": "只发 Android OpenAPI 联调",
                    "keyPrefix": "scrm_test****1234",
                    "remark": "Android 联调"
                  },
                  "plainKey": "scrm_plain_secret_1234"
                }
                """.trimIndent()
            )
        )

        val created = ScrmAdminApiClient(
            serverBaseUrl = "https://api.example.com",
            transport = transport
        ).createOpenApiKey(
            token = "jwt_secret_token",
            request = ScrmCreateOpenApiKeyRequest(
                name = "只发 Android OpenAPI 联调",
                remark = "Android 联调"
            )
        )

        val request = requireNotNull(transport.lastRequest)
        val body = Json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals("scrm_plain_secret_1234", created.plainKey)
        assertEquals("scrm_test****1234", created.keyPrefix)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.com/api/openapi-keys", request.url)
        assertEquals("Bearer jwt_secret_token", request.headers["Authorization"])
        assertEquals("只发 Android OpenAPI 联调", body.getValue("name").jsonPrimitive.content)
        assertFalse(request.toString().contains("jwt_secret_token"))
        assertFalse(created.toString().contains("scrm_plain_secret_1234"))
    }

    @Test
    fun adminHttpErrorsKeepActionableMessageWithoutSecrets() {
        val transport = RecordingTransport(
            ScrmHttpResponse(
                statusCode = 401,
                body = """{"message":"用户名或密码错误"}"""
            )
        )

        val result = runCatching {
            ScrmAdminApiClient("https://api.example.com", transport).login(
                username = "admin@example.com",
                password = "bad-password"
            )
        }

        assertTrue(result.exceptionOrNull() is ScrmRequestException)
        assertEquals("用户名或密码错误", result.exceptionOrNull()?.message)
        assertFalse(result.exceptionOrNull().toString().contains("bad-password"))
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
}
