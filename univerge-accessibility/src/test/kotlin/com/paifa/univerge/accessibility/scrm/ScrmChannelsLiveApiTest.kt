package com.paifa.univerge.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试流程：从视频号直播页提交已同步朋友圈编号，验证实际 OpenAPI 请求与直播素材响应解析。 */
class ScrmChannelsLiveApiTest {
    @Test
    fun copyMomentToFinderMaterialPostsLiveDraftToDocumentedRoute() {
        val transport = RecordingTransport(
            ScrmHttpResponse(
                statusCode = 200,
                body = """
                    {"success":true,"message":"created","detectedType":"live","publishReady":true}
                """.trimIndent()
            )
        )
        val client = ScrmApiClient(
            ScrmApiConfig(
                baseUrl = "https://api.example.com",
                apiKey = ScrmApiKey.from("test-api-key")
            ),
            transport
        )

        val result = client.copyMomentToFinderMaterial(
            snsId = 9527L,
            request = ScrmMomentCopyFinderMaterialRequest(
                deviceUuid = "device-1",
                weChatId = "wxid-me",
                snsId = 9527L,
                preferredType = "live",
                materialName = "夏季直播"
            )
        )

        val request = requireNotNull(transport.request)
        assertTrue(result.success)
        assertTrue(result.publishReady)
        assertEquals("POST", request.method)
        assertEquals(
            "https://api.example.com/openapi/v1/moments/9527/copy-finder-material",
            request.url
        )
        assertEquals("test-api-key", request.headers["X-API-Key"])
        assertTrue(requireNotNull(request.body).contains("\"preferredType\":\"live\""))
    }

    private class RecordingTransport(
        private val response: ScrmHttpResponse
    ) : ScrmHttpTransport {
        var request: ScrmHttpRequest? = null
            private set

        override fun execute(request: ScrmHttpRequest): ScrmHttpResponse {
            this.request = request
            return response
        }
    }
}
