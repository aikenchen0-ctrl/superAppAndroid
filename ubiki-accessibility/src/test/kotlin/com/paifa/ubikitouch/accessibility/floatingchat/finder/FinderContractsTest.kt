package com.paifa.ubikitouch.accessibility.floatingchat.finder

import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmApiConfig
import com.paifa.ubikitouch.accessibility.scrm.ScrmApiKey
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpResponse
import com.paifa.ubikitouch.accessibility.scrm.ScrmHttpTransport
import com.paifa.ubikitouch.accessibility.scrm.ScrmTimeoutException
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinderContractsTest {
    @Test
    fun finderMessageClickOnlyAcceptsChannelMessagesAndTrustedUsernameMetadata() {
        val channelMessage = finderMessage(
            type = FloatingChatMessageType.ChannelsLive,
            resourceUrl = "https://channels.weixin.qq.com/feed?sphUserName=sph_creator_1"
        )
        val regularLink = finderMessage(
            type = FloatingChatMessageType.WebLink,
            resourceUrl = "https://channels.weixin.qq.com/feed?sphUserName=sph_creator_2"
        )

        assertTrue(isFinderMessage(channelMessage))
        assertEquals("sph_creator_1", finderUserNameForMessage(channelMessage))
        assertFalse(isFinderMessage(regularLink))
        assertNull(finderUserNameForMessage(regularLink))
    }

    @Test
    fun finderMessageClickDoesNotGuessUsernameFromDisplayTextOrMalformedUrl() {
        assertNull(
            finderUserNameForMessage(
                finderMessage(
                    type = FloatingChatMessageType.ChannelsVideo,
                    resourceUrl = "https://channels.weixin.qq.com/feed?sphUserName=",
                    text = "sph_not_trusted"
                )
            )
        )
        assertNull(
            finderUserNameForMessage(
                finderMessage(
                    type = FloatingChatMessageType.ChannelsLive,
                    resourceUrl = "not a url",
                    text = "sph_not_trusted"
                )
            )
        )
    }

    @Test
    fun mediaUrlValidationOnlyAcceptsAccessibleHttpSchemes() {
        assertTrue(isAccessibleHttpUrl("https://cdn.example.net/finder/video.mp4"))
        assertTrue(isAccessibleHttpUrl("http://10.0.0.8/media/image.jpg"))
        assertFalse(isAccessibleHttpUrl("file:///sdcard/video.mp4"))
        assertFalse(isAccessibleHttpUrl("ftp://cdn.example.net/video.mp4"))
        assertFalse(isAccessibleHttpUrl("http://"))
        assertFalse(isAccessibleHttpUrl("https://"))
    }

    @Test
    fun postTemplateRequestCarriesFinderContractFields() {
        val request = FinderPostTemplateRequest(
            deviceUuid = "device-1",
            weChatId = "wxid-owner",
            content = "正文 #话题",
            medias = listOf("https://cdn.example.net/finder/video.mp4"),
            mediaType = FinderMediaType.Video.code,
            cover = "https://cdn.example.net/finder/cover.jpg",
            includePostRequest = true
        )

        assertEquals("/openapi/v1/finder/posts/template", FinderEndpoints.POST_TEMPLATE)
        assertEquals(FinderMediaType.Video.code, request.mediaType)
        assertEquals(true, request.includePostRequest)
        assertEquals(1, request.medias.size)

        val json = Json.encodeToString(request)
        assertFalse(json.contains("normalizedKeyword"))
    }

    @Test
    fun templateValidationRequiresServerReturnedPublishPayload() {
        val response = FinderPostTemplateResponse(success = true)

        val failure = runCatching { validatedFinderPostRequest(response) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("发布模板未返回可提交的发布载荷", failure?.message)
    }

    @Test
    fun navigationSearchRequiresBoundedKeywordAndHomeDropsKeyword() {
        val home = FinderNavigationRequest(
            deviceUuid = "device-1",
            weChatId = "wxid-owner",
            action = FinderNavigationAction.Home,
            keyword = "ignored"
        )
        assertEquals(null, home.normalizedKeyword)

        val search = FinderNavigationRequest(
            deviceUuid = "device-1",
            weChatId = "wxid-owner",
            action = FinderNavigationAction.Search,
            keyword = "  cats  "
        )
        assertEquals("cats", search.normalizedKeyword)
        assertTrue(runCatching {
            FinderNavigationRequest(
                deviceUuid = "device-1",
                weChatId = "wxid-owner",
                action = FinderNavigationAction.Search,
                keyword = ""
            )
        }.isFailure)
    }

    @Test
    fun taskAwaiterStopsAtTerminalStatesAndSurfacesUnknown() {
        val api = FakeTaskApi(
            listOf(
                task(status = "processing"),
                task(status = "success", success = true)
            )
        )
        val result = FinderTaskAwaiter(api, pollDelayMillis = 1, sleepMillis = {}).await {
            ScrmTaskSubmissionResult(taskId = 42, success = true, message = "submitted")
        }
        assertTrue(result.completed)
        assertEquals(2, api.calls)

        val unknownApi = FakeTaskApi(listOf(task(status = "failed", resultUnknown = true)))
        assertTrue(runCatching {
            FinderTaskAwaiter(unknownApi, pollDelayMillis = 1, sleepMillis = {}).await {
                ScrmTaskSubmissionResult(taskId = 42, success = true)
            }
        }.isFailure)
    }

    @Test
    fun taskAwaiterReportsPollExhaustionAsTimeoutInsteadOfIncompleteSuccess() {
        val api = FakeTaskApi(List(2) { task(status = "processing") })

        val failure = runCatching {
            FinderTaskAwaiter(
                taskApi = api,
                pollDelayMillis = 1,
                maxPollAttempts = 2,
                sleepMillis = {}
            ).await {
                ScrmTaskSubmissionResult(taskId = 42, success = true, message = "submitted")
            }
        }.exceptionOrNull()

        assertTrue(failure is ScrmTimeoutException)
        assertEquals(2, api.calls)
    }

    @Test
    fun topicComposerNormalizesHashTagsWithoutUsingDemoMedia() {
        assertEquals("正文 #春天 #旅行", finderComposeContent(" 正文 ", "春天, #旅行"))
        assertEquals(listOf("https://cdn.example.net/a.mp4", "http://10.0.0.8/b.jpg"), finderParseMediaUrls("https://cdn.example.net/a.mp4\nhttp://10.0.0.8/b.jpg"))
    }

    @Test
    fun fixtureGatewayUsesDocumentedFinderRoutesWithoutSendingRealRequests() {
        val transport = RecordingFinderTransport(
            ScrmHttpResponse(200, "{\"taskId\":7,\"success\":true,\"message\":\"queued\"}")
        )
        val api = ScrmFinderApi(
            config = ScrmApiConfig("https://api.example.com", ScrmApiKey.from("test-key-1234")),
            transport = transport
        )

        val submission = api.navigate(
            FinderNavigationRequest("device-1", "wxid-owner", FinderNavigationAction.Home)
        )

        assertEquals(7L, submission.taskId)
        assertEquals("POST", transport.lastRequest?.method)
        assertEquals("https://api.example.com/openapi/v1/finder/navigation", transport.lastRequest?.url)
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"action\":\"home\""))
        assertFalse(transport.lastRequest?.toString().orEmpty().contains("test-key-1234"))
    }

    private fun task(
        status: String,
        success: Boolean = false,
        resultUnknown: Boolean = false
    ) = ScrmTaskResult(
        taskId = 42,
        success = success,
        status = status,
        resultUnknown = resultUnknown,
        resultCode = null,
        message = null,
        deviceUuid = "device-1",
        connectionIdHash = null,
        receivedAt = "2026-08-12T00:00:00Z",
        rawHidden = true,
        data = JsonPrimitive("ok"),
        taskResultUrl = null,
        recentTaskResultsUrl = null,
        nextStep = null
    )

    private fun finderMessage(
        type: FloatingChatMessageType,
        resourceUrl: String?,
        text: String = "视频号消息"
    ) = FloatingChatMessage(
        id = "finder-message",
        type = type,
        text = text,
        fromMe = false,
        senderName = "视频号作者",
        time = "10:00",
        resourceUrl = resourceUrl
    )

    private class FakeTaskApi(private val results: List<ScrmTaskResult>) : ScrmTaskApi {
        var calls: Int = 0
            private set

        override fun getTask(taskId: Long): ScrmTaskResult {
            return results[calls++]
        }

        override fun getRecentTasks(deviceUuid: String?, count: Int) =
            error("not used")
    }

    private class RecordingFinderTransport(
        private val response: ScrmHttpResponse
    ) : ScrmHttpTransport {
        var lastRequest: ScrmHttpRequest? = null

        override fun execute(request: ScrmHttpRequest): ScrmHttpResponse {
            lastRequest = request
            return response
        }
    }
}
