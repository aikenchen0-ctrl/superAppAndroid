package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatAiContractTest {
    @Test
    fun defaultAiConfigUsesBundledOpenAiCompatibleProvider() {
        val config = defaultFloatingChatAiConfig()

        assertEquals("https://cc2.cx/v1", config.baseUrl)
        assertEquals("sk-jwdArLBwIENRVm6itUAfMMqIVAdWN6J6CbGWstNknvtRmurk", config.apiKey)
        assertEquals("gpt-5.6-luna", config.model)
        assertTrue(config.isConfigured)
    }

    @Test
    fun defaultAiConfigReplacesBlankAndLegacyUiDefaultsButKeepsCustomValues() {
        assertEquals("https://cc2.cx/v1", floatingChatAiBaseUrlOrDefault(""))
        assertEquals("https://cc2.cx/v1", floatingChatAiBaseUrlOrDefault("https://api.openai.com/v1"))
        assertEquals("https://custom.example.com/v1", floatingChatAiBaseUrlOrDefault(" https://custom.example.com/v1 "))

        assertEquals("sk-jwdArLBwIENRVm6itUAfMMqIVAdWN6J6CbGWstNknvtRmurk", floatingChatAiApiKeyOrDefault(""))
        assertEquals("sk-custom", floatingChatAiApiKeyOrDefault(" sk-custom "))

        assertEquals("gpt-5.6-luna", floatingChatAiModelOrDefault(""))
        assertEquals("gpt-5.6-luna", floatingChatAiModelOrDefault("gpt-4.1-mini"))
        assertEquals("custom-model", floatingChatAiModelOrDefault(" custom-model "))
    }

    @Test
    fun aiConfigRequiresBaseUrlApiKeyAndModel() {
        assertFalse(FloatingChatAiConfig().isConfigured)
        assertFalse(
            FloatingChatAiConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "",
                model = "gpt-test"
            ).isConfigured
        )
        assertFalse(
            FloatingChatAiConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                model = ""
            ).isConfigured
        )
        assertTrue(
            FloatingChatAiConfig(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                model = "gpt-test"
            ).isConfigured
        )
    }

    @Test
    fun aiEndpointUsesOpenAiCompatibleChatCompletions() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            floatingChatAiCompletionsEndpoint(
                FloatingChatAiConfig(
                    baseUrl = "https://api.example.com/v1",
                    apiKey = "sk-test",
                    model = "gpt-test"
                )
            )
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            floatingChatAiCompletionsEndpoint(
                FloatingChatAiConfig(
                    baseUrl = "https://api.example.com",
                    apiKey = "sk-test",
                    model = "gpt-test"
                )
            )
        )
    }

    @Test
    fun aiPromptUsesLatestNormalMessagesOnly() {
        val messages = listOf(
            baseMessage(id = "old", senderName = "A", text = "old message"),
            baseMessage(id = "system", senderName = "system", text = "system notice").copy(
                kind = FloatingChatMessageKind.System,
                presentation = FloatingChatMessagePresentation.System
            ),
            baseMessage(id = "draft", senderName = "me", text = "discarded candidate").copy(
                kind = FloatingChatMessageKind.AiDraft
            ),
            baseMessage(id = "latest-1", senderName = "B", text = "latest one"),
            baseMessage(id = "latest-2", senderName = "me", text = "latest two", fromMe = true),
            baseMessage(id = "latest-3", senderName = "C", text = "latest three")
        )

        val prompt = buildFloatingChatAiDraftPrompt(
            messages = messages,
            selectedAccountName = "me",
            maxMessages = 3
        )

        assertFalse(prompt.contains("old message"))
        assertFalse(prompt.contains("system notice"))
        assertFalse(prompt.contains("discarded candidate"))
        assertTrue(prompt.contains("B: latest one"))
        assertTrue(prompt.contains("me: latest two"))
        assertTrue(prompt.contains("C: latest three"))
        assertTrue(prompt.contains("me"))
    }

    @Test
    fun aiRequestBodyContainsSystemPromptAndDraftContext() {
        val config = FloatingChatAiConfig(
            baseUrl = "https://api.example.com/v1",
            apiKey = "sk-test",
            model = "gpt-test",
            systemPrompt = "Only output the reply draft.",
            temperature = 0.2f,
            maxTokens = 120
        )
        val body = buildFloatingChatAiChatCompletionsBody(
            config = config,
            draftPrompt = "A: ping\nme: pong"
        )

        assertTrue(body.contains("\"model\":\"gpt-test\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("Only output the reply draft."))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("A: ping"))
        assertTrue(body.contains("\"temperature\":0.2"))
        assertTrue(body.contains("\"max_tokens\":120"))
    }

    @Test
    fun aiResponseParserSupportsProxyDataString() {
        assertEquals(
            "pong",
            parseFloatingChatAiDraftResponse("""{"data":"pong"}""")
        )
        assertEquals(
            "pong",
            parseFloatingChatAiDraftResponse("""{"choices":["pong"]}""")
        )
        assertEquals(
            "pong",
            parseFloatingChatAiDraftResponse("""{"choices":[{"message":"pong"}]}""")
        )
        assertEquals(
            "你好，可以这样回复。",
            parseFloatingChatAiDraftResponse(
                """
                data: {"id":"","object":"chat.completion.chunk","choices":[{"delta":{"content":"你好，"}}]}

                data: {"id":"","object":"chat.completion.chunk","choices":[{"delta":{"content":"可以这样回复。"}}]}

                data: [DONE]
                """.trimIndent()
            )
        )
        assertEquals(
            "测试通过",
            parseFloatingChatAiDraftResponse(
                """{"data":"{\"id\":\"\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"测试通过\"}}]}"}"""
            )
        )
    }

    @Test
    fun aiResponseParserUnwrapsNestedSseChunkContentInsteadOfReturningRawData() {
        val nestedSse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "data: {\"id\":\"\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"售后专员，这条我已经标记，晚上给你最终确认。\"}}]}\n\ndata: [DONE]"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = parseFloatingChatAiDraftResponse(nestedSse)

        assertEquals("售后专员，这条我已经标记，晚上给你最终确认。", parsed)
        assertFalse(parsed.startsWith("data:"))
        assertFalse(parsed.contains("chat.completion.chunk"))
    }

    @Test
    fun aiResponseParserUnwrapsPrefixedNestedSseAndSkipsEmptyChunks() {
        val prefixedNestedSse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "AI data:\n{\"id\":\"\",\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{\"prompt_tokens\":308}}\n\nAI data:\n{\"id\":\"\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"reply ready\"}}]}\n\nAI data: [DONE]"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = parseFloatingChatAiDraftResponse(prefixedNestedSse)

        assertEquals("reply ready", parsed)
        assertFalse(parsed.contains("chat.completion.chunk"))
        assertFalse(parsed.contains("\"usage\""))
    }

    @Test
    fun aiResponseParserRejectsPrefixedChunkPayloadWithoutDraftText() {
        val emptyChunkOnly = """
            {
              "choices": [
                {
                  "message": {
                    "content": "AI data:\n{\"id\":\"\",\"object\":\"chat.completion.chunk\",\"choices\":[],\"usage\":{\"prompt_tokens\":308}}"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = runCatching { parseFloatingChatAiDraftResponse(emptyChunkOnly) }

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("chat.completion.chunk"))
    }

    @Test
    fun aiConfigTestPromptIsIndependentFromChatHistory() {
        val prompt = buildFloatingChatAiConfigTestPrompt()

        assertTrue(prompt.contains("ping"))
        assertFalse(prompt.contains("Recent chat context"))
    }

    @Test
    fun aiInputPolishPromptOnlyRequestsPolishedInputText() {
        val prompt = buildFloatingChatAiInputPolishPrompt("今晚八点我晚点到")

        assertTrue(prompt.contains("今晚八点我晚点到"))
        assertTrue(prompt.contains("润色"))
        assertTrue(prompt.contains("只输出"))
        assertFalse(prompt.contains("Recent chat context"))
    }

    @Test
    fun blinkVoiceInputActionsUseDoubleBlinkForBlankPredictionAndLongCloseForPolish() {
        assertEquals(
            FloatingChatBlinkInputAiAction.PredictReply,
            blinkVoiceInputActionFor(eventType = "DOUBLE_BLINK", inputText = "")
        )
        assertEquals(
            FloatingChatBlinkInputAiAction.PredictReply,
            blinkVoiceInputActionFor(eventType = "DOUBLE_BLINK", inputText = "   ")
        )
        assertEquals(
            FloatingChatBlinkInputAiAction.None,
            blinkVoiceInputActionFor(eventType = "DOUBLE_BLINK", inputText = "已经有内容")
        )
        assertEquals(
            FloatingChatBlinkInputAiAction.PolishInput,
            blinkVoiceInputActionFor(eventType = "LONG_CLOSE", inputText = "帮我改顺一点")
        )
        assertEquals(
            FloatingChatBlinkInputAiAction.None,
            blinkVoiceInputActionFor(eventType = "LONG_CLOSE", inputText = "")
        )
        assertEquals(
            FloatingChatBlinkInputAiAction.None,
            blinkVoiceInputActionFor(eventType = "SINGLE_BLINK", inputText = "")
        )
    }

    @Test
    fun blinkVoiceInputStatusExplainsRecognizedEventAndAiWork() {
        assertEquals(
            "识别到双眨，正在进行预测回复内容",
            blinkVoiceInputStatusMessageFor(
                eventType = "DOUBLE_BLINK",
                action = FloatingChatBlinkInputAiAction.PredictReply,
                phase = FloatingChatBlinkInputStatusPhase.Recognized
            )
        )
        assertEquals(
            "识别到长闭眼，正在进行输入内容润色",
            blinkVoiceInputStatusMessageFor(
                eventType = "LONG_CLOSE",
                action = FloatingChatBlinkInputAiAction.PolishInput,
                phase = FloatingChatBlinkInputStatusPhase.Recognized
            )
        )
        assertEquals(
            "预测回复已生成",
            blinkVoiceInputStatusMessageFor(
                eventType = "DOUBLE_BLINK",
                action = FloatingChatBlinkInputAiAction.PredictReply,
                phase = FloatingChatBlinkInputStatusPhase.Completed
            )
        )
        assertEquals(
            "AI处理失败，请检查配置或网络",
            blinkVoiceInputStatusMessageFor(
                eventType = "DOUBLE_BLINK",
                action = FloatingChatBlinkInputAiAction.PredictReply,
                phase = FloatingChatBlinkInputStatusPhase.Failed
            )
        )
    }

    @Test
    fun aiDraftMessageTargetsCurrentAccountAndThread() {
        val message = createFloatingChatAiDraftMessage(
            conversation = FloatingChatPrototype.sampleConversation(),
            selection = ChatThreadSelection.Private("li-si"),
            accountId = "account-main",
            text = "I can send it later.",
            sequence = 7
        )

        assertEquals(FloatingChatMessageKind.AiDraft, message.kind)
        assertEquals(FloatingChatMessageType.MixedText, message.type)
        assertEquals(FloatingChatMessagePresentation.Bubble, message.presentation)
        assertEquals(FloatingChatConnectionTarget.Account, message.connectionTarget)
        assertEquals("account-main", message.connectionTargetId)
        assertEquals("li-si", message.threadContactId)
        assertEquals("I can send it later.", message.text)
        assertEquals(FloatingChatInlineTokenType.Ai, message.inlineTokens.first().type)
    }

    @Test
    fun aiLoadingDraftIsVisibleImmediately() {
        val message = createFloatingChatAiLoadingDraftMessage(
            conversation = FloatingChatPrototype.sampleConversation(),
            selection = ChatThreadSelection.Private("li-si"),
            accountId = "account-main",
            sequence = 8
        )

        assertEquals(FloatingChatMessageKind.AiDraft, message.kind)
        assertEquals(FloatingChatMessageType.MixedText, message.type)
        assertEquals("\u0041\u0049\u8349\u7a3f\u751f\u6210\u4e2d...", message.text)
        assertEquals(FloatingChatInlineTokenType.Ai, message.inlineTokens.first().type)
    }

    @Test
    fun aiDraftSendIsIdempotentForSameDraftId() {
        assertFalse(aiDraftSendAlreadyHandled("draft-1", emptyMap()))
        assertTrue(aiDraftSendAlreadyHandled("draft-1", mapOf("draft-1" to true)))
    }

    @Test
    fun aiDraftSendPassesNormalTextThroughOutgoingPreparation() {
        val draft = createFloatingChatAiDraftMessage(
            conversation = FloatingChatPrototype.sampleConversation(),
            selection = ChatThreadSelection.Private("li-si"),
            accountId = "account-main",
            text = "  reply ready  ",
            sequence = 9
        )
        var preparedThreadId: String? = null

        val prepared = preparedAiDraftTextMessageForSend(
            draft = draft,
            messageId = "sent-draft-1",
            time = "now",
            threadId = "private:li-si",
            prepareOutgoingMessage = { message, threadId ->
                preparedThreadId = threadId
                message.copy(
                    sendState = FloatingChatSendState.Queued,
                    clientRequestId = "client-1"
                )
            }
        )

        assertEquals("private:li-si", preparedThreadId)
        assertEquals("sent-draft-1", prepared.id)
        assertEquals("reply ready", prepared.text)
        assertEquals(FloatingChatMessageType.Text, prepared.type)
        assertEquals(FloatingChatMessageKind.Normal, prepared.kind)
        assertEquals(emptyList<com.paifa.ubikitouch.core.model.FloatingChatInlineToken>(), prepared.inlineTokens)
        assertEquals(FloatingChatSendState.Queued, prepared.sendState)
        assertEquals("client-1", prepared.clientRequestId)
    }

    @Test
    fun aiDraftActionsMatchRequestedOperations() {
        assertEquals(
            listOf(
                "\u7f16\u8f91\u8349\u7a3f",
                "\u91cd\u65b0\u751f\u6210",
                "\u53d6\u6d88\u8349\u7a3f",
                "\u53d1\u9001"
            ),
            floatingChatAiDraftActions().map { it.label }
        )
    }

    private fun baseMessage(
        id: String,
        senderName: String,
        text: String,
        fromMe: Boolean = false
    ): FloatingChatMessage {
        return FloatingChatMessage(
            id = id,
            type = FloatingChatMessageType.Text,
            text = text,
            fromMe = fromMe,
            senderName = senderName,
            time = "10:00"
        )
    }
}
