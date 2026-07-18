package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.accessibility.data.localThreadIdForSelection
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatPrototype

internal const val GroupThreadId = "floating-chat-group-thread"

internal sealed interface ChatThreadSelection {
    data object Group : ChatThreadSelection
    data class GroupChat(val groupId: String) : ChatThreadSelection
    data class Private(val contactId: String) : ChatThreadSelection
}

internal fun localMessageReplacementInvalidatesDisplayConversation(): Boolean = true

internal fun ChatThreadSelection.toLocalThreadId(): String {
    return when (this) {
        ChatThreadSelection.Group -> localThreadIdForSelection()
        is ChatThreadSelection.GroupChat -> localThreadIdForSelection(groupId = groupId)
        is ChatThreadSelection.Private -> localThreadIdForSelection(privateContactId = contactId)
    }
}

internal fun groupMemberRailContacts(
    contacts: List<FloatingChatContact>,
    messages: List<FloatingChatMessage>
): List<FloatingChatContact> {
    val contactById = contacts.associateBy { contact -> contact.id }
    return messages
        .asSequence()
        .filter { message -> message.connectionTarget == FloatingChatConnectionTarget.User }
        .mapNotNull { message -> message.connectionTargetId }
        .filterNot { targetId -> targetId == ASSISTANT_CONTACT_ID }
        .distinct()
        .mapNotNull { targetId -> contactById[targetId] }
        .toList()
}

internal fun ChatThreadSelection.toPrototypeToolSelection(): FloatingChatPrototype.ToolThreadSelection {
    return when (this) {
        ChatThreadSelection.Group -> FloatingChatPrototype.ToolThreadSelection.Group
        is ChatThreadSelection.GroupChat -> FloatingChatPrototype.ToolThreadSelection.GroupChat(groupId)
        is ChatThreadSelection.Private -> FloatingChatPrototype.ToolThreadSelection.Private(contactId)
    }
}

internal fun ChatThreadSelection.isGroupThread(): Boolean {
    return this is ChatThreadSelection.Group || this is ChatThreadSelection.GroupChat
}

internal fun ChatThreadSelection.groupConnectorId(): String {
    return when (this) {
        ChatThreadSelection.Group -> GroupThreadId
        is ChatThreadSelection.GroupChat -> groupId.groupConnectorId()
        is ChatThreadSelection.Private -> contactId
    }
}

internal fun FloatingChatContact.toGroupThreadSelection(): ChatThreadSelection {
    return if (id == GroupThreadId) {
        ChatThreadSelection.Group
    } else {
        ChatThreadSelection.GroupChat(id)
    }
}

internal fun FloatingChatContact.groupConnectorId(): String {
    return id.groupConnectorId()
}

internal fun String.groupConnectorId(): String {
    return "floating-chat-group-$this"
}

internal fun FloatingChatPrototype.ToolThreadSelection.toChatThreadSelection(): ChatThreadSelection {
    return when (this) {
        FloatingChatPrototype.ToolThreadSelection.Group -> ChatThreadSelection.Group
        is FloatingChatPrototype.ToolThreadSelection.GroupChat -> ChatThreadSelection.GroupChat(groupId)
        is FloatingChatPrototype.ToolThreadSelection.Private -> ChatThreadSelection.Private(contactId)
    }
}

private const val ASSISTANT_CONTACT_ID = "assistant"
