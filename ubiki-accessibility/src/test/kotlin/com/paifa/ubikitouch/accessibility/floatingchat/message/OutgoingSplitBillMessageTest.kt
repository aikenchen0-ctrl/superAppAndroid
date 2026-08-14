package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OutgoingSplitBillMessageTest {
    /** 测试流程：群聊选择两名成员并提交 320.00，核对本地卡片及 iOS 六行详情。 */
    @Test
    fun addSplitBillMessageCreatesIosCompatibleLocalCard() {
        val account = contact("account-main", "林舟")
        val members = listOf(contact("member-a", "小王"), contact("member-b", "陈浩"))
        val conversation = FloatingChatConversation(
            peerName = "运营群",
            accountName = account.name,
            contacts = members,
            accountContacts = listOf(account),
            messages = emptyList(),
            toolActions = emptyList()
        )
        var createdMessage: FloatingChatMessage? = null
        val actions = OutgoingMessageActions(
            conversation = conversation,
            selectedThread = ChatThreadSelection.GroupChat("group-ops"),
            selectedAccount = account,
            nextSequence = { 1 },
            prepareOutgoingMessage = { message, _ -> message },
            onOutgoingMessageCreated = { message, _ -> createdMessage = message }
        )

        actions.addSplitBillMessage("运营群", "320.00", members)

        assertNotNull(createdMessage)
        val message = requireNotNull(createdMessage)
        assertEquals(FloatingChatMessageType.SplitBill, message.type)
        assertEquals(FloatingChatMessagePresentation.SpecialCard, message.presentation)
        assertEquals("群收款 2 人", message.text)
        assertEquals(
            "群收款总额：¥320.00\n" +
                "收款群：运营群\n" +
                "已收 0/2 人\n" +
                "收款成员：小王、陈浩\n" +
                "收款成员ID：member-a、member-b\n" +
                "未支付：小王、陈浩",
            message.detail
        )
    }

    private fun contact(id: String, name: String) = FloatingChatContact(
        id = id,
        name = name,
        initials = name.take(2),
        description = "群成员",
        avatarColor = 0xFF607D8B
    )
}
