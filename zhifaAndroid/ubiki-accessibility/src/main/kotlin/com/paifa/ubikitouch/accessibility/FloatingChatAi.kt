package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.content.SharedPreferences
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatInlineToken
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val AiPrefsName = "floating_chat_ai_config"
private const val AiPrefsBaseUrl = "base_url"
private const val AiPrefsApiKey = "api_key"
private const val AiPrefsModel = "model"
private const val AiPrefsSystemPrompt = "system_prompt"
private const val AiPrefsTemperature = "temperature"
private const val AiPrefsMaxTokens = "max_tokens"
private const val DefaultAiConnectTimeoutMs = 15_000
private const val DefaultAiReadTimeoutMs = 45_000
private const val DefaultAiMaxPromptMessages = 10

internal data class FloatingChatAiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = defaultFloatingChatAiSystemPrompt(),
    val temperature: Float = 0.4f,
    val maxTokens: Int = 220
)

internal val FloatingChatAiConfig.isConfigured: Boolean
    get() = baseUrl.trim().isNotEmpty() &&
        apiKey.trim().isNotEmpty() &&
        model.trim().isNotEmpty()

internal enum class FloatingChatAiDraftAction(val label: String) {
    Edit("\u7f16\u8f91\u8349\u7a3f"),
    Regenerate("\u91cd\u65b0\u751f\u6210"),
    Cancel("\u53d6\u6d88\u8349\u7a3f"),
    Send("\u53d1\u9001")
}

internal enum class FloatingChatBlinkInputAiAction {
    None,
    PredictReply,
    PolishInput
}

internal enum class FloatingChatBlinkInputStatusPhase {
    Recognized,
    Completed,
    Failed
}

internal fun floatingChatAiDraftActions(): List<FloatingChatAiDraftAction> {
    return listOf(
        FloatingChatAiDraftAction.Edit,
        FloatingChatAiDraftAction.Regenerate,
        FloatingChatAiDraftAction.Cancel,
        FloatingChatAiDraftAction.Send
    )
}

internal fun loadFloatingChatAiConfig(context: Context): FloatingChatAiConfig {
    val prefs = context.getSharedPreferences(AiPrefsName, Context.MODE_PRIVATE)
    return FloatingChatAiConfig(
        baseUrl = prefs.getString(AiPrefsBaseUrl, "") ?: "",
        apiKey = prefs.getString(AiPrefsApiKey, "") ?: "",
        model = prefs.getString(AiPrefsModel, "") ?: "",
        systemPrompt = prefs.getString(AiPrefsSystemPrompt, defaultFloatingChatAiSystemPrompt())
            ?: defaultFloatingChatAiSystemPrompt(),
        temperature = prefs.getFloat(AiPrefsTemperature, 0.4f),
        maxTokens = prefs.getInt(AiPrefsMaxTokens, 220)
    ).normalized()
}

internal fun saveFloatingChatAiConfig(context: Context, config: FloatingChatAiConfig) {
    context.getSharedPreferences(AiPrefsName, Context.MODE_PRIVATE)
        .edit()
        .putFloatingChatAiConfig(config.normalized())
        .apply()
}

internal fun floatingChatAiCompletionsEndpoint(config: FloatingChatAiConfig): String {
    val trimmed = config.baseUrl.trim().trimEnd('/')
    return when {
        trimmed.endsWith("/chat/completions", ignoreCase = true) -> trimmed
        trimmed.endsWith("/v1", ignoreCase = true) -> "$trimmed/chat/completions"
        else -> "$trimmed/v1/chat/completions"
    }
}

internal fun buildFloatingChatAiDraftPrompt(
    messages: List<FloatingChatMessage>,
    selectedAccountName: String,
    maxMessages: Int = DefaultAiMaxPromptMessages
): String {
    val safeLimit = maxMessages.coerceAtLeast(1)
    val lines = messages
        .asSequence()
        .filter { message -> message.kind == FloatingChatMessageKind.Normal }
        .filterNot { message -> message.presentation == FloatingChatMessagePresentation.System }
        .filter { message -> message.aiPromptContent().isNotBlank() }
        .toList()
        .takeLast(safeLimit)
        .joinToString(separator = "\n") { message ->
            "${message.senderName.ifBlank { if (message.fromMe) selectedAccountName else "Contact" }}: ${message.aiPromptContent()}"
        }

    return buildString {
        append("Current account: ")
        appendLine(selectedAccountName.ifBlank { "Me" })
        appendLine("Recent chat context:")
        appendLine(lines.ifBlank { "(no previous messages)" })
        appendLine("Generate one natural, concise Chinese reply draft for the current account.")
        append("Output only the draft text. Do not include explanations, quotes, labels, or markdown.")
    }
}

internal fun buildFloatingChatAiConfigTestPrompt(): String {
    return "ping. Reply with pong only."
}

