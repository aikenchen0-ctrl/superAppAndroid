package com.paifa.univerge.accessibility.floatingchat.shell

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiAssistantWorkspacePresentationTest {
    @Test(timeout = 60_000)
    fun `AI auto reply is presented as a fullscreen workspace`() {
        assertTrue(aiAssistantUsesFullscreenWorkspace())
    }

    @Test(timeout = 60_000)
    fun `AI workspace enters from below and exits above`() {
        assertEquals(1, aiAssistantEnterOffsetDirection())
        assertEquals(-1, aiAssistantExitOffsetDirection())
    }
}
