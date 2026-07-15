package com.paifa.ubikitouch.accessibility.scrm

import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
    fun sendMediaMessagesPostSwaggerJsonBodies() {
        val imageTransport = RecordingTransport(ok("""{"taskId":43,"success":true}"""))
        val imageTask = ScrmApiClient(config, imageTransport).sendImage(
            ScrmSendImageMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                imageUrl = "https://cdn.example.com/image.jpg"
            )
        )
        val imageRequest = requireNotNull(imageTransport.lastRequest)
        val imageBody = Json.parseToJsonElement(requireNotNull(imageRequest.body)).jsonObject
        assertEquals(43L, imageTask.taskId)
        assertEquals("POST", imageRequest.method)
        assertEquals("https://api.example.com/openapi/v1/messages/image", imageRequest.url)
        assertEquals("https://cdn.example.com/image.jpg", imageBody.getValue("imageUrl").jsonPrimitive.content)

        val videoTransport = RecordingTransport(ok("""{"taskId":44,"success":true}"""))
        val videoTask = ScrmApiClient(config, videoTransport).sendVideo(
            ScrmSendVideoMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "room@chatroom",
                videoUrl = "https://cdn.example.com/video.mp4"
            )
        )
        val videoRequest = requireNotNull(videoTransport.lastRequest)
        val videoBody = Json.parseToJsonElement(requireNotNull(videoRequest.body)).jsonObject
        assertEquals(44L, videoTask.taskId)
        assertEquals("https://api.example.com/openapi/v1/messages/video", videoRequest.url)
        assertEquals("room@chatroom", videoBody.getValue("conversationId").jsonPrimitive.content)
        assertEquals("https://cdn.example.com/video.mp4", videoBody.getValue("videoUrl").jsonPrimitive.content)

        val voiceTransport = RecordingTransport(ok("""{"taskId":45,"success":true}"""))
        val voiceTask = ScrmApiClient(config, voiceTransport).sendVoice(
            ScrmSendVoiceMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                voiceUrl = "https://cdn.example.com/voice.amr",
                durationSeconds = 4
            )
        )
        val voiceRequest = requireNotNull(voiceTransport.lastRequest)
        val voiceBody = Json.parseToJsonElement(requireNotNull(voiceRequest.body)).jsonObject
        assertEquals(45L, voiceTask.taskId)
        assertEquals("https://api.example.com/openapi/v1/messages/voice", voiceRequest.url)
        assertEquals("https://cdn.example.com/voice.amr", voiceBody.getValue("voiceUrl").jsonPrimitive.content)
        assertEquals("4", voiceBody.getValue("durationSeconds").jsonPrimitive.content)
    }

    @Test
    fun sendFilePostsSwaggerJsonBody() {
        val transport = RecordingTransport(ok("""{"taskId":46,"success":true}"""))

        val task = ScrmApiClient(config, transport).sendFile(
            ScrmSendFileMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                fileName = "quote.pdf",
                fileUrl = "https://cdn.example.com/docs/quote.pdf"
            )
        )

        val request = requireNotNull(transport.lastRequest)
        val body = Json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals(46L, task.taskId)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.com/openapi/v1/messages/file", request.url)
        assertEquals("device-1", body.getValue("deviceUuid").jsonPrimitive.content)
        assertEquals("wxid_account", body.getValue("weChatId").jsonPrimitive.content)
        assertEquals("wxid_friend", body.getValue("conversationId").jsonPrimitive.content)
        assertEquals("quote.pdf", body.getValue("fileName").jsonPrimitive.content)
        assertEquals("https://cdn.example.com/docs/quote.pdf", body.getValue("fileUrl").jsonPrimitive.content)
    }

    @Test
    fun sendCardAndQuoteMessagesPostSwaggerJsonBodies() {
        val linkTransport = RecordingTransport(ok("""{"taskId":47,"success":true}"""))
        val linkTask = ScrmApiClient(config, linkTransport).sendLinkCard(
            ScrmSendLinkCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                url = "https://example.com/order/1",
                title = "订单详情",
                description = "点击查看报价",
                thumb = "https://example.com/thumb.jpg",
                appId = "app-1",
                sourceName = "销售系统",
                source = "sales"
            )
        )
        val linkRequest = requireNotNull(linkTransport.lastRequest)
        val linkBody = Json.parseToJsonElement(requireNotNull(linkRequest.body)).jsonObject
        assertEquals(47L, linkTask.taskId)
        assertEquals("https://api.example.com/openapi/v1/messages/link-card", linkRequest.url)
        assertEquals("https://example.com/order/1", linkBody.getValue("url").jsonPrimitive.content)
        assertEquals("订单详情", linkBody.getValue("title").jsonPrimitive.content)
        assertEquals("销售系统", linkBody.getValue("sourceName").jsonPrimitive.content)

        val noteTransport = RecordingTransport(ok("""{"taskId":48,"success":true}"""))
        val noteTask = ScrmApiClient(config, noteTransport).sendNoteCard(
            ScrmSendNoteCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                title = "会议纪要",
                description = "重点事项",
                thumb = "https://example.com/note.jpg",
                recordItem = """{"type":"note","id":"note-1"}"""
            )
        )
        val noteBody = Json.parseToJsonElement(requireNotNull(noteTransport.lastRequest?.body)).jsonObject
        assertEquals(48L, noteTask.taskId)
        assertEquals("https://api.example.com/openapi/v1/messages/note-card", noteTransport.lastRequest?.url)
        assertEquals("会议纪要", noteBody.getValue("title").jsonPrimitive.content)
        assertEquals("""{"type":"note","id":"note-1"}""", noteBody.getValue("recordItem").jsonPrimitive.content)

        val officialTransport = RecordingTransport(ok("""{"taskId":49,"success":true}"""))
        val officialTask = ScrmApiClient(config, officialTransport).sendOfficialArticleCard(
            ScrmSendLinkCardMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                url = "https://mp.weixin.qq.com/s/article",
                title = "公众号文章",
                description = "文章摘要",
                thumb = "https://example.com/article.jpg",
                sourceName = "公众号"
            )
        )
        val officialBody = Json.parseToJsonElement(requireNotNull(officialTransport.lastRequest?.body)).jsonObject
        assertEquals(49L, officialTask.taskId)
        assertEquals(
            "https://api.example.com/openapi/v1/messages/official-article-card",
            officialTransport.lastRequest?.url
        )
        assertEquals("https://mp.weixin.qq.com/s/article", officialBody.getValue("url").jsonPrimitive.content)

        val quoteTransport = RecordingTransport(ok("""{"taskId":50,"success":true}"""))
        val quoteTask = ScrmApiClient(config, quoteTransport).sendQuote(
            ScrmSendQuoteMessageRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                conversationId = "wxid_friend",
                content = "这条引用回复",
                quoteMsgSvrId = 88990011L
            )
        )
        val quoteBody = Json.parseToJsonElement(requireNotNull(quoteTransport.lastRequest?.body)).jsonObject
        assertEquals(50L, quoteTask.taskId)
        assertEquals("https://api.example.com/openapi/v1/messages/quote", quoteTransport.lastRequest?.url)
        assertEquals("这条引用回复", quoteBody.getValue("content").jsonPrimitive.content)
        assertEquals("88990011", quoteBody.getValue("quoteMsgSvrId").jsonPrimitive.content)
    }

    @Test
    fun taskSubmissionFailureResponseExposesServiceMessage() {
        val transport = RecordingTransport(
            ok("""{"taskId":0,"success":false,"message":"好友添加失败：账号不存在"}""")
        )

        val result = runCatching {
            ScrmApiClient(config, transport).addFriend(
                ScrmAddFriendRequest(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    friendWxid = "13428811836",
                    message = "hello"
                )
            )
        }

        val error = result.exceptionOrNull()
        assertTrue(error is ScrmRequestException)
        assertEquals("好友添加失败：账号不存在", error?.message)
    }

    @Test
    fun taskSubmissionWithoutTaskIdFailsBeforeRecentTaskFallback() {
        val transport = RecordingTransport(
            ok("""{"success":true,"message":"朋友圈同步未返回 taskId"}""")
        )

        val result = runCatching {
            ScrmApiClient(config, transport).syncMoments(
                ScrmSyncMomentsRequest(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    startTime = 0L
                )
            )
        }

        val error = result.exceptionOrNull()
        assertTrue(error is ScrmRequestException)
        assertEquals("朋友圈同步未返回 taskId", error?.message)
    }

    @Test
    fun momentsEndpointsPostSwaggerJsonBodies() {
        val postTransport = RecordingTransport(ok("""{"taskId":201,"success":true}"""))
        val postTask = ScrmApiClient(config, postTransport).postMoment(
            ScrmPostMomentRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                clientRequestId = "moment-request-1",
                content = "today update",
                attachmentType = "image",
                attachments = listOf("https://cdn.example.com/moment.jpg"),
                payload = ScrmMomentPostPayload(
                    clientRequestId = "moment-request-1",
                    weChatId = "wxid_account",
                    content = "today update",
                    attachment = ScrmMomentPostAttachment(
                        type = ScrmMomentAttachmentType.Image,
                        content = listOf("https://cdn.example.com/moment.jpg")
                    )
                )
            )
        )
        val postRequest = requireNotNull(postTransport.lastRequest)
        val postBody = Json.parseToJsonElement(requireNotNull(postRequest.body)).jsonObject
        assertEquals(201L, postTask.taskId)
        assertEquals("POST", postRequest.method)
        assertEquals("https://api.example.com/openapi/v1/moments", postRequest.url)
        assertEquals("device-1", postBody.getValue("deviceUuid").jsonPrimitive.content)
        assertEquals("wxid_account", postBody.getValue("weChatId").jsonPrimitive.content)
        assertEquals("moment-request-1", postBody.getValue("clientRequestId").jsonPrimitive.content)
        assertEquals("image", postBody.getValue("attachmentType").jsonPrimitive.content)
        assertEquals(
            "https://cdn.example.com/moment.jpg",
            postBody.getValue("attachments").jsonArray.single().jsonPrimitive.content
        )
        assertEquals(
            "2",
            postBody.getValue("payload")
                .jsonObject.getValue("attachment")
                .jsonObject.getValue("type")
                .jsonPrimitive.content
        )
        assertFalse(postBody.containsKey("comment"))
        assertFalse(postBody.containsKey("extComment"))
        assertFalse(postBody.containsKey("extComments"))
        assertFalse(postBody.toString().contains("sns_send_ok"))
        val payloadBody = postBody.getValue("payload").jsonObject
        assertFalse(payloadBody.containsKey("comment"))
        assertFalse(payloadBody.containsKey("extComment"))
        assertFalse(payloadBody.containsKey("extComments"))
        assertFalse(payloadBody.toString().contains("sns_send_ok"))

        val syncTransport = RecordingTransport(ok("""{"taskId":202,"success":true}"""))
        ScrmApiClient(config, syncTransport).syncMoments(
            ScrmSyncMomentsRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                startTime = 0L,
                circleIds = listOf(123L)
            )
        )
        val syncRequest = requireNotNull(syncTransport.lastRequest)
        val syncBody = Json.parseToJsonElement(requireNotNull(syncRequest.body)).jsonObject
        assertEquals("https://api.example.com/openapi/v1/moments/sync", syncRequest.url)
        assertEquals("123", syncBody.getValue("circleIds").jsonArray.single().jsonPrimitive.content)

        val messagesTransport = RecordingTransport(ok("""{"taskId":203,"success":true}"""))
        ScrmApiClient(config, messagesTransport).syncMomentMessages(
            ScrmSyncMomentMessagesRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                onlyComment = false,
                getAll = true
            )
        )
        val messagesRequest = requireNotNull(messagesTransport.lastRequest)
        val messagesBody = Json.parseToJsonElement(requireNotNull(messagesRequest.body)).jsonObject
        assertEquals("https://api.example.com/openapi/v1/moments/messages/sync", messagesRequest.url)
        assertEquals("false", messagesBody.getValue("onlyComment").jsonPrimitive.content)
        assertEquals("true", messagesBody.getValue("getAll").jsonPrimitive.content)

        val detailTransport = RecordingTransport(ok("""{"taskId":204,"success":true}"""))
        ScrmApiClient(config, detailTransport).getMomentDetail(
            ScrmMomentDetailRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                circleId = 123L,
                getBigMap = true
            )
        )
        val detailRequest = requireNotNull(detailTransport.lastRequest)
        val detailBody = Json.parseToJsonElement(requireNotNull(detailRequest.body)).jsonObject
        assertEquals("https://api.example.com/openapi/v1/moments/detail", detailRequest.url)
        assertEquals("123", detailBody.getValue("circleId").jsonPrimitive.content)
        assertEquals("true", detailBody.getValue("getBigMap").jsonPrimitive.content)

        val likeTransport = RecordingTransport(ok("""{"taskId":205,"success":true}"""))
        ScrmApiClient(config, likeTransport).likeMoment(
            ScrmMomentLikeRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                circleId = 123L,
                isCancel = false
            )
        )
        val likeRequest = requireNotNull(likeTransport.lastRequest)
        val likeBody = Json.parseToJsonElement(requireNotNull(likeRequest.body)).jsonObject
        assertEquals("https://api.example.com/openapi/v1/moments/like", likeRequest.url)
        assertEquals("false", likeBody.getValue("isCancel").jsonPrimitive.content)

        val commentTransport = RecordingTransport(ok("""{"taskId":206,"success":true}"""))
        ScrmApiClient(config, commentTransport).commentMoment(
            ScrmMomentCommentRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                circleId = 123L,
                toWeChatId = "wxid_friend",
                content = "nice",
                replyCommentId = 0L,
                isResend = false
            )
        )
        val commentRequest = requireNotNull(commentTransport.lastRequest)
        val commentBody = Json.parseToJsonElement(requireNotNull(commentRequest.body)).jsonObject
        assertEquals("https://api.example.com/openapi/v1/moments/comments", commentRequest.url)
        assertEquals("wxid_friend", commentBody.getValue("toWeChatId").jsonPrimitive.content)
        assertEquals("nice", commentBody.getValue("content").jsonPrimitive.content)
        assertEquals("0", commentBody.getValue("replyCommentId").jsonPrimitive.content)
    }

    @Test
    fun uploadMediaPostsMultipartFileAndParsesUploadedUrl() {
        val transport = RecordingTransport(
            ok(
                """{"success":true,"fileUrl":"https://cdn.example.com/upload/photo.jpg","fileName":"photo.jpg","mediaType":"image","contentType":"image/jpeg","fileSize":3}"""
            )
        )

        val upload = ScrmApiClient(config, transport).uploadMedia(
            ScrmMediaUploadRequest(
                fileName = "photo.jpg",
                contentType = "image/jpeg",
                bytes = byteArrayOf(0x41, 0x42, 0x43)
            )
        )

        val request = requireNotNull(transport.lastRequest)
        val bodyBytes = requireNotNull(request.bodyBytes)
        val bodyText = bodyBytes.toString(Charsets.ISO_8859_1)
        assertEquals(true, upload.success)
        assertEquals("https://cdn.example.com/upload/photo.jpg", upload.fileUrl)
        assertEquals("POST", request.method)
        assertEquals("https://api.example.com/openapi/v1/media", request.url)
        assertTrue(request.headers.getValue("Content-Type").startsWith("multipart/form-data; boundary="))
        assertTrue(bodyText.contains("""name="file""""))
        assertTrue(bodyText.contains("""filename="photo.jpg""""))
        assertTrue(bodyText.contains("Content-Type: image/jpeg"))
        assertTrue(bodyText.contains("ABC"))
        assertNull(request.body)
    }

    @Test
    fun uploadVoicePostsVoiceEndpointAndParsesDuration() {
        val transport = RecordingTransport(
            ok(
                """{"success":true,"voiceUrl":"https://cdn.example.com/upload/voice.amr","durationSeconds":6,"format":"amr","fileSize":3}"""
            )
        )

        val upload = ScrmApiClient(config, transport).uploadVoice(
            ScrmMediaUploadRequest(
                fileName = "voice.m4a",
                contentType = "audio/mp4",
                bytes = byteArrayOf(0x56, 0x4f, 0x43)
            )
        )

        val request = requireNotNull(transport.lastRequest)
        val bodyText = requireNotNull(request.bodyBytes).toString(Charsets.ISO_8859_1)
        assertEquals(true, upload.success)
        assertEquals("https://cdn.example.com/upload/voice.amr", upload.voiceUrl)
        assertEquals(6, upload.durationSeconds)
        assertEquals("https://api.example.com/openapi/v1/media/voice", request.url)
        assertTrue(bodyText.contains("""filename="voice.m4a""""))
        assertTrue(bodyText.contains("VOC"))
    }

    @Test
    fun getContactsUsesSwaggerQueryAndParsesPage() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "items": [{
                    "id": 9,
                    "ownerWxid": "wxid_account",
                    "wxid": "wxid_friend",
                    "friendNo": "alice_001",
                    "nickname": "Alice",
                    "remarks": "VIP Alice",
                    "avatar": "https://cdn.example.com/a.png",
                    "labelIds": [1, 3],
                    "source": "phone",
                    "sourceExt": "expo",
                    "contactType": 1,
                    "wechatCreateTime": 1710000000,
                    "verifyFlag": 0,
                    "friendPermissionMask": 0,
                    "friendPermissionSynced": true,
                    "isFriend": 1,
                    "isBlocked": 0,
                    "isStarred": 1,
                    "isDeleted": false,
                    "lastInteractionTime": "2026-07-12T08:00:00Z",
                    "updatedAt": "2026-07-12T09:00:00Z"
                  }],
                  "totalCount": 1,
                  "page": 2,
                  "pageSize": 20
                }
                """.trimIndent()
            )
        )

        val page = ScrmApiClient(config, transport).getContacts(
            ScrmContactQuery(
                weChatId = "wxid_account",
                page = 2,
                pageSize = 20,
                search = "Alice Zhang",
                onlyFriends = true,
                includeProfile = true
            )
        )

        assertEquals(1, page.totalCount)
        assertEquals(2, page.page)
        assertEquals("wxid_friend", page.items.single().wxid)
        assertEquals("VIP Alice", page.items.single().displayName)
        assertEquals(listOf(1, 3), page.items.single().labelIds)
        assertEquals(
            "https://api.example.com/openapi/v1/contacts" +
                "?weChatId=wxid_account&page=2&pageSize=20&search=Alice%20Zhang" +
                "&includeDeleted=false&onlyFriends=true&includeProfile=true",
            transport.lastRequest?.url
        )
    }

    @Test
    fun getChatRoomsUsesSwaggerQueryAndParsesPage() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "items": [{
                    "id": 9,
                    "ownerWxid": "wxid_account",
                    "chatRoomId": "room_1@chatroom",
                    "name": "VIP Group",
                    "avatar": "https://cdn.example.com/room.png",
                    "ownerMemberWxid": "wxid_owner",
                    "memberCount": 32,
                    "groupStatus": 1,
                    "isDeleted": false,
                    "updatedAt": "2026-07-12T09:00:00Z"
                  }],
                  "totalCount": 1,
                  "page": 3,
                  "pageSize": 50
                }
                """.trimIndent()
            )
        )

        val page = ScrmApiClient(config, transport).getChatRooms(
            ScrmChatRoomQuery(
                weChatId = "wxid_account",
                page = 3,
                pageSize = 50,
                search = "VIP",
                includeDeleted = false
            )
        )

        assertEquals(1, page.totalCount)
        assertEquals(3, page.page)
        assertEquals("room_1@chatroom", page.items.single().chatRoomId)
        assertEquals("VIP Group", page.items.single().displayName)
        assertEquals("https://cdn.example.com/room.png", page.items.single().avatar)
        assertEquals(
            "https://api.example.com/openapi/v1/chatrooms" +
                "?weChatId=wxid_account&page=3&pageSize=50&search=VIP&includeDeleted=false",
            transport.lastRequest?.url
        )
    }

    @Test
    fun getChatRoomMembersUsesSwaggerPathAndParsesMemberAvatars() {
        val transport = RecordingTransport(
            ok(
                """
                {
                  "items": [{
                    "id": 11,
                    "chatRoomId": "room 1@chatroom",
                    "memberWxid": "wxid_member_1",
                    "nickname": "Member One",
                    "displayName": "One",
                    "avatar": "http://mmbiz.qpic.cn/member-one.png",
                    "remarks": "VIP",
                    "memberRole": 1,
                    "isOwner": false,
                    "isAdmin": true,
                    "updatedAt": "2026-07-12T09:00:00Z"
                  }],
                  "totalCount": 1,
                  "page": 1,
                  "pageSize": 9
                }
                """.trimIndent()
            )
        )

        val page = ScrmApiClient(config, transport).getChatRoomMembers(
            chatRoomId = "room 1@chatroom",
            query = ScrmChatRoomMemberQuery(
                weChatId = "wxid_account",
                page = 1,
                pageSize = 9,
                includeDeleted = false
            )
        )

        assertEquals(1, page.totalCount)
        assertEquals("wxid_member_1", page.items.single().memberWxid)
        assertEquals("One", page.items.single().displayNameValue)
        assertEquals("http://mmbiz.qpic.cn/member-one.png", page.items.single().avatar)
        assertEquals(
            "https://api.example.com/openapi/v1/chatrooms/room%201%40chatroom/members" +
                "?weChatId=wxid_account&page=1&pageSize=9&includeDeleted=false",
            transport.lastRequest?.url
        )
    }

    @Test
    fun friendOperationsPostAndDeleteTaskRequests() {
        val addTransport = RecordingTransport(ok("""{"taskId":101,"success":true,"message":"queued"}"""))

        val addTask = ScrmApiClient(config, addTransport).addFriend(
            ScrmAddFriendRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                friendWxid = "wxid_target",
                message = "hello",
                remark = "Alice",
                label = "VIP"
            )
        )

        val addRequest = requireNotNull(addTransport.lastRequest)
        val addBody = Json.parseToJsonElement(requireNotNull(addRequest.body)).jsonObject
        assertEquals(101L, addTask.taskId)
        assertEquals("POST", addRequest.method)
        assertEquals("https://api.example.com/openapi/v1/friends", addRequest.url)
        assertEquals("device-1", addBody.getValue("deviceUuid").jsonPrimitive.content)
        assertEquals("wxid_target", addBody.getValue("friendWxid").jsonPrimitive.content)
        assertEquals("VIP", addBody.getValue("label").jsonPrimitive.content)

        val deleteTransport = RecordingTransport(ok("""{"taskId":102,"success":true,"message":"queued"}"""))

        val deleteTask = ScrmApiClient(config, deleteTransport).deleteFriend(
            friendId = "wxid_target",
            deviceUuid = "device-1",
            weChatId = "wxid_account"
        )

        val deleteRequest = requireNotNull(deleteTransport.lastRequest)
        val deleteBody = Json.parseToJsonElement(requireNotNull(deleteRequest.body)).jsonObject
        assertEquals(102L, deleteTask.taskId)
        assertEquals("DELETE", deleteRequest.method)
        assertEquals(
            "https://api.example.com/openapi/v1/friends/wxid_target" +
                "?deviceUuid=device-1&weChatId=wxid_account",
            deleteRequest.url
        )
        assertEquals("wxid_target", deleteBody.getValue("friendId").jsonPrimitive.content)
        assertFalse(deleteRequest.toString().contains("wxid_target"))
    }

    @Test
    fun friendSearchAndPhoneAddUseSwaggerTaskRoutes() {
        val findTransport = RecordingTransport(ok("""{"taskId":104,"success":true,"message":"queued"}"""))
        val findTask = ScrmApiClient(config, findTransport).findFriend(
            ScrmFindContactRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                content = "alice_wechat"
            )
        )

        val findRequest = requireNotNull(findTransport.lastRequest)
        val findBody = Json.parseToJsonElement(requireNotNull(findRequest.body)).jsonObject
        assertEquals(104L, findTask.taskId)
        assertEquals("POST", findRequest.method)
        assertEquals("https://api.example.com/openapi/v1/friends/find", findRequest.url)
        assertEquals("alice_wechat", findBody.getValue("content").jsonPrimitive.content)

        val phoneTransport = RecordingTransport(ok("""{"taskId":105,"success":true,"message":"queued"}"""))
        val phoneTask = ScrmApiClient(config, phoneTransport).addFriendsByPhone(
            ScrmAddFriendsByPhoneRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                phones = listOf("13428811836"),
                message = "hello",
                remark = "Alice",
                label = "VIP"
            )
        )

        val phoneRequest = requireNotNull(phoneTransport.lastRequest)
        val phoneBody = Json.parseToJsonElement(requireNotNull(phoneRequest.body)).jsonObject
        assertEquals(105L, phoneTask.taskId)
        assertEquals("POST", phoneRequest.method)
        assertEquals("https://api.example.com/openapi/v1/friends/by-phone", phoneRequest.url)
        assertEquals("13428811836", phoneBody.getValue("phones").jsonArray.single().jsonPrimitive.content)
        assertEquals("VIP", phoneBody.getValue("label").jsonPrimitive.content)

        val verifyTransport = RecordingTransport(ok("""{"taskId":106,"success":true,"message":"queued"}"""))
        val verifyTask = ScrmApiClient(config, verifyTransport).sendFriendVerify(
            ScrmSendFriendVerifyRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                friendId = "wxid_found",
                message = "hello"
            )
        )

        val verifyRequest = requireNotNull(verifyTransport.lastRequest)
        val verifyBody = Json.parseToJsonElement(requireNotNull(verifyRequest.body)).jsonObject
        assertEquals(106L, verifyTask.taskId)
        assertEquals("POST", verifyRequest.method)
        assertEquals("https://api.example.com/openapi/v1/friends/verify", verifyRequest.url)
        assertEquals("wxid_found", verifyBody.getValue("friendId").jsonPrimitive.content)
    }

    @Test
    fun momentMaterialsUseSwaggerRoutesAndPayloads() {
        val listTransport = RecordingTransport(
            ok(
                """
                [{
                  "id": 71,
                  "tenantId": "tenant-a",
                  "name": "Launch Copy",
                  "category": "sales",
                  "status": 1,
                  "statusName": "enabled",
                  "contentHash": "c1",
                  "attachmentHash": "a1",
                  "commentHash": "m1",
                  "attachmentType": 2,
                  "attachmentCount": 1,
                  "extCommentCount": 2,
                  "sendSlow": true,
                  "createdBy": "tester",
                  "updatedBy": "tester",
                  "createdAt": "2026-07-14T10:00:00Z",
                  "updatedAt": "2026-07-14T11:00:00Z"
                }]
                """.trimIndent()
            )
        )

        val materials = ScrmApiClient(config, listTransport).getMomentMaterials(
            ScrmMomentMaterialQuery(
                tenantId = "tenant a",
                skip = 10,
                take = 20
            )
        )

        assertEquals(1, materials.size)
        assertEquals(71L, materials.single().id)
        assertEquals("Launch Copy", materials.single().displayName)
        assertEquals(
            "https://api.example.com/openapi/v1/moments/materials?tenantId=tenant%20a&skip=10&take=20",
            listTransport.lastRequest?.url
        )

        val createTransport = RecordingTransport(ok("""{"id":72,"name":"New Copy","status":1,"attachmentType":0,"attachmentCount":0,"extCommentCount":0,"sendSlow":false,"createdAt":"2026-07-14T10:00:00Z","updatedAt":"2026-07-14T10:00:00Z"}"""))
        val created = ScrmApiClient(config, createTransport).createMomentMaterial(
            ScrmMomentMaterialCreateRequest(
                name = "New Copy",
                category = "sales",
                content = "hello moments",
                tenantId = "tenant-a",
                enableImmediately = true
            )
        )
        val createBody = Json.parseToJsonElement(requireNotNull(createTransport.lastRequest?.body)).jsonObject
        assertEquals(72L, created.id)
        assertEquals("POST", createTransport.lastRequest?.method)
        assertEquals("https://api.example.com/openapi/v1/moments/materials", createTransport.lastRequest?.url)
        assertEquals("New Copy", createBody.getValue("name").jsonPrimitive.content)
        assertEquals("hello moments", createBody.getValue("content").jsonPrimitive.content)
        assertEquals("true", createBody.getValue("enableImmediately").jsonPrimitive.content)

        val detailTransport = RecordingTransport(ok("""{"id":71,"name":"Launch Copy","status":1,"attachmentType":2,"attachmentCount":1,"extCommentCount":0,"sendSlow":false,"template":{"content":"detail text"},"createdAt":"2026-07-14T10:00:00Z","updatedAt":"2026-07-14T11:00:00Z"}"""))
        val detail = ScrmApiClient(config, detailTransport).getMomentMaterialDetail(
            materialId = 71L,
            tenantId = "tenant-a"
        )
        assertEquals("detail text", detail.template?.content)
        assertEquals(
            "https://api.example.com/openapi/v1/moments/materials/71/detail?tenantId=tenant-a",
            detailTransport.lastRequest?.url
        )

        val copyTransport = RecordingTransport(ok("""{"id":73,"name":"Launch Copy 2","status":1,"attachmentType":2,"attachmentCount":1,"extCommentCount":0,"sendSlow":false,"createdAt":"2026-07-14T10:00:00Z","updatedAt":"2026-07-14T11:00:00Z"}"""))
        val copied = ScrmApiClient(config, copyTransport).copyMomentMaterial(
            materialId = 71L,
            request = ScrmMomentMaterialCopyRequest(
                name = "Launch Copy 2",
                enableImmediately = true
            )
        )
        val copyBody = Json.parseToJsonElement(requireNotNull(copyTransport.lastRequest?.body)).jsonObject
        assertEquals(73L, copied.id)
        assertEquals(
            "https://api.example.com/openapi/v1/moments/materials/71/copy",
            copyTransport.lastRequest?.url
        )
        assertEquals("Launch Copy 2", copyBody.getValue("name").jsonPrimitive.content)

        val archiveTransport = RecordingTransport(ok("""{"id":71,"name":"Launch Copy","status":2,"attachmentType":2,"attachmentCount":1,"extCommentCount":0,"sendSlow":false,"createdAt":"2026-07-14T10:00:00Z","updatedAt":"2026-07-14T11:00:00Z"}"""))
        val archived = ScrmApiClient(config, archiveTransport).archiveMomentMaterial(
            materialId = 71L,
            request = ScrmMomentMaterialControlRequest(reason = "expired")
        )
        val archiveBody = Json.parseToJsonElement(requireNotNull(archiveTransport.lastRequest?.body)).jsonObject
        assertEquals(2, archived.status)
        assertEquals(
            "https://api.example.com/openapi/v1/moments/materials/71/archive",
            archiveTransport.lastRequest?.url
        )
        assertEquals("expired", archiveBody.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun friendRequestsCanBeListedAndHandled() {
        val listTransport = RecordingTransport(
            ok(
                """
                [{
                  "id": 7,
                  "ownerWxid": "wxid_account",
                  "wechatAccountId": 3,
                  "requestWxid": "wxid_new",
                  "nickname": "New Friend",
                  "avatar": null,
                  "gender": 1,
                  "region": "CN",
                  "source": "search",
                  "requestMessage": "please add me",
                  "status": 0,
                  "requestTime": "2026-07-12T07:00:00Z",
                  "responseTime": "2026-07-12T07:05:00Z",
                  "responseMessage": null,
                  "createdAt": "2026-07-12T07:00:00Z",
                  "updatedAt": "2026-07-12T07:00:00Z"
                }]
                """.trimIndent()
            )
        )

        val requests = ScrmApiClient(config, listTransport).getFriendRequests(
            weChatId = "wxid_account",
            count = 20,
            pendingOnly = true
        )

        assertEquals("wxid_new", requests.single().requestWxid)
        assertEquals("New Friend", requests.single().displayName)
        assertEquals(
            "https://api.example.com/openapi/v1/friend-requests" +
                "?weChatId=wxid_account&count=20&pendingOnly=true",
            listTransport.lastRequest?.url
        )

        val handleTransport = RecordingTransport(ok("""{"taskId":103,"success":true,"message":"queued"}"""))
        val task = ScrmApiClient(config, handleTransport).handleFriendRequest(
            ScrmHandleFriendRequestRequest(
                deviceUuid = "device-1",
                weChatId = "wxid_account",
                friendId = "wxid_new",
                friendNick = "New Friend",
                remark = "New Friend",
                replyMsg = "accepted",
                operation = ScrmFriendRequestOperation.Accept
            )
        )

        val handleRequest = requireNotNull(handleTransport.lastRequest)
        val handleBody = Json.parseToJsonElement(requireNotNull(handleRequest.body)).jsonObject
        assertEquals(103L, task.taskId)
        assertEquals("POST", handleRequest.method)
        assertEquals("https://api.example.com/openapi/v1/friend-requests/handle", handleRequest.url)
        assertEquals("wxid_new", handleBody.getValue("friendId").jsonPrimitive.content)
        assertEquals("1", handleBody.getValue("operation").jsonPrimitive.content)
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
