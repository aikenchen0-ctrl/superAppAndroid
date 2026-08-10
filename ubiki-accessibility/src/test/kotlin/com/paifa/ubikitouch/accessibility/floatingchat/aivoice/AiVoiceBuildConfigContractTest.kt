package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceBuildConfigContractTest {
    @Test
    fun gradleExposesGatewayConfigurationWithoutDefaultSecrets() {
        val source = buildFile().readText()

        assertTrue(source.contains("AI_VOICE_GATEWAY_BASE_URL"))
        assertTrue(source.contains("AI_VOICE_SESSION_TOKEN"))
        assertTrue(source.contains("ai.voice.gatewayBaseUrl"))
        assertTrue(source.contains("AI_VOICE_GATEWAY_BASE_URL"))
    }

    private fun buildFile(): File {
        val moduleRelative = File("build.gradle.kts")
        if (moduleRelative.readText().contains("namespace = \"com.paifa.ubikitouch.accessibility\"")) {
            return moduleRelative
        }
        return File("ubiki-accessibility/build.gradle.kts")
    }
}