internal fun buildFloatingChatAiInputPolishPrompt(inputText: String): String {
    return buildString {
        appendLine("请润色下面这段聊天输入，让它更自然、清楚、适合直接发送。")
        appendLine("保留原意，不要扩写成很长的内容。")
        appendLine("只输出润色后的文本，不要解释、不要加引号、不要加 markdown。")
        appendLine()
        appendLine("原文：")
        append(inputText.trim())
    }
}

internal fun blinkVoiceInputActionFor(
    eventType: String,
    inputText: String
): FloatingChatBlinkInputAiAction {
    val hasInput = inputText.isNotBlank()
    return when (eventType) {
        "DOUBLE_BLINK" -> if (hasInput) {
            FloatingChatBlinkInputAiAction.None
        } else {
            FloatingChatBlinkInputAiAction.PredictReply
        }
        "LONG_CLOSE" -> if (hasInput) {
            FloatingChatBlinkInputAiAction.PolishInput
        } else {
            FloatingChatBlinkInputAiAction.None
        }
        else -> FloatingChatBlinkInputAiAction.None
    }
}

internal fun blinkVoiceInputStatusMessageFor(
    eventType: String,
    action: FloatingChatBlinkInputAiAction,
    phase: FloatingChatBlinkInputStatusPhase
): String {
    if (phase == FloatingChatBlinkInputStatusPhase.Failed) {
        return "\u0041\u0049\u5904\u7406\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u914d\u7f6e\u6216\u7f51\u7edc"
    }
    if (phase == FloatingChatBlinkInputStatusPhase.Completed) {
        return when (action) {
            FloatingChatBlinkInputAiAction.PredictReply -> "\u9884\u6d4b\u56de\u590d\u5df2\u751f\u6210"
            FloatingChatBlinkInputAiAction.PolishInput -> "\u8f93\u5165\u5185\u5bb9\u5df2\u6da6\u8272"
            FloatingChatBlinkInputAiAction.None -> blinkVoiceInputRecognizedLabel(eventType)
        }
    }
    return when (action) {
        FloatingChatBlinkInputAiAction.PredictReply ->
            "\u8bc6\u522b\u5230\u53cc\u7728\uff0c\u6b63\u5728\u8fdb\u884c\u9884\u6d4b\u56de\u590d\u5185\u5bb9"
        FloatingChatBlinkInputAiAction.PolishInput ->
            "\u8bc6\u522b\u5230\u957f\u95ed\u773c\uff0c\u6b63\u5728\u8fdb\u884c\u8f93\u5165\u5185\u5bb9\u6da6\u8272"
        FloatingChatBlinkInputAiAction.None -> blinkVoiceInputRecognizedLabel(eventType)
    }
}

private fun blinkVoiceInputRecognizedLabel(eventType: String): String {
    return when (eventType) {
        "SINGLE_BLINK" -> "\u8bc6\u522b\u5230\u5355\u7728"
        "DOUBLE_BLINK" -> "\u8bc6\u522b\u5230\u53cc\u7728"
        "LONG_CLOSE" -> "\u8bc6\u522b\u5230\u957f\u95ed\u773c"
        else -> "\u8bc6\u522b\u5230\u773c\u775b\u72b6\u6001"
    }
}

internal fun buildFloatingChatAiChatCompletionsBody(
    config: FloatingChatAiConfig,
    draftPrompt: String
): String {
    val normalized = config.normalized()
    return buildString {
        append('{')
        append("\"model\":")
        append(jsonString(normalized.model))
        append(",\"messages\":[")
        append("{\"role\":\"system\",\"content\":")
        append(jsonString(normalized.systemPrompt.ifBlank { defaultFloatingChatAiSystemPrompt() }))
        append("},")
        append("{\"role\":\"user\",\"content\":")
        append(jsonString(draftPrompt))
        append("}],")
        append("\"temperature\":")
        append(normalized.temperature.toString())
        append(",\"max_tokens\":")
        append(normalized.maxTokens.toString())
        append('}')
    }
}

internal fun createFloatingChatAiDraftMessage(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    accountId: String,
    text: String,
    sequence: Int
): FloatingChatMessage {
    val threadContactId = when (selection) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> selection.contactId
    }
    val idPrefix = when (selection) {
        ChatThreadSelection.Group -> "local-ai-draft-group"
        is ChatThreadSelection.GroupChat -> "local-ai-draft-${selection.groupId}"
        is ChatThreadSelection.Private -> "local-ai-draft-${selection.contactId}"
    }
    val trimmedText = text.trim()
    return FloatingChatMessage(
        id = "$idPrefix-$accountId-$sequence",
        type = FloatingChatMessageType.MixedText,
        text = trimmedText,
        fromMe = true,
        senderName = conversation.accountContacts.firstOrNull { account -> account.id == accountId }?.name
            ?: conversation.accountName,
        time = "\u521a\u521a",
        kind = FloatingChatMessageKind.AiDraft,
        presentation = FloatingChatMessagePresentation.Bubble,
        connectionTarget = FloatingChatConnectionTarget.Account,
        connectionTargetId = accountId,
        threadContactId = threadContactId,
        inlineTokens = listOf(
            FloatingChatInlineToken(FloatingChatInlineTokenType.Ai, "AI"),
            FloatingChatInlineToken(FloatingChatInlineTokenType.Plain, " $trimmedText")
        )
    )
}

