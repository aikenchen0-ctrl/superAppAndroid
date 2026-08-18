package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessagePresentation

/** iOS「携带名字」的本地展示策略：未配置的账号默认携带名字。 */
internal fun outgoingMessageCarriesName(
    message: FloatingChatMessage,
    enabledByAccountId: Map<String, Boolean>
): Boolean {
    if (!message.fromMe || message.presentation == FloatingChatMessagePresentation.System) return false
    return enabledByAccountId[message.connectionTargetId] ?: true
}
