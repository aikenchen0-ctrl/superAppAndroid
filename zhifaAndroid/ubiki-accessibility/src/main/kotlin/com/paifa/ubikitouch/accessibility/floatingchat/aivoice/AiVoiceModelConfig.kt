package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

data class AiVoiceModelOption(
    val id: String,
    val name: String,
    val domain: String,
    val status: String?,
    val taskTypes: List<String>
) {
    val displayName: String
        get() = name.ifBlank { id }

    val displayStatus: String
        get() = status?.takeIf { it.isNotBlank() } ?: "Available"

    fun isCompatibleWith(feature: AiVoiceFeature): Boolean {
        return when (feature) {
            AiVoiceFeature.RealtimeConversation,
            AiVoiceFeature.ClonedVoiceCall -> supportsRealtimeAudio()
            AiVoiceFeature.ClonedVoiceMessage,
            AiVoiceFeature.AiVoiceMessage -> supportsTextToSpeech()
            AiVoiceFeature.VoiceAgent -> supportsTextGeneration()
        }
    }

    private fun supportsTextGeneration(): Boolean {
        return domain.equals("LLM", ignoreCase = true) ||
            taskTypes.any { it.equals("TextGeneration", ignoreCase = true) }
    }

    private fun supportsTextToSpeech(): Boolean {
        val text = searchableText()
        return taskTypes.any { taskType ->
            taskType.equals("TextToSpeech", ignoreCase = true) ||
                taskType.equals("TTS", ignoreCase = true)
        } || text.contains("tts") ||
            text.contains("text-to-speech") ||
            text.contains("text_to_speech")
    }

    private fun supportsRealtimeAudio(): Boolean {
        val text = searchableText()
        return taskTypes.any { taskType ->
            taskType.equals("SpeechToSpeech", ignoreCase = true) ||
                taskType.equals("RealtimeAudio", ignoreCase = true) ||
                taskType.equals("RealtimeConversation", ignoreCase = true)
        } || text.contains("realtime-audio") ||
            text.contains("realtime_audio") ||
            text.contains("speech-to-speech") ||
            text.contains("speech_to_speech")
    }

    private fun searchableText(): String {
        return (id + " " + name + " " + domain + " " + taskTypes.joinToString(" ")).lowercase()
    }
}

data class AiVoiceModelConfigState(
    val apiKeyInput: String = "",
    val loading: Boolean = false,
    val models: List<AiVoiceModelOption> = emptyList(),
    val selectedModelIds: Map<AiVoiceFeature, String> = emptyMap(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    fun withApiKeyInput(value: String): AiVoiceModelConfigState {
        return copy(apiKeyInput = value, errorMessage = null)
    }

    fun loadingModels(): AiVoiceModelConfigState {
        return copy(loading = true, statusMessage = "正在获取模型列表", errorMessage = null)
    }

    fun withFetchedModels(value: List<AiVoiceModelOption>): AiVoiceModelConfigState {
        val normalizedModels = value.distinctBy { it.id }
        val nextSelections = AiVoiceFeature.entries.mapNotNull { feature ->
            val kept = selectedModelIds[feature]
                ?.takeIf { id -> normalizedModels.any { it.id == id && it.isCompatibleWith(feature) } }
            val selected = kept ?: normalizedModels.firstOrNull { it.isCompatibleWith(feature) }?.id
            selected?.let { feature to it }
        }.toMap()
        val matchedCount = AiVoiceFeature.entries.count { feature ->
            normalizedModels.any { it.isCompatibleWith(feature) }
        }
        return copy(
            loading = false,
            models = normalizedModels,
            selectedModelIds = nextSelections,
            statusMessage = "已获取 ${normalizedModels.size} 个模型；${matchedCount} 项语音类型已匹配模型",
            errorMessage = null
        )
    }

    fun withFetchError(message: String): AiVoiceModelConfigState {
        return copy(loading = false, errorMessage = message, statusMessage = null)
    }

    fun compatibleModels(feature: AiVoiceFeature): List<AiVoiceModelOption> {
        return models.filter { it.isCompatibleWith(feature) }
    }

    fun selectedModel(feature: AiVoiceFeature): AiVoiceModelOption? {
        val selectedId = selectedModelIds[feature] ?: return null
        return models.firstOrNull { it.id == selectedId }
    }

    fun selectedModelId(feature: AiVoiceFeature): String? = selectedModelIds[feature]

    fun selectModel(feature: AiVoiceFeature, modelId: String): AiVoiceModelConfigState {
        val model = models.firstOrNull { it.id == modelId && it.isCompatibleWith(feature) }
            ?: return withFetchError("所选模型不适用于${feature.title}")
        return copy(
            selectedModelIds = selectedModelIds + (feature to model.id),
            errorMessage = null
        )
    }

    fun missingFeatureLabels(): List<String> {
        return AiVoiceFeature.entries
            .filter { feature -> compatibleModels(feature).isEmpty() }
            .map { it.title }
    }
}

sealed interface AiVoiceModelConfigEvent {
    data class ApiKeyChanged(val value: String) : AiVoiceModelConfigEvent
    data object FetchModelsRequested : AiVoiceModelConfigEvent
    data class ModelSelected(
        val feature: AiVoiceFeature,
        val modelId: String
    ) : AiVoiceModelConfigEvent
}
