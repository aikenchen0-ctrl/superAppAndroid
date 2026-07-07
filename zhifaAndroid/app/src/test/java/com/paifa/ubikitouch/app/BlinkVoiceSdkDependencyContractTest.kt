package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkVoiceSdkDependencyContractTest {
    @Test
    fun appUsesSourceBlinkVoiceVisualSdkModuleInsteadOfBundledAar() {
        val settings = projectFile("settings.gradle.kts").readText()
        val appBuild = projectFile("app/build.gradle.kts").readText()

        assertTrue(settings.contains("include(\":blinkvoice-visual-sdk\")"))
        assertTrue(appBuild.contains("implementation(project(\":blinkvoice-visual-sdk\"))"))
        assertFalse(appBuild.contains("blinkvoice-visual-sdk-release.aar"))
        assertTrue(projectFile("blinkvoice-visual-sdk/src/main/java/com/blinkvoice/visual/detector/BlinkDetector.kt").isFile)
        assertTrue(projectFile("blinkvoice-visual-sdk/src/main/assets/face_landmarker.task").isFile)
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
