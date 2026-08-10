package com.paifa.ubikitouch.accessibility.scrm

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmTaskApiClientTest {
    private val config = ScrmApiConfig(
        baseUrl = "https://api.example.com",
        apiKey = ScrmApiKey.from("scrm_test_secret_1234")
    )

    @Test
    fun getTaskParsesLongIdStatusAndStructuredData() {
        val transport = RecordingTransport(
            ScrmHttpResponse(
                statusCode = 200,
                body =
                    """{"taskId":42,"success":true,"status":"success","resultUnknown":false,"resultCode":"Success","message":"ok","deviceUuid":"device-1","connectionIdHash":"hash","receivedAt":"2026-07-12T10:00:00Z","rawHidden":true,"data":{"msgSvrId":"server-message-1"},"taskResultUrl":"/openapi/v1/tasks/42","recentTaskResultsUrl":"/openapi/v1/tasks/recent","nextStep":null}"""
            )
        )

        val result = ScrmApiClient(config, transport).getTask(42L)

        assertEquals(42L, result.taskId)
        assertEquals("success", result.status)
        assertEquals(
            "server-message-1",
            result.data?.jsonObject?.get("msgSvrId")?.jsonPrimitive?.content
        )
        assertEquals("https://api.example.com/openapi/v1/tasks/42", transport.lastRequest?.url)
        assertFalse(transport.lastRequest.toString().contains("tasks/42"))
        assertTrue(transport.lastRequest.toString().contains("tasks/{taskId}"))
    }

    @Test
    fun recentTasksUsesOptionalDeviceAndParsesItems() {
        val transport = RecordingTransport(
            ScrmHttpResponse(
                statusCode = 200,
                body =
                    """{"deviceUuid":"device-1","count":1,"items":[{"taskId":43,"success":false,"status":"failed","resultUnknown":false,"resultCode":"InternalError","message":"failed","deviceUuid":"device-1","connectionIdHash":null,"receivedAt":"2026-07-12T10:01:00Z","rawHidden":true,"data":null,"taskResultUrl":"/openapi/v1/tasks/43","recentTaskResultsUrl":"/openapi/v1/tasks/recent","nextStep":"check settings"}],"warnings":[]}"""
            )
        )

        val result = ScrmApiClient(config, transport).getRecentTasks(
            deviceUuid = "device 1",
            count = 10
        )

        assertEquals(1, result.count)
        assertEquals(43L, result.items.orEmpty().single().taskId)
        assertNull(result.items.orEmpty().single().data)
        assertEquals(
            "https://api.example.com/openapi/v1/tasks/recent?deviceUuid=device%201&count=10",
            transport.lastRequest?.url
        )
    }

    @Test
    fun taskIdMustBePositiveBeforeTransportCall() {
        val transport = RecordingTransport(ScrmHttpResponse(200, "{}"))

        val result = runCatching { ScrmApiClient(config, transport).getTask(0L) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertNull(transport.lastRequest)
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
