package com.paifa.univerge.accessibility.floatingchat.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageRevokeRuntimeStateTest {
    @Test
    fun revokeTaskIsScopedByAccountAndRemoteMessage() {
        val state = FloatingChatOverlayRuntimeState()

        state.rememberMessageRevokeTask("account-a", 101L, 901L)
        state.rememberMessageRevokeTask("account-b", 101L, 902L)

        assertEquals(901L, state.messageRevokeTaskId("account-a", 101L))
        assertEquals(902L, state.messageRevokeTaskId("account-b", 101L))

        state.clearMessageRevokeTask("account-a", 101L)

        assertNull(state.messageRevokeTaskId("account-a", 101L))
        assertEquals(902L, state.messageRevokeTaskId("account-b", 101L))
    }
}
