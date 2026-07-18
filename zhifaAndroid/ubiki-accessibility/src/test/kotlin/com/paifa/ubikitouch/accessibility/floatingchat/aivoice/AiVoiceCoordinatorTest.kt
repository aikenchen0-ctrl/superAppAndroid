package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Test

class AiVoiceCoordinatorTest {
    @Test
    fun completedVoiceProfileCreationResumesOriginalClonedMessageAction() {
        val coordinator = AiVoiceCoordinator(
            hasVoiceProfile = false,
            apiConfig = configuredApi()
        )

        coordinator.dispatch(AiVoiceEvent.FeatureSelected(AiVoiceFeature.ClonedVoiceMessage))
        coordinator.dispatch(AiVoiceEvent.VoiceProfileCreated)

        assertEquals(
            AiVoiceState.GeneratingMessage(AiVoiceFeature.ClonedVoiceMessage),
            coordinator.state
        )
    }

    @Test
    fun clonedCallWithProfileStartsRealtimeConnection() {
        val coordinator = AiVoiceCoordinator(
            hasVoiceProfile = true,
            apiConfig = configuredApi()
        )

        coordinator.dispatch(AiVoiceEvent.FeatureSelected(AiVoiceFeature.ClonedVoiceCall))

        assertEquals(AiVoiceState.RealtimeCall(RealtimeCallState.Connecting), coordinator.state)
    }

    @Test
    fun directProviderFeatureIsNotBlockedByLegacyGatewayConfiguration() {
        val coordinator = AiVoiceCoordinator(
            hasVoiceProfile = false,
            apiConfig = AiVoiceApiConfig()
        )

        coordinator.dispatch(AiVoiceEvent.FeatureSelected(AiVoiceFeature.RealtimeConversation))

        assertEquals(AiVoiceState.RealtimeCall(RealtimeCallState.Connecting), coordinator.state)
    }

    private fun configuredApi() = AiVoiceApiConfig(
        gatewayBaseUrl = "https://voice.example.com",
        sessionToken = "token"
    )
}
