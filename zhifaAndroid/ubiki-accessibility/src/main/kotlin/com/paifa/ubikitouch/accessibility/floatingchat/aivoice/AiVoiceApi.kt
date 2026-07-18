package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import kotlinx.serialization.Serializable

interface AiVoiceApi {
    suspend fun createVoiceProfile(sampleAudioPath: String): VoiceProfileRemote
    suspend fun generateVoice(request: VoiceGenerationRequest): GeneratedVoiceRemote
    suspend fun createVoiceAgent(request: VoiceAgentRequest): VoiceAgentRemote
}

@Serializable
data class VoiceProfileRemote(
    val id: String,
    val displayName: String
)

@Serializable
data class VoiceGenerationRequest(
    val text: String,
    val voiceProfileId: String?
)

@Serializable
data class GeneratedVoiceRemote(
    val audioUrl: String,
    val durationMs: Int
)

@Serializable
data class VoiceAgentRequest(
    val name: String,
    val roleDescription: String,
    val voiceProfileId: String?,
    val allowedContactIds: Set<String>
)

@Serializable
data class VoiceAgentRemote(
    val id: String,
    val name: String
)

class MissingAiVoiceApiConfigException(message: String) : IllegalStateException(message)

fun requireAiVoiceApiConfig(config: AiVoiceApiConfig) {
    config.validationError()?.let(::throwMissingAiVoiceApiConfig)
}

private fun throwMissingAiVoiceApiConfig(message: String): Nothing {
    throw MissingAiVoiceApiConfigException(message)
}
