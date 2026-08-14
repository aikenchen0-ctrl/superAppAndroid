package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toPrototypeToolSelection
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal class OutgoingMessageActions(
    private val conversation: FloatingChatConversation,
    private val selectedThread: ChatThreadSelection,
    private val selectedAccount: FloatingChatContact,
    private val nextSequence: () -> Int,
    private val prepareOutgoingMessage: (FloatingChatMessage, String) -> FloatingChatMessage,
    private val onOutgoingMessageCreated: (FloatingChatMessage, String) -> Unit
) {
    fun addTextMessage(text: String, quotedMessage: FloatingChatMessage?) {
        val sequence = nextSequence()
        val baseMessage = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingTextMessage(
                    conversation = conversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    text = text,
                    sequence = sequence
                )
            }
        }
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(
            outgoingTextMessageWithOptionalQuote(baseMessage, quotedMessage),
            threadId
        )
        onOutgoingMessageCreated(message, threadId)
    }

    fun addVoiceMessage(audioUri: String, durationMs: Int) {
        val sequence = nextSequence()
        val baseMessage = when (val thread = selectedThread) {
            ChatThreadSelection.Group -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence
                )
            }
            is ChatThreadSelection.GroupChat -> {
                FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
                    conversation = conversation,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence,
                    groupId = thread.groupId
                )
            }
            is ChatThreadSelection.Private -> {
                FloatingChatPrototype.simulatedOutgoingVoiceMessage(
                    conversation = conversation,
                    contactId = thread.contactId,
                    accountId = selectedAccount.id,
                    audioUri = audioUri,
                    durationMs = durationMs,
                    sequence = sequence
                )
            }
        }
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessage(baseMessage, threadId)
        onOutgoingMessageCreated(message, threadId)
    }

    fun addToolMessage(
        action: FloatingChatToolAction,
        customize: (FloatingChatMessage) -> FloatingChatMessage = { it }
    ) {
        val sequence = nextSequence()
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = action,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = sequence
        )
        val message = customize(baseMessage)
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }

    /**
     * 创建 iOS 对齐的本地音乐分享卡片。当前 SCRM 接口目录没有 music-card，不能伪造远端请求；
     * 分享结果进入本地会话，后续可由真实消息同步替换。
     */
    fun addMusicMessage(
        title: String,
        artist: String,
        audioUrl: String?,
        durationLabel: String
    ) {
        val sequence = nextSequence()
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Favorite,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = selectedAccount.id,
            sequence = sequence
        )
        val message = prepareOutgoingMessage(
            baseMessage.copy(
                type = FloatingChatMessageType.Music,
                text = title,
                detail = "音乐分享 · $durationLabel",
                cardName = title,
                cardSubtitle = artist,
                resourceUrl = audioUrl
            ),
            selectedThread.toLocalThreadId()
        )
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }

    /**
     * 按 iOS `insertOutgoingMessage(.splitBill, ...)` 契约创建本地群收款卡片。
     * AA 收款没有独立远端写接口，消息经过 prepareOutgoingMessage 后保持本地状态，不进入虚构的网络队列。
     * 测试流程：选择群成员、提交金额，检查正文、六行详情和当前会话列表中的 SplitBill 类型。
     */
    fun addSplitBillMessage(
        groupName: String,
        totalAmount: String,
        members: List<FloatingChatContact>
    ) {
        require(selectedThread !is ChatThreadSelection.Private) { "AA 收款只能在群聊中发起" }
        require(members.isNotEmpty()) { "至少选择一位收款成员" }
        val sequence = nextSequence()
        val groupId = (selectedThread as? ChatThreadSelection.GroupChat)?.groupId
        val baseMessage = FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
            conversation = conversation,
            accountId = selectedAccount.id,
            text = "群收款 ${members.size} 人",
            sequence = sequence,
            groupId = groupId
        )
        val names = members.joinToString("、") { it.name }
        val ids = members.joinToString("、") { it.id }
        val message = prepareOutgoingMessage(
            baseMessage.copy(
                type = FloatingChatMessageType.SplitBill,
                presentation = FloatingChatMessagePresentation.SpecialCard,
                text = "群收款 ${members.size} 人",
                detail = buildString {
                    appendLine("群收款总额：¥$totalAmount")
                    appendLine("收款群：$groupName")
                    appendLine("已收 0/${members.size} 人")
                    appendLine("收款成员：$names")
                    appendLine("收款成员ID：$ids")
                    append("未支付：$names")
                },
                appName = "AA 收款",
                cardName = groupName,
                cardSubtitle = "已收 0/${members.size} 人",
                resourceUrl = null
            ),
            selectedThread.toLocalThreadId()
        )
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }

    fun addAccountCardMessage(
        account: FloatingChatContact,
        customize: (FloatingChatMessage) -> FloatingChatMessage
    ) {
        val sequence = nextSequence()
        val baseMessage = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Card,
            selection = selectedThread.toPrototypeToolSelection(),
            accountId = account.id,
            sequence = sequence
        )
        val message = customize(baseMessage)
        onOutgoingMessageCreated(message, selectedThread.toLocalThreadId())
    }
}
