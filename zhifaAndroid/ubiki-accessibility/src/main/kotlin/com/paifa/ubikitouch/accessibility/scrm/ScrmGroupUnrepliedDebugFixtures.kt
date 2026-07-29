package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal fun scrmGroupUnrepliedDebugMessages(
    groups: List<FloatingChatContact>
): List<FloatingChatMessage> {
    return groups.flatMapIndexed { groupIndex, group ->
        groupUnrepliedScenarioMessages(group = group, groupIndex = groupIndex)
    }
}

private fun groupUnrepliedScenarioMessages(
    group: FloatingChatContact,
    groupIndex: Int
): List<FloatingChatMessage> {
    val members = group.groupMemberContacts.ifEmpty { listOf(group) }
    val firstMember = members.first()
    val secondMember = members.getOrElse(1) { firstMember }
    var sequence = 0

    fun message(
        scenario: String,
        text: String,
        type: FloatingChatMessageType = FloatingChatMessageType.Text,
        fromMe: Boolean = false,
        member: FloatingChatContact = firstMember,
        kind: FloatingChatMessageKind = FloatingChatMessageKind.Normal,
        presentation: FloatingChatMessagePresentation = FloatingChatMessagePresentation.Bubble,
        connectionTarget: FloatingChatConnectionTarget = if (fromMe) {
            FloatingChatConnectionTarget.Account
        } else {
            FloatingChatConnectionTarget.User
        }
    ): FloatingChatMessage {
        val currentSequence = sequence++
        return FloatingChatMessage(
            id = "scrm-group-unreplied-$groupIndex-$scenario-$currentSequence",
            type = type,
            text = text,
            fromMe = fromMe,
            senderName = if (fromMe) "我" else member.name.ifBlank { "未命名成员" },
            time = "${(9 + groupIndex).coerceAtMost(23).toString().padStart(2, '0')}:${(currentSequence * 2).coerceAtMost(59).toString().padStart(2, '0')}",
            kind = kind,
            presentation = presentation,
            connectionTarget = connectionTarget,
            connectionTargetId = if (fromMe) null else member.id,
            threadContactId = group.id
        )
    }

    val excludedTypes = FloatingChatMessageType.entries
        .filterNot { type -> type == FloatingChatMessageType.Text }
        .map { type ->
            message(
                scenario = "excluded-${type.name.lowercase()}",
                text = "[排除:${type.label}] 不应进入未回聚合",
                type = type
            )
        }

    return buildList {
        add(message("old-incoming", "[场景:回复边界] 这条位于我方回复之前，不应保留"))
        addAll(excludedTypes)
        add(message("blank-text", "   \n\t"))
        add(
            message(
                scenario = "system-notice",
                text = "[排除:系统消息] 群公告已更新",
                kind = FloatingChatMessageKind.System,
                presentation = FloatingChatMessagePresentation.System,
                connectionTarget = FloatingChatConnectionTarget.None
            )
        )
        add(
            message(
                scenario = "special-card",
                text = "[排除:特殊卡片] 不应形成未回事项",
                presentation = FloatingChatMessagePresentation.SpecialCard
            )
        )
        add(
            message(
                scenario = "ai-draft",
                text = "[排除:AI草稿] 建议稍后统一回复",
                fromMe = true,
                kind = FloatingChatMessageKind.AiDraft
            )
        )
        add(message("self-reply-boundary", "[场景:回复边界] 我方已经回复", fromMe = true))
        add(message("single-incoming", "[场景:单条未回] 请确认今天的群内安排"))
        add(message("same-member-sequence-1", "[场景:同成员连续] 第一条补充说明"))
        add(message("same-member-sequence-2", "[场景:同成员连续] 第二条补充说明"))
        add(message("same-member-sequence-3", "[场景:同成员连续] 第三条，请以此为准"))
        add(message("alternating-members-1", "[场景:多成员交替] 成员一提出问题", member = firstMember))
        add(message("alternating-members-2", "[场景:多成员交替] 成员二补充信息", member = secondMember))
        add(message("alternating-members-3", "[场景:多成员交替] 成员一继续追问", member = firstMember))
        add(message("mention-me", "[场景:@我] @我 请确认报价和交付时间"))
        add(message("mention-all", "[场景:@所有人] @所有人 明早十点前反馈"))
        add(message("pending-confirmation", "[场景:待确认] 这个事项待确认，请核对后处理"))
        add(message("reply-request", "[场景:请回复] 看到后请回复最新处理结果"))
        add(
            message(
                "long-text",
                "[场景:长文本] 这是用于验证长文本换行、气泡高度、滚动位置和返回恢复的一段测试内容。" +
                    "请依次核对需求、排期、负责人、风险、验收标准和后续动作，确认无误后在群里统一回复。".repeat(3)
            )
        )
        add(message("emoji-mixed", "[场景:混合字符] 已收到 ✅ 测试 A/B、中文 English、￥100、#标签、括号()[]{}"))
        add(message("same-time-a", "[场景:相同时间] 第一条同时间消息").copy(time = "23:59"))
        add(message("same-time-b", "[场景:相同时间] 第二条同时间消息").copy(time = "23:59"))
        add(message("latest-incoming", "[场景:最新未回] 这是该群当前最后一条有效未回消息").copy(time = "23:59"))
    }
}
