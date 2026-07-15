package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.accessibility.leftRailSortsSessionsByLatestChatTime
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal sealed class SessionRailItem(open val contact: FloatingChatContact) {
    abstract val key: String
    abstract val contentType: String

    data class Group(override val contact: FloatingChatContact) : SessionRailItem(contact) {
        override val key: String = "group-${contact.id}"
        override val contentType: String = "group"
    }

    data class Contact(override val contact: FloatingChatContact) : SessionRailItem(contact) {
        override val key: String = "contact-${contact.id}"
        override val contentType: String = "contact"

        companion object {
            fun keyFor(contactId: String): String = "contact-$contactId"
        }
    }
}

internal fun sessionRailItemKeys(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>
): List<String> {
    return sessionRailItems(
        groups = groups,
        contacts = contacts
    ).map { item -> item.key }
}

internal fun sessionRailItemKeysByLatestChatTime(
    conversation: FloatingChatConversation,
    selectedAccountId: String
): List<String> {
    return sessionRailItemsByLatestChatTime(
        groups = conversation.groupContacts,
        contacts = conversation.contacts,
        conversation = conversation,
        selectedAccountId = selectedAccountId
    ).map { item -> item.key }
}

internal fun sessionRailItemsByLatestChatTime(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    conversation: FloatingChatConversation,
    selectedAccountId: String
): List<SessionRailItem> {
    val items = sessionRailItems(groups, contacts)
    if (!leftRailSortsSessionsByLatestChatTime()) return items

    val latestMessageIndexes = latestMessageIndexesBySessionRailKey(
        conversation = conversation,
        groups = groups,
        contacts = contacts,
        selectedAccountId = selectedAccountId
    )
    return items.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<SessionRailItem>> { indexed ->
                latestMessageIndexes[indexed.value.key] ?: Int.MIN_VALUE
            }.thenBy { indexed -> indexed.index }
        )
        .map { indexed -> indexed.value }
}

private fun sessionRailItems(
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>
): List<SessionRailItem> {
    return buildList {
        dedupeSessionRailContacts(groups).forEach { group -> add(SessionRailItem.Group(group)) }
        dedupeSessionRailContacts(contacts).forEach { contact -> add(SessionRailItem.Contact(contact)) }
    }
}

private fun dedupeSessionRailContacts(contacts: List<FloatingChatContact>): List<FloatingChatContact> {
    val mergedById = linkedMapOf<String, FloatingChatContact>()
    contacts.forEach { contact ->
        val existing = mergedById[contact.id]
        mergedById[contact.id] = if (existing == null) {
            contact
        } else {
            mergeRailContact(existing, contact)
        }
    }
    return mergedById.values.toList()
}

private fun latestMessageIndexesBySessionRailKey(
    conversation: FloatingChatConversation,
    groups: List<FloatingChatContact>,
    contacts: List<FloatingChatContact>,
    selectedAccountId: String
): Map<String, Int> {
    val groupIds = groups.asSequence().map { group -> group.id }.toHashSet()
    val contactIds = contacts.asSequence().map { contact -> contact.id }.toHashSet()
    val indexes = mutableMapOf<String, Int>()
    conversation.messages.forEachIndexed { index, message ->
        val key = sessionRailKeyForMessage(
            message = message,
            groupIds = groupIds,
            contactIds = contactIds,
            selectedAccountId = selectedAccountId
        ) ?: return@forEachIndexed
        indexes[key] = index
    }
    return indexes
}

private fun sessionRailKeyForMessage(
    message: FloatingChatMessage,
    groupIds: Set<String>,
    contactIds: Set<String>,
    selectedAccountId: String
): String? {
    val explicitThreadId = message.threadContactId?.takeIf { threadId -> threadId.isNotBlank() }
    return when {
        explicitThreadId != null && explicitThreadId in groupIds -> "group-$explicitThreadId"
        explicitThreadId != null && explicitThreadId in contactIds -> SessionRailItem.Contact.keyFor(explicitThreadId)
        message.connectionTarget == FloatingChatConnectionTarget.User &&
            message.connectionTargetId in groupIds -> "group-${message.connectionTargetId}"
        message.connectionTarget == FloatingChatConnectionTarget.User &&
            message.connectionTargetId in contactIds -> SessionRailItem.Contact.keyFor(requireNotNull(message.connectionTargetId))
        message.connectionTarget == FloatingChatConnectionTarget.Account &&
            message.connectionTargetId == selectedAccountId -> null
        else -> null
    }
}

private fun mergeRailContact(
    existing: FloatingChatContact,
    candidate: FloatingChatContact
): FloatingChatContact {
    return existing.copy(
        name = existing.name.ifBlank { candidate.name },
        initials = existing.initials.ifBlank { candidate.initials },
        description = existing.description.ifBlank { candidate.description },
        selected = existing.selected || candidate.selected,
        online = existing.online || candidate.online,
        avatarUrl = existing.avatarUrl ?: candidate.avatarUrl,
        groupMemberAvatarUrls = existing.groupMemberAvatarUrls.ifEmpty { candidate.groupMemberAvatarUrls }
    )
}
