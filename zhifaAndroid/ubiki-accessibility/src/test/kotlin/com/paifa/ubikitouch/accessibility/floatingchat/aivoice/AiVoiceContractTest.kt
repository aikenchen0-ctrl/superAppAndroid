package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceContractTest {
    @Test
    fun `transcript updates append turns and replace the active partial turn`() {
        val first = emptyList<RealtimeTranscriptMessage>()
            .withTranscript(RealtimeSpeaker.User, "你好")
            .withTranscript(RealtimeSpeaker.User, "你好，帮我查天气")
            .withTranscript(RealtimeSpeaker.Assistant, "好的")

        assertEquals(
            listOf(
                RealtimeTranscriptMessage(RealtimeSpeaker.User, "你好，帮我查天气"),
                RealtimeTranscriptMessage(RealtimeSpeaker.Assistant, "好的")
            ),
            first
        )
    }

    @Test
    fun `call state changes preserve existing transcript messages`() {
        val messages = listOf(RealtimeTranscriptMessage(RealtimeSpeaker.User, "你好"))
        val state = AiVoiceState.RealtimeCall(RealtimeCallState.UserSpeaking, messages)

        assertEquals(
            state.copy(state = RealtimeCallState.AiResponding),
            reduceAiVoiceState(state, AiVoiceEvent.CallStateChanged(RealtimeCallState.AiResponding), false)
        )
    }

    @Test
    fun `assistant transcript deltas accumulate into the active message`() {
        val messages = emptyList<RealtimeTranscriptMessage>()
            .withTranscript(RealtimeSpeaker.Assistant, "广州今天")
            .withTranscript(RealtimeSpeaker.Assistant, "天气晴朗")
            .withTranscript(RealtimeSpeaker.Assistant, "。")

        assertEquals(
            listOf(RealtimeTranscriptMessage(RealtimeSpeaker.Assistant, "广州今天天气晴朗。")),
            messages
        )
    }

    @Test
    fun `streaming subtitle reveals one unicode character per tick`() {
        assertEquals("深", nextStreamingSubtitle("", "深圳天气"))
        assertEquals("深圳", nextStreamingSubtitle("深", "深圳天气"))
        assertEquals("深圳天气", nextStreamingSubtitle("深圳天气", "深圳天气"))
    }
    @Test
    fun menuExposesEveryConfirmedAiVoiceFeatureInProductOrder() {
        assertEquals(
            listOf(
                AiVoiceFeature.RealtimeConversation,
                AiVoiceFeature.ClonedVoiceMessage,
                AiVoiceFeature.ClonedVoiceCall,
                AiVoiceFeature.VoiceAgent,
                AiVoiceFeature.AiVoiceMessage
            ),
            AiVoiceFeature.entries
        )
    }

    @Test
    fun clonedFeatureWithoutVoiceProfileRequiresInlineProfileCreation() {
        val state = AiVoiceState.Menu
        val event = AiVoiceEvent.FeatureSelected(AiVoiceFeature.ClonedVoiceMessage)

        val next = reduceAiVoiceState(state, event, hasVoiceProfile = false)

        assertEquals(AiVoiceState.CreatingVoiceProfile(VoiceProfileStep.Consent), next)
    }

    @Test
    fun realtimeCallFailureStaysExplicit() {
        val state = AiVoiceState.RealtimeCall(RealtimeCallState.Connecting)

        val next = reduceAiVoiceState(
            state,
            AiVoiceEvent.CallFailed("WebSocket authentication failed"),
            hasVoiceProfile = true
        )

        assertTrue(next is AiVoiceState.Failed)
        assertEquals("WebSocket authentication failed", (next as AiVoiceState.Failed).message)
    }

    @Test
    fun generatedVoiceMessageReturnsPlayableAudioUrl() {
        val state = AiVoiceState.GeneratingMessage(
            feature = AiVoiceFeature.AiVoiceMessage,
            text = "hello",
            generating = true
        )

        val next = reduceAiVoiceState(
            state,
            AiVoiceEvent.VoiceMessageGenerated("file:///cache/doubao-voice.mp3"),
            hasVoiceProfile = false
        )

        assertEquals(
            state.copy(generating = false, generatedAudioUrl = "file:///cache/doubao-voice.mp3"),
            next
        )
    }
}