internal fun createFloatingChatAiLoadingDraftMessage(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    return createFloatingChatAiDraftMessage(
        conversation = conversation,
        selection = selection,
        accountId = accountId,
        text = floatingChatAiDraftLoadingText(),
        sequence = sequence
    )
}

internal fun floatingChatAiDraftLoadingText(): String {
    return "\u0041\u0049\u8349\u7a3f\u751f\u6210\u4e2d..."
}

internal fun aiDraftSendAlreadyHandled(
    draftId: String,
    sentDraftIds: Map<String, Boolean>
): Boolean {
    return sentDraftIds[draftId] == true
}

internal fun draftTextMessageFromAiDraft(
    draft: FloatingChatMessage
): FloatingChatMessage {
    return draft.copy(
        id = draft.id.replace("local-ai-draft", "local-ai-send"),
        type = FloatingChatMessageType.Text,
        kind = FloatingChatMessageKind.Normal,
        text = draft.text.trim(),
        inlineTokens = emptyList()
    )
}

internal class FloatingChatAiClient(
    private val connectTimeoutMs: Int = DefaultAiConnectTimeoutMs,
    private val readTimeoutMs: Int = DefaultAiReadTimeoutMs
) {
    fun generateDraft(
        config: FloatingChatAiConfig,
        draftPrompt: String
    ): String {
        require(config.isConfigured) { "AI config is incomplete." }

        val connection = URL(floatingChatAiCompletionsEndpoint(config)).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            connection.outputStream.use { output ->
                output.write(buildFloatingChatAiChatCompletionsBody(config, draftPrompt).toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseText = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: ""
                throw IllegalStateException("AI request failed: HTTP $statusCode ${errorText.take(240)}")
            }
            parseFloatingChatAiDraftResponse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    fun testConfig(config: FloatingChatAiConfig): String {
        return generateDraft(
            config = config,
            draftPrompt = buildFloatingChatAiConfigTestPrompt()
        )
    }
}

internal fun parseFloatingChatAiDraftResponse(responseText: String): String {
    val trimmed = responseText.trim()
    if (trimmed.isBlank()) {
        throw IllegalStateException("AI response is empty.")
    }
    val jsonLike = trimmed.startsWith("{") || trimmed.startsWith("[")
    if (!jsonLike) {
        parseServerSentEventAiContent(trimmed)?.let { return it }
        if (trimmed.hasServerSentEventMarkers() && trimmed.looksLikeAiChunkPayload()) {
            throw IllegalStateException("AI response missing draft content.")
        }
        return trimmed.trim('"').trim().ifBlank {
            throw IllegalStateException("AI response missing draft content.")
        }
    }
    firstJsonStringValue(
        json = trimmed,
        keys = listOf("content", "text", "message", "response", "result")
    )?.let { value ->
        if (value.looksLikeStructuredAiPayload()) {
            return parseFloatingChatAiDraftResponse(value)
        }
        return value
    }
    firstChoiceStringValue(trimmed)?.let { return it }
    firstJsonStringValue(json = trimmed, keys = listOf("data"))?.let { data ->
        if (data.looksLikeStructuredAiPayload()) {
            return parseFloatingChatAiDraftResponse(data)
        }
        return data
    }
    throw IllegalStateException("AI response missing draft content.")
}

private fun SharedPreferences.Editor.putFloatingChatAiConfig(
    config: FloatingChatAiConfig
): SharedPreferences.Editor {
    return putString(AiPrefsBaseUrl, config.baseUrl)
        .putString(AiPrefsApiKey, config.apiKey)
        .putString(AiPrefsModel, config.model)
        .putString(AiPrefsSystemPrompt, config.systemPrompt)
        .putFloat(AiPrefsTemperature, config.temperature)
        .putInt(AiPrefsMaxTokens, config.maxTokens)
}

private fun FloatingChatAiConfig.normalized(): FloatingChatAiConfig {
    return copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        systemPrompt = systemPrompt.trim().ifBlank { defaultFloatingChatAiSystemPrompt() },
        temperature = temperature.coerceIn(0f, 2f),
        maxTokens = maxTokens.coerceIn(32, 2048)
    )
}

private fun firstJsonStringValue(json: String, keys: List<String>): String? {
    keys.forEach { key ->
        val pattern = Regex(
            pattern = """"${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val value = pattern.find(json)?.groupValues?.getOrNull(1)?.let(::decodeJsonString)?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun firstChoiceStringValue(json: String): String? {
    val pattern = Regex(
        pattern = """"choices"\s*:\s*\[\s*"((?:\\.|[^"\\])*)"""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
    return pattern.find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::decodeJsonString)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun parseServerSentEventAiContent(response: String): String? {
    val dataLines = serverSentEventPayloads(response)
        .filter { payload -> payload.isNotBlank() && payload != "[DONE]" }
        .toList()
    if (dataLines.isEmpty()) return null

    val content = dataLines.mapNotNull { payload ->
        runCatching { parseFloatingChatAiDraftResponse(payload) }.getOrNull()
    }.joinToString(separator = "")
    return content.takeIf { it.isNotBlank() }
}

private fun serverSentEventPayloads(response: String): Sequence<String> = sequence {
    var awaitingPayloadLine = false
    response.lineSequence()
        .map { line -> line.trim() }
        .forEach { line ->
            if (line.isBlank()) return@forEach
            val dataIndex = line.indexOf("data:")
            if (dataIndex >= 0) {
                val payload = line.substring(dataIndex + "data:".length).trim()
                if (payload.isBlank()) {
                    awaitingPayloadLine = true
                } else {
                    awaitingPayloadLine = false
                    yield(payload)
                }
                return@forEach
            }
            if (awaitingPayloadLine) {
                awaitingPayloadLine = false
                yield(line)
            }
        }
}

private fun String.looksLikeStructuredAiPayload(): Boolean {
    val value = trim()
    return value.startsWith("{") ||
        value.startsWith("[") ||
        value.hasServerSentEventMarkers()
}

private fun String.hasServerSentEventMarkers(): Boolean {
    return lineSequence().any { line -> line.trim().contains("data:") }
}

private fun String.looksLikeAiChunkPayload(): Boolean {
    return contains("chat.completion.chunk") ||
        contains("\"choices\"") ||
        contains("\"usage\"")
}

private fun decodeJsonString(value: String): String {
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\' || index == value.lastIndex) {
            builder.append(char)
            index += 1
            continue
        }
        val escaped = value[index + 1]
        when (escaped) {
            '"', '\\', '/' -> builder.append(escaped)
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'u' -> {
                val hex = value.substring(index + 2, (index + 6).coerceAtMost(value.length))
                val decoded = hex.takeIf { it.length == 4 }?.toIntOrNull(16)?.toChar()
                if (decoded != null) {
                    builder.append(decoded)
                    index += 4
                } else {
                    builder.append("\\u")
                }
            }
            else -> builder.append(escaped)
        }
        index += 2
    }
    return builder.toString()
}

private fun FloatingChatMessage.aiPromptContent(): String {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> "[image] ${fileName ?: thumbnailUrl ?: resourceUrl ?: text}"
        FloatingChatMessageType.VideoPreview -> "[video] ${fileName ?: thumbnailUrl ?: resourceUrl ?: text}"
        FloatingChatMessageType.Location -> {
            listOfNotNull(locationTitle, locationAddress, resourceUrl)
                .joinToString(separator = " ")
                .ifBlank { text }
                .let { "[location] $it" }
        }
        FloatingChatMessageType.FilePreview -> {
            val format = fileFormat.aiPromptLabel()
            "[file$format] ${fileName ?: text}"
        }
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.InlineContact -> "[contact card] ${cardName ?: text}"
        FloatingChatMessageType.MiniProgramLink -> "[mini program] ${appName ?: text}"
        FloatingChatMessageType.Voice -> "[voice] ${detail ?: text}"
        FloatingChatMessageType.InlineLocation -> "[location] ${locationTitle ?: text}"
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote -> inlineTokens
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "") { token -> token.text }
            ?.ifBlank { text }
            ?: text
    }.replace(Regex("\\s+"), " ").trim()
}

private fun FloatingChatFileFormat?.aiPromptLabel(): String {
    return this?.label?.lowercase(Locale.ROOT)?.let { " $it" }.orEmpty()
}

private fun jsonString(value: String): String {
    return buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}

private fun defaultFloatingChatAiSystemPrompt(): String {
    return "\u4f60\u662f\u804a\u5929\u56de\u590d\u52a9\u624b\uff0c\u53ea\u8f93\u51fa\u4e00\u6761\u81ea\u7136\u3001\u7b80\u77ed\u3001\u53ef\u76f4\u63a5\u53d1\u9001\u7684\u4e2d\u6587\u56de\u590d\uff0c\u4e0d\u8981\u89e3\u91ca\u3002"
}
