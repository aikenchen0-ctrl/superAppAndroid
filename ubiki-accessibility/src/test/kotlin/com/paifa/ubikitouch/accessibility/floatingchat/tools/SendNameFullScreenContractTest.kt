package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.message.outgoingMessageCarriesName
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendNameFullScreenContractTest {
    @Test
    fun sendNameActionOpensItsDedicatedFullScreenMode() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.SendName),
            toolActionDispatchFor(FloatingChatToolAction.SendName)
        )
    }

    @Test
    fun sendNameDefaultsToEnabledAndIsScopedToTheSendingAccount() {
        val outgoingMessage = FloatingChatMessage(
            id = "outgoing",
            type = FloatingChatMessageType.Text,
            text = "test",
            fromMe = true,
            senderName = "当前账号",
            time = "刚刚",
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = "account-a"
        )

        assertTrue(outgoingMessageCarriesName(outgoingMessage, emptyMap()))
        assertFalse(outgoingMessageCarriesName(outgoingMessage, mapOf("account-a" to false)))
        assertTrue(outgoingMessageCarriesName(outgoingMessage, mapOf("account-b" to false)))
    }
}
