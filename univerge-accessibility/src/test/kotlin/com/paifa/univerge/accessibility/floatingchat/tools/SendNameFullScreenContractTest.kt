package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.message.outgoingMessageCarriesName
import com.paifa.univerge.core.model.FloatingChatConnectionTarget
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.core.model.FloatingChatToolAction
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
