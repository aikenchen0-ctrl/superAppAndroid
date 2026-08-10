package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

enum class AiVoiceFeature(val title: String, val description: String) {
    RealtimeConversation("实时对话", "直接与 AI 连续通话，可随时打断"),
    ClonedVoiceMessage("克隆语音", "输入文字，用我的声音生成并发送"),
    ClonedVoiceCall("克隆音色通话", "使用我的音色与 AI 实时通话"),
    VoiceAgent("声音智能体", "选择、创建或配置声音智能体"),
    AiVoiceMessage("AI语音消息", "把文字生成为普通 AI 音色语音")
}

enum class VoiceProfileStep {
    Consent,
    Recording,
    Creating,
    Completed
}

enum class RealtimeCallState {
    Connecting,
    Listening,
    UserSpeaking,
    AiResponding,
    Interrupted,
    Reconnecting
}

data class RealtimeTranscriptMessage(
    val speaker: RealtimeSpeaker,
    val text: String
)

internal fun List<RealtimeTranscriptMessage>.withTranscript(
    speaker: RealtimeSpeaker,
    text: String
): List<RealtimeTranscriptMessage> {
    if (text.isBlank()) return this
    val previous = lastOrNull()
    if (previous?.speaker != speaker) return this + RealtimeTranscriptMessage(speaker, text)
    val updatedText = when (speaker) {
        RealtimeSpeaker.User -> text
        RealtimeSpeaker.Assistant -> previous.text + text
    }
    return dropLast(1) + previous.copy(text = updatedText)
}

internal fun nextStreamingSubtitle(current: String, target: String): String {
    if (current == target) return current
    if (!target.startsWith(current)) return target.substring(0, target.offsetByCodePoints(0, 1))
    val nextEnd = target.offsetByCodePoints(current.length, 1)
    return target.substring(0, nextEnd)
}

sealed interface AiVoiceState {
    data object Menu : AiVoiceState
    data class CreatingVoiceProfile(
        val step: VoiceProfileStep,
        val speakerId: String = "",
        val isRecording: Boolean = false,
        val recordedAudioPath: String? = null,
        val statusMessage: String? = null
    ) : AiVoiceState
    data class GeneratingMessage(
        val feature: AiVoiceFeature,
        val text: String = "",
        val generating: Boolean = false,
        val generatedAudioUrl: String? = null
    ) : AiVoiceState
    data class RealtimeCall(
        val state: RealtimeCallState,
        val messages: List<RealtimeTranscriptMessage> = emptyList()
    ) : AiVoiceState
    data object VoiceAgents : AiVoiceState
    data class Failed(val message: String) : AiVoiceState
}

sealed interface AiVoiceEvent {
    data class FeatureSelected(val feature: AiVoiceFeature) : AiVoiceEvent
    data class CallStateChanged(val state: RealtimeCallState) : AiVoiceEvent
    data class CallFailed(val message: String) : AiVoiceEvent
    data class MessageTextChanged(val value: String) : AiVoiceEvent
    data class VoiceMessageGenerated(val audioUrl: String) : AiVoiceEvent
    data object PreviewGeneratedVoice : AiVoiceEvent
    data object SendGeneratedVoice : AiVoiceEvent
    data object GenerateMessageRequested : AiVoiceEvent
    data object VoiceProfileConsentAccepted : AiVoiceEvent
    data object VoiceProfileRecordingStarted : AiVoiceEvent
    data object VoiceProfileRecordingStopped : AiVoiceEvent
    data class VoiceProfileSpeakerIdChanged(val value: String) : AiVoiceEvent
    data class VoiceProfileRecordingCompleted(val path: String) : AiVoiceEvent
    data object VoiceProfileSubmitRequested : AiVoiceEvent
    data object RetryRequested : AiVoiceEvent
    data object CallEnded : AiVoiceEvent
    data object VoiceProfileCreated : AiVoiceEvent
    data object BackRequested : AiVoiceEvent
}

