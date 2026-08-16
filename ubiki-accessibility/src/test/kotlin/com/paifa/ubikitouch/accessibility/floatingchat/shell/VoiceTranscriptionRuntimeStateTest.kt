package com.paifa.ubikitouch.accessibility.floatingchat.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTranscriptionRuntimeStateTest {
    @Test
    fun processingTaskSurvivesOverlayRemountUntilTerminalResult() {
        val state = FloatingChatOverlayRuntimeState()

        state.rememberVoiceTranscriptionTask(
            accountId = "account-1",
            remoteMessageId = 123L,
            taskId = 75L
        )

        assertEquals(75L, state.voiceTranscriptionTaskId("account-1", 123L))
        assertNull(state.voiceTranscriptionTaskId("account-2", 123L))

        state.clearVoiceTranscriptionTask("account-1", 123L)

        assertNull(state.voiceTranscriptionTaskId("account-1", 123L))
    }
}
