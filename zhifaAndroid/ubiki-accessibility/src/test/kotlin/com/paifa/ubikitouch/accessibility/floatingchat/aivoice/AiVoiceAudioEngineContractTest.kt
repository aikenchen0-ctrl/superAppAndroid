package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceAudioEngineContractTest {
    @Test
    fun engineUsesDocumentedPcmFormatsAndReleasesNativeResources() {
        val source = sourceFile().readText()

        assertTrue(source.contains("InputSampleRateHz = 16_000"))
        assertTrue(source.contains("OutputSampleRateHz = 24_000"))
        assertTrue(source.contains("CHANNEL_IN_MONO"))
        assertTrue(source.contains("ENCODING_PCM_16BIT"))
        assertTrue(source.contains("audioRecord.release()"))
        assertTrue(source.contains("audioTrack.release()"))
    }

    private fun sourceFile(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceAudioEngine.kt"
        )
        if (moduleRelative.exists()) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoiceAudioEngine.kt"
        )
    }
}
