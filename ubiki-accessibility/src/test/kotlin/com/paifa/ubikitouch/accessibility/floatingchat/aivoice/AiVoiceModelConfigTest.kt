package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceModelConfigTest {
    @Test
    fun visibleArkModelsAreMatchedToSupportedVoiceFeaturesOnly() {
        val models = listOf(
            AiVoiceModelOption(
                id = "doubao-seed-2-0-mini-260428",
                name = "doubao-seed-2-0-mini",
                domain = "VLM",
                status = null,
                taskTypes = listOf("VisualQuestionAnswering", "TextGeneration", "SpeechToText")
            ),
            AiVoiceModelOption(
                id = "doubao-tts-pro",
                name = "doubao-tts-pro",
                domain = "Audio",
                status = null,
                taskTypes = listOf("TextToSpeech")
            )
        )

        val state = AiVoiceModelConfigState().withFetchedModels(models)

        assertEquals("doubao-seed-2-0-mini-260428", state.selectedModelId(AiVoiceFeature.VoiceAgent))
        assertEquals("doubao-tts-pro", state.selectedModelId(AiVoiceFeature.AiVoiceMessage))
        assertEquals("doubao-tts-pro", state.selectedModelId(AiVoiceFeature.ClonedVoiceMessage))
        assertNull(state.selectedModelId(AiVoiceFeature.RealtimeConversation))
        assertNull(state.selectedModelId(AiVoiceFeature.ClonedVoiceCall))
    }

    @Test
    fun modelFetchSummaryExposesVoiceCapabilityGaps() {
        val state = AiVoiceModelConfigState().withFetchedModels(
            listOf(
                AiVoiceModelOption(
                    id = "doubao-seed-2-0-mini-260428",
                    name = "doubao-seed-2-0-mini",
                    domain = "VLM",
                    status = null,
                    taskTypes = listOf("VisualQuestionAnswering", "TextGeneration", "SpeechToText")
                )
            )
        )

        assertTrue(state.statusMessage.orEmpty().contains("已获取 1 个模型"))
        assertTrue(state.missingFeatureLabels().contains("实时对话"))
        assertTrue(state.missingFeatureLabels().contains("AI语音消息"))
    }

    @Test
    fun selectedModelCanBeChangedPerVoiceFeature() {
        val model = AiVoiceModelOption(
            id = "doubao-tts-pro",
            name = "doubao-tts-pro",
            domain = "Audio",
            status = null,
            taskTypes = listOf("TextToSpeech")
        )
        val state = AiVoiceModelConfigState()
            .withFetchedModels(listOf(model))
            .selectModel(AiVoiceFeature.AiVoiceMessage, model.id)

        assertEquals(model.id, state.selectedModelId(AiVoiceFeature.AiVoiceMessage))
    }
}
