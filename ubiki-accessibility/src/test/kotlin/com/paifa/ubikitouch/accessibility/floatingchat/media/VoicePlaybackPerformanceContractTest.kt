package com.paifa.ubikitouch.accessibility.floatingchat.media

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackPerformanceContractTest {
    private val source by lazy {
        productionSource("floatingchat/media/NativeMediaSurface.kt").readText()
    }

    @Test
    fun voicePlaybackPreparesAsynchronouslyWithoutBlockingTheClickHandler() {
        assertTrue(source.contains("setOnPreparedListener"))
        assertTrue(source.contains("prepareAsync()"))
        assertFalse(Regex("""(?<!Async)\bprepare\(\)""").containsMatchIn(source))
    }

    @Test
    fun mediaPlayerConstructionKeepsTheExistingFailureBoundary() {
        assertTrue(
            Regex("""runCatching\s*\{\s*MediaPlayer\(\)\.also""")
                .containsMatchIn(source)
        )
    }

    @Test
    fun asyncPreparationKeepsExistingPlaybackAndFailureSemantics() {
        assertTrue(source.contains("onClick()"))
        assertTrue(source.contains("if (preparing)"))
        assertTrue(source.contains("currentPlayer.pause()"))
        assertTrue(source.contains("currentPlayer.start()"))
        assertTrue(source.contains("setOnCompletionListener"))
        assertTrue(source.contains("it.seekTo(0)"))
        assertTrue(source.contains("setOnErrorListener"))
        assertTrue(source.contains("failed = true"))
    }

    private fun productionSource(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
        if (moduleRelative.isFile) return moduleRelative
        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
    }
}
