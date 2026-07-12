package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkVoiceSdkDependencyContractTest {
    @Test
    fun appUsesBundledBlinkVoiceVisualSdkAarInsteadOfSourceModule() {
        val settings = projectFile("settings.gradle.kts").readText()
        val appBuild = projectFile("app/build.gradle.kts").readText()

        assertFalse(settings.contains("include(\":blinkvoice-visual-sdk\")"))
        assertFalse(appBuild.contains("implementation(project(\":blinkvoice-visual-sdk\"))"))
        assertTrue(appBuild.contains("implementation(files(\"libs/blinkvoice-visual-sdk-release.aar\"))"))
        assertTrue(projectFile("app/libs/blinkvoice-visual-sdk-release.aar").isFile)
    }

    @Test
    fun releaseBuildDoesNotMinifyBlinkVoiceNativeDependencies() {
        val appBuild = projectFile("app/build.gradle.kts").readText()

        assertTrue(appBuild.contains("isMinifyEnabled = false"))
        assertTrue(appBuild.contains("isShrinkResources = false"))
    }

    @Test
    fun releaseShrinkRulesKeepAllMediapipeGeneratedProtoFieldsForReflection() {
        val proguardRules = projectFile("app/proguard-rules.pro").readText()

        assertTrue(
            proguardRules.contains(
                "-keepclassmembers class com.google.mediapipe.** { <fields>; }"
            )
        )
    }

    @Test
    fun releaseShrinkRulesKeepFloggerCallerFramesForMediapipeGraphInitialization() {
        val proguardRules = projectFile("app/proguard-rules.pro").readText()

        assertTrue(
            proguardRules.contains(
                "-keep class com.google.common.flogger.** { *; }"
            )
        )
    }

    @Test
    fun blinkBridgeUsesTheSdkEyelidAngleThresholdInsteadOfTheLegacyEarThreshold() {
        val activitySource = projectFile(
            "app/src/main/java/com/paifa/ubikitouch/app/FloatingChatBlinkVoiceActivity.kt"
        ).readText()

        assertTrue(activitySource.contains("elaCloseThreshold = BlinkDetector.ELA_CLOSE_THRESHOLD"))
        assertFalse(activitySource.contains("elaCloseThreshold = options.earCloseThreshold"))
    }

    private fun projectFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("../$path")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")
    }
}
