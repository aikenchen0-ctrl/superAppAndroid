package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

class AiVoiceCoordinator(
    hasVoiceProfile: Boolean,
    private val apiConfig: AiVoiceApiConfig
) {
    private var hasVoiceProfile = hasVoiceProfile
    private var pendingFeature: AiVoiceFeature? = null

    var state: AiVoiceState = AiVoiceState.Menu
        private set

    fun dispatch(event: AiVoiceEvent): AiVoiceState {
        if (event is AiVoiceEvent.FeatureSelected && event.feature.requiresVoiceProfile() && !hasVoiceProfile) {
            pendingFeature = event.feature
            state = AiVoiceState.CreatingVoiceProfile(VoiceProfileStep.Consent)
            return state
        }

        if (event == AiVoiceEvent.VoiceProfileCreated) {
            hasVoiceProfile = true
            val feature = pendingFeature
            pendingFeature = null
            state = if (feature == null) {
                AiVoiceState.Menu
            } else {
                reduceAiVoiceState(AiVoiceState.Menu, AiVoiceEvent.FeatureSelected(feature), true)
            }
            return state
        }

        state = reduceAiVoiceState(state, event, hasVoiceProfile)
        return state
    }
}

private fun AiVoiceFeature.requiresVoiceProfile(): Boolean {
    return this == AiVoiceFeature.ClonedVoiceMessage || this == AiVoiceFeature.ClonedVoiceCall
}