fun reduceAiVoiceState(
    state: AiVoiceState,
    event: AiVoiceEvent,
    hasVoiceProfile: Boolean
): AiVoiceState {
    return when (event) {
        is AiVoiceEvent.FeatureSelected -> when (event.feature) {
            AiVoiceFeature.RealtimeConversation -> AiVoiceState.RealtimeCall(RealtimeCallState.Connecting)
            AiVoiceFeature.ClonedVoiceMessage,
            AiVoiceFeature.ClonedVoiceCall -> {
                if (hasVoiceProfile) {
                    if (event.feature == AiVoiceFeature.ClonedVoiceCall) {
                        AiVoiceState.RealtimeCall(RealtimeCallState.Connecting)
                    } else {
                        AiVoiceState.GeneratingMessage(event.feature)
                    }
                } else {
                    AiVoiceState.CreatingVoiceProfile(VoiceProfileStep.Consent)
                }
            }
            AiVoiceFeature.VoiceAgent -> AiVoiceState.VoiceAgents
            AiVoiceFeature.AiVoiceMessage -> AiVoiceState.GeneratingMessage(event.feature)
        }
        is AiVoiceEvent.CallStateChanged -> when (state) {
            is AiVoiceState.RealtimeCall -> state.copy(state = event.state)
            else -> AiVoiceState.RealtimeCall(event.state)
        }
        is AiVoiceEvent.CallFailed -> AiVoiceState.Failed(event.message)
        is AiVoiceEvent.MessageTextChanged -> when (state) {
            is AiVoiceState.GeneratingMessage -> state.copy(text = event.value, generatedAudioUrl = null)
            else -> state
        }
        is AiVoiceEvent.VoiceMessageGenerated -> when (state) {
            is AiVoiceState.GeneratingMessage -> state.copy(
                generating = false,
                generatedAudioUrl = event.audioUrl
            )
            else -> state
        }
        AiVoiceEvent.PreviewGeneratedVoice,
        AiVoiceEvent.SendGeneratedVoice -> state
        AiVoiceEvent.GenerateMessageRequested -> when (state) {
            is AiVoiceState.GeneratingMessage -> state.copy(generating = true)
            else -> state
        }
        AiVoiceEvent.VoiceProfileConsentAccepted -> AiVoiceState.CreatingVoiceProfile(VoiceProfileStep.Recording)
        AiVoiceEvent.VoiceProfileRecordingStarted -> (state as? AiVoiceState.CreatingVoiceProfile)?.copy(isRecording = true) ?: state
        AiVoiceEvent.VoiceProfileRecordingStopped -> (state as? AiVoiceState.CreatingVoiceProfile)?.copy(isRecording = false) ?: state
        is AiVoiceEvent.VoiceProfileSpeakerIdChanged -> (state as? AiVoiceState.CreatingVoiceProfile)?.copy(speakerId = event.value) ?: state
        is AiVoiceEvent.VoiceProfileRecordingCompleted -> (state as? AiVoiceState.CreatingVoiceProfile)?.copy(
            isRecording = false,
            recordedAudioPath = event.path,
            statusMessage = "声音样本已录制，可以提交训练"
        ) ?: state
        AiVoiceEvent.VoiceProfileSubmitRequested -> (state as? AiVoiceState.CreatingVoiceProfile)?.copy(
            step = VoiceProfileStep.Creating,
            statusMessage = "正在上传并训练音色"
        ) ?: state
        AiVoiceEvent.RetryRequested -> AiVoiceState.Menu
        AiVoiceEvent.CallEnded -> AiVoiceState.Menu
        AiVoiceEvent.VoiceProfileCreated -> when (state) {
            is AiVoiceState.CreatingVoiceProfile -> AiVoiceState.Menu
            else -> state
        }
        AiVoiceEvent.BackRequested -> AiVoiceState.Menu
    }
}
