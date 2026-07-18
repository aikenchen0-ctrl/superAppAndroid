package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

enum class AiVoiceCapabilityStatus(val label: String) {
    NotChecked("待验证"), Verifying("验证中"), Available("可用"), NeedsSetup("需先创建音色"), Unavailable("不可用")
}

data class AiVoiceCapabilityConfigState(
    val apiKeyInput: String = "",
    val appIdInput: String = "",
    val accessTokenInput: String = "",
    val loading: Boolean = false,
    val statuses: Map<AiVoiceFeature, AiVoiceCapabilityStatus> = emptyMap(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    fun withApiKeyInput(value: String) = copy(apiKeyInput = value, statusMessage = null, errorMessage = null)
    fun withAppIdInput(value: String) = copy(appIdInput = value, statusMessage = null, errorMessage = null)
    fun withAccessTokenInput(value: String) = copy(accessTokenInput = value, statusMessage = null, errorMessage = null)

    fun verifyingCapabilities() = copy(
        loading = true,
        statuses = AiVoiceFeature.entries.associateWith { AiVoiceCapabilityStatus.Verifying },
        statusMessage = "正在验证豆包语音能力",
        errorMessage = null
    )

    fun withTtsVerificationSucceeded() = copy(
        loading = false,
        statuses = AiVoiceFeature.entries.associateWith { feature ->
            if (feature == AiVoiceFeature.AiVoiceMessage) AiVoiceCapabilityStatus.Available
            else AiVoiceCapabilityStatus.NotChecked
        },
        statusMessage = "API Key 有效；语音合成可用",
        errorMessage = null
    )

    fun withVerificationError(message: String) = copy(
        loading = false,
        statuses = AiVoiceFeature.entries.associateWith { feature ->
            if (feature == AiVoiceFeature.AiVoiceMessage) AiVoiceCapabilityStatus.Unavailable
            else AiVoiceCapabilityStatus.NotChecked
        },
        statusMessage = null,
        errorMessage = message
    )

    fun withProbeResults(ttsAvailable: Boolean, realtimeAvailable: Boolean) = copy(
        loading = false,
        statuses = mapOf(
            AiVoiceFeature.AiVoiceMessage to if (ttsAvailable) AiVoiceCapabilityStatus.Available else AiVoiceCapabilityStatus.Unavailable,
            AiVoiceFeature.RealtimeConversation to if (realtimeAvailable) AiVoiceCapabilityStatus.Available else AiVoiceCapabilityStatus.Unavailable,
            AiVoiceFeature.VoiceAgent to if (realtimeAvailable) AiVoiceCapabilityStatus.Available else AiVoiceCapabilityStatus.Unavailable,
            AiVoiceFeature.ClonedVoiceMessage to AiVoiceCapabilityStatus.NeedsSetup,
            AiVoiceFeature.ClonedVoiceCall to AiVoiceCapabilityStatus.NeedsSetup
        ),
        statusMessage = "全部能力测试完成",
        errorMessage = null
    )

    fun statusFor(feature: AiVoiceFeature) = statuses[feature] ?: AiVoiceCapabilityStatus.NotChecked
}

sealed interface AiVoiceCapabilityConfigEvent {
    data class ApiKeyChanged(val value: String) : AiVoiceCapabilityConfigEvent
    data class AppIdChanged(val value: String) : AiVoiceCapabilityConfigEvent
    data class AccessTokenChanged(val value: String) : AiVoiceCapabilityConfigEvent
    data object VerifyCapabilitiesRequested : AiVoiceCapabilityConfigEvent
}
