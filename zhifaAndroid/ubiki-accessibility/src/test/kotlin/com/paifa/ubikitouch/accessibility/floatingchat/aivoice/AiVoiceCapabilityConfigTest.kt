package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceCapabilityConfigTest {
    @Test
    fun successfulTtsProbeMarksOnlyBackedCapabilitiesAvailable() {
        val state = AiVoiceCapabilityConfigState()
            .withApiKeyInput("speech-key")
            .verifyingCapabilities()
            .withTtsVerificationSucceeded()

        assertEquals(AiVoiceCapabilityStatus.Available, state.statusFor(AiVoiceFeature.AiVoiceMessage))
        assertEquals(AiVoiceCapabilityStatus.NotChecked, state.statusFor(AiVoiceFeature.RealtimeConversation))
        assertEquals(AiVoiceCapabilityStatus.NotChecked, state.statusFor(AiVoiceFeature.ClonedVoiceMessage))
        assertFalse(state.loading)
        assertTrue(state.statusMessage.orEmpty().contains("语音合成可用"))
    }

    @Test
    fun failedProbeKeepsProviderErrorVisible() {
        val state = AiVoiceCapabilityConfigState()
            .verifyingCapabilities()
            .withVerificationError("HTTP 403: resource not activated")

        assertFalse(state.loading)
        assertEquals("HTTP 403: resource not activated", state.errorMessage)
        assertEquals(AiVoiceCapabilityStatus.Unavailable, state.statusFor(AiVoiceFeature.AiVoiceMessage))
    }

    @Test
    fun independentProbeResultsAreMergedForAllCapabilities() {
        val state = AiVoiceCapabilityConfigState()
            .withApiKeyInput("speech-key")
            .withAppIdInput("1234567890")
            .withAccessTokenInput("token")
            .verifyingCapabilities()
            .withProbeResults(ttsAvailable = true, realtimeAvailable = true)

        assertEquals(AiVoiceCapabilityStatus.Available, state.statusFor(AiVoiceFeature.AiVoiceMessage))
        assertEquals(AiVoiceCapabilityStatus.Available, state.statusFor(AiVoiceFeature.RealtimeConversation))
        assertEquals(AiVoiceCapabilityStatus.Available, state.statusFor(AiVoiceFeature.VoiceAgent))
        assertEquals(AiVoiceCapabilityStatus.NeedsSetup, state.statusFor(AiVoiceFeature.ClonedVoiceMessage))
        assertEquals(AiVoiceCapabilityStatus.NeedsSetup, state.statusFor(AiVoiceFeature.ClonedVoiceCall))
    }
}
