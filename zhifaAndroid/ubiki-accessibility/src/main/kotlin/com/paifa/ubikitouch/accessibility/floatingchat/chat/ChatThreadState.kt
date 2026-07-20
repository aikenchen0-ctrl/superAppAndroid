package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.GroupThreadId
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.floatingchat.chat.groupConnectorId
import com.paifa.ubikitouch.accessibility.floatingchat.chat.isGroupThread
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toLocalThreadId
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import kotlin.math.min

internal fun defaultChatThreadSelection(
    conversation: FloatingChatConversation
): ChatThreadSelection {
    return if (conversation.groupContacts.isNotEmpty()) {
        ChatThreadSelection.GroupChat(conversation.groupContacts.first().id)
    } else if (conversation.contacts.isNotEmpty()) {
        ChatThreadSelection.Private(conversation.contacts.first().id)
    } else {
        ChatThreadSelection.Group
    }
}

internal fun selectedAccountIdAfterAccountAvatarClick(
    currentAccountId: String,
    clickedAccountId: String
): String {
    return clickedAccountId.ifBlank { currentAccountId }
}

internal fun selectedThreadAfterAccountAvatarClick(
    conversation: FloatingChatConversation,
    clickedAccountId: String,
    currentThread: ChatThreadSelection
): ChatThreadSelection {
    val scopedConversation = accountScopedConversation(
        conversation = conversation,
        activeAccountId = clickedAccountId
    )
    return initialChatThreadSelection(
        conversation = scopedConversation,
        preferredSelection = currentThread
    )
}

internal fun accountScopedConversation(
    conversation: FloatingChatConversation,
    activeAccountId: String
): FloatingChatConversation {
    val activeAccount = conversation.accountContacts.firstOrNull { account -> account.id == activeAccountId }
        ?: conversation.accountContacts.firstOrNull()
        ?: return conversation
    val accountIndex = conversation.accountContacts.indexOfFirst { account -> account.id == activeAccount.id }
        .coerceAtLeast(0)
    val scopedContacts = accountScopedContacts(
        source = conversation.contacts,
        activeAccount = activeAccount,
        accountIndex = accountIndex,
        count = accountScopedContactCount(activeAccount.id),
        selectedFirst = false
    )
    val scopedGroups = accountScopedContacts(
        source = conversation.groupContacts,
        activeAccount = activeAccount,
        accountIndex = accountIndex,
        count = AccountScopedGroupCount,
        selectedFirst = true
    )
    val contactsByBaseId = scopedContacts.associateBy { contact -> contact.base.id }
    val messages = buildList {
        scopedGroups.forEachIndexed { groupIndex, scopedGroup ->
            val baseMessages = FloatingChatPrototype.groupMessagesFor(
                conversation = conversation,
                groupId = scopedGroup.base.id
            ).ifEmpty {
                FloatingChatPrototype.groupMessagesFor(conversation)
            }.ifEmpty {
                conversation.messages.take(4)
            }
            baseMessages.takeLast(8).forEachIndexed { messageIndex, message ->
                val sender = if (scopedContacts.isNotEmpty()) {
                    scopedContacts[(groupIndex + messageIndex) % scopedContacts.size]
                } else {
                    scopedGroup
                }
                add(
                    message.toAccountScopedMessage(
                        activeAccount = activeAccount,
                        threadId = scopedGroup.contact.id,
                        sender = sender.contact,
                        scopedMessageId = "${scopedGroup.contact.id}-${message.id}-$messageIndex"
                    )
                )
            }
        }
        scopedContacts.forEach { scopedContact ->
            val baseMessages = FloatingChatPrototype.privateMessagesFor(
                conversation = conversation,
                contactId = scopedContact.base.id,
                accountId = activeAccount.id
            ).ifEmpty {
                conversation.messages.filter { message ->
                    message.connectionTarget == FloatingChatConnectionTarget.User ||
                        message.connectionTarget == FloatingChatConnectionTarget.Account
                }.take(4)
            }
            baseMessages.takeLast(6).forEachIndexed { messageIndex, message ->
                add(
                    message.toAccountScopedMessage(
                        activeAccount = activeAccount,
                        threadId = scopedContact.contact.id,
                        sender = scopedContact.contact,
                        scopedMessageId = "${scopedContact.contact.id}-${message.id}-$messageIndex"
                    )
                )
            }
        }
    }
    return conversation.copy(
        peerName = activeAccount.name,
        accountName = activeAccount.name,
        contacts = scopedContacts.map { scoped -> scoped.contact },
        groupContacts = scopedGroups.map { scoped -> scoped.contact },
        accountContacts = conversation.accountContacts.map { account ->
            account.copy(selected = account.id == activeAccount.id)
        },
        messages = messages.distinctBy { message -> message.id }
    )
}

internal data class AccountScopedConversation(
    val accountId: String,
    val conversation: FloatingChatConversation
)

internal fun accountScopedConversations(
    conversation: FloatingChatConversation
): List<AccountScopedConversation> {
    return conversation.accountContacts.map { account ->
        AccountScopedConversation(
            accountId = account.id,
            conversation = accountScopedConversation(
                conversation = conversation,
                activeAccountId = account.id
            )
        )
    }
}

internal fun applyContactProfilesToConversation(
    conversation: FloatingChatConversation,
    profiles: List<LocalContactProfile>
): FloatingChatConversation {
    val accountId = conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: return conversation
    val remarksByContactId = profiles
        .asSequence()
        .filter { profile -> profile.accountId == accountId && profile.remark.isNotBlank() }
        .associate { profile -> profile.contactId to profile.remark.trim() }
    if (remarksByContactId.isEmpty()) return conversation

    fun renamedContact(contact: FloatingChatContact): FloatingChatContact {
        val remark = remarksByContactId[contact.id] ?: return contact
        return contact.copy(
            name = remark,
            initials = remark.take(2).ifBlank { contact.initials }
        )
    }

    return conversation.copy(
        contacts = conversation.contacts.map(::renamedContact),
        messages = conversation.messages.map { message ->
            val targetId = message.connectionTargetId
            val remark = if (message.connectionTarget == FloatingChatConnectionTarget.User && targetId != null) {
                remarksByContactId[targetId]
            } else {
                null
            }
            if (remark == null) message else message.copy(senderName = remark)
        }
    )
}

internal fun applyGroupProfilesToConversation(
    conversation: FloatingChatConversation,
    profiles: List<LocalGroupProfile>,
    accountId: String
): FloatingChatConversation {
    val profilesByGroupId = profiles
        .asSequence()
        .filter { profile -> profile.accountId == accountId }
        .associateBy { profile -> profile.groupId }
    if (profilesByGroupId.isEmpty()) return conversation

    fun renamedGroup(group: FloatingChatContact): FloatingChatContact {
        val profile = profilesByGroupId[group.id] ?: return group
        val displayName = profile.groupName.trim().ifBlank { group.name }
        val displayDescription = profile.remark.trim().ifBlank { group.description }
        return group.copy(
            name = displayName,
            initials = displayName.take(2).ifBlank { group.initials },
            description = displayDescription
        )
    }

    return conversation.copy(
        groupContacts = conversation.groupContacts.map(::renamedGroup)
    )
}

internal fun groupMemberAvatarsVisibleForSelection(
    selection: ChatThreadSelection,
    groupProfilesById: Map<String, LocalGroupProfile>
): Boolean {
    val groupId = when (selection) {
        ChatThreadSelection.Group -> return true
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> return true
    }
    return groupProfilesById[groupId]?.showMemberAvatars ?: true
}

internal fun forwardTargetConversationFor(
    conversation: FloatingChatConversation,
    profiles: List<LocalContactProfile>
): FloatingChatConversation {
    return applyContactProfilesToConversation(conversation, profiles)
}

internal fun allAccountHomeConversation(
    baseConversation: FloatingChatConversation,
    accountConversations: List<AccountScopedConversation>
): FloatingChatConversation {
    return baseConversation.copy(
        peerName = "All accounts",
        contacts = accountConversations.flatMap { scoped -> scoped.conversation.contacts },
        groupContacts = accountConversations.flatMap { scoped -> scoped.conversation.groupContacts },
        messages = accountConversations.flatMap { scoped -> scoped.conversation.messages }
    )
}

internal fun shouldBuildAllAccountHomeOverview(homeOverviewVisible: Boolean): Boolean = homeOverviewVisible

internal fun rightRailAccountAvatarClickSwitchesActiveAccountWorkspace(): Boolean = true

internal fun accountScopedFriendsAndGroupsAreIndependent(): Boolean = true

internal fun accountSwitchResetsThreadToScopedDefault(): Boolean = true

private data class AccountScopedContact(
    val base: FloatingChatContact,
    val contact: FloatingChatContact
)

private fun accountScopedContactCount(accountId: String): Int {
    return if (accountId == StoreServiceAccountId) {
        AccountScopedContactCount * StoreServiceContactMultiplier
    } else {
        AccountScopedContactCount
    }
}

private fun accountScopedContacts(
    source: List<FloatingChatContact>,
    activeAccount: FloatingChatContact,
    accountIndex: Int,
    count: Int,
    selectedFirst: Boolean
): List<AccountScopedContact> {
    if (source.isEmpty()) return emptyList()
    val preScoped = source.filter { contact -> accountIdForScopedThreadId(contact.id) == activeAccount.id }
    if (preScoped.isNotEmpty()) {
        return preScoped.mapIndexed { index, contact ->
            AccountScopedContact(
                base = contact,
                contact = contact.copy(
                    description = "${activeAccount.name} / ${contact.description}",
                    selected = selectedFirst && index == 0
                )
            )
        }
    }
    if (source.any { contact -> accountIdForScopedThreadId(contact.id) != null }) {
        return emptyList()
    }
    val safeCount = min(count, source.size).coerceAtLeast(1)
    val startIndex = (accountIndex * safeCount) % source.size
    return List(safeCount) { offset ->
        val base = source[(startIndex + offset) % source.size]
        AccountScopedContact(
            base = base,
            contact = base.copy(
                id = accountScopedThreadId(activeAccount.id, base.id),
                description = "${activeAccount.name} / ${base.description}",
                selected = selectedFirst && offset == 0
            )
        )
    }
}

private fun accountScopedThreadId(accountId: String, sourceThreadId: String): String {
    return "$accountId$AccountScopedThreadSeparator$sourceThreadId"
}

internal fun accountIdForScopedThreadSelection(selection: ChatThreadSelection): String? {
    val threadId = when (selection) {
        ChatThreadSelection.Group -> return null
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> selection.contactId
    }
    return accountIdForScopedThreadId(threadId)
}

internal fun accountIdForScopedThreadId(threadId: String): String? {
    return threadId.substringBefore(AccountScopedThreadSeparator, missingDelimiterValue = "")
        .takeIf { accountId -> accountId.isNotBlank() && threadId.contains(AccountScopedThreadSeparator) }
}

private fun FloatingChatMessage.toAccountScopedMessage(
    activeAccount: FloatingChatContact,
    threadId: String,
    sender: FloatingChatContact,
    scopedMessageId: String
): FloatingChatMessage {
    val target = when {
        fromMe -> FloatingChatConnectionTarget.Account
        connectionTarget == FloatingChatConnectionTarget.None -> FloatingChatConnectionTarget.None
        else -> FloatingChatConnectionTarget.User
    }
    return copy(
        id = scopedMessageId,
        senderName = if (fromMe) activeAccount.name else sender.name,
        connectionTarget = target,
        connectionTargetId = when (target) {
            FloatingChatConnectionTarget.User -> sender.id
            FloatingChatConnectionTarget.Account -> activeAccount.id
            FloatingChatConnectionTarget.None -> null
        },
        threadContactId = threadId
    )
}

internal fun initialChatThreadSelection(
    conversation: FloatingChatConversation,
    preferredSelection: ChatThreadSelection
): ChatThreadSelection {
    return when (preferredSelection) {
        ChatThreadSelection.Group -> defaultChatThreadSelection(conversation)
        is ChatThreadSelection.GroupChat -> {
            if (conversation.groupContacts.any { group -> group.id == preferredSelection.groupId }) {
                preferredSelection
            } else {
                defaultChatThreadSelection(conversation)
            }
        }
        is ChatThreadSelection.Private -> {
            if (conversation.contacts.any { contact -> contact.id == preferredSelection.contactId }) {
                preferredSelection
            } else {
                defaultChatThreadSelection(conversation)
            }
        }
    }
}

internal fun selectedAccountForThread(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    overrideAccountId: String? = null
): FloatingChatContact {
    if (!overrideAccountId.isNullOrBlank()) {
        conversation.accountContacts.firstOrNull { account -> account.id == overrideAccountId }?.let { account ->
            return account
        }
    }
    val contactId = when (selection) {
        ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull { it.selected }?.id
            ?: conversation.groupContacts.firstOrNull()?.id
            ?: conversation.contacts.firstOrNull()?.id
            ?: ""
        is ChatThreadSelection.GroupChat -> selection.groupId
        is ChatThreadSelection.Private -> selection.contactId
    }
    return FloatingChatPrototype.pairedAccountFor(
        conversation = conversation,
        contactId = contactId
    )
}

internal fun selectedAccountForCoordinateBody(
    conversation: FloatingChatConversation,
    selectedThread: ChatThreadSelection,
    activeAccountId: String
): FloatingChatContact {
    return selectedAccountForThread(
        conversation = conversation,
        selection = selectedThread,
        overrideAccountId = activeAccountId
    )
}

internal fun visibleMessagesForThread(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    selectedAccountId: String
): List<FloatingChatMessage> {
    return when (selection) {
        ChatThreadSelection.Group -> FloatingChatPrototype.groupMessagesFor(conversation)
        is ChatThreadSelection.GroupChat -> FloatingChatPrototype.groupMessagesFor(conversation, selection.groupId)
        is ChatThreadSelection.Private -> FloatingChatPrototype.privateMessagesFor(
            conversation = conversation,
            contactId = selection.contactId,
            accountId = selectedAccountId
        )
    }
}

internal fun previewableThreadMedia(
    messages: List<FloatingChatMessage>
): List<FloatingChatMessage> {
    return messages.filter { message ->
        message.type == FloatingChatMessageType.ImageThumbnail ||
            message.type == FloatingChatMessageType.VideoPreview
    }
}

internal data class HomeUnreadThreadSummary(
    val accountId: String,
    val threadId: String,
    val selection: ChatThreadSelection,
    val message: FloatingChatMessage,
    val unreadCount: Int,
    val avatarContact: FloatingChatContact
)

internal data class HomeOverviewMessageGroup(
    val avatarContactId: String?,
    val accountId: String,
    val connectorId: String,
    val messages: List<FloatingChatMessage>
) {
    val key: String = messages.firstOrNull()?.id ?: "home-overview-empty"
}

internal fun homeOverviewMessageGroups(
    messages: List<FloatingChatMessage>,
    accountIdsByMessageId: Map<String, String> = emptyMap()
): List<HomeOverviewMessageGroup> {
    val groupsByContactAndAccount = linkedMapOf<Pair<String?, String>, HomeOverviewMessageGroup>()
    messages.forEach { message ->
        val avatarContactId = message.connectionTargetId
        val accountId = accountIdsByMessageId[message.id].orEmpty()
        val groupKey = avatarContactId to accountId
        val existing = groupsByContactAndAccount[groupKey]
        groupsByContactAndAccount[groupKey] = if (existing == null) {
            HomeOverviewMessageGroup(
                avatarContactId = avatarContactId,
                accountId = accountId,
                connectorId = "home-group:${accountId}:${avatarContactId}:${message.id}",
                messages = listOf(message)
            )
        } else {
            existing.copy(messages = existing.messages + message)
        }
    }
    return groupsByContactAndAccount.values.toList()
}

internal fun homeOverviewMessagesForVisibleGroup(
    groups: List<HomeOverviewMessageGroup>,
    groupIndex: Int
): List<FloatingChatMessage> = groups.getOrNull(groupIndex)?.messages.orEmpty()

internal fun homeOverviewVisibleGroups(
    groups: List<HomeOverviewMessageGroup>,
    visibleGroupIndexes: Set<Int>
): List<HomeOverviewMessageGroup> = groups.filterIndexed { index, _ -> index in visibleGroupIndexes }

internal fun defaultHomeUnreadThreadIds(conversation: FloatingChatConversation): Set<String> {
    return defaultHomeUnreadTextSelections(conversation)
        .take(DefaultHomeUnreadThreadLimit)
        .map { selection -> selection.toLocalThreadId() }
        .toSet()
}

internal fun defaultAllAccountHomeUnreadThreadIds(
    conversation: FloatingChatConversation
): Set<String> {
    return homeUnreadThreadSummaries(accountScopedConversations(conversation))
        .map { summary -> summary.threadId }
        .toSet()
}

private fun MutableList<ChatThreadSelection>.addRoundRobinUnreadSelections(
    candidatesByAccount: List<List<ChatThreadSelection>>,
    limit: Int
) {
    if (limit <= 0) return
    val maxSize = (size + limit).coerceAtMost(DefaultHomeUnreadThreadLimit)
    val maxCandidateCount = candidatesByAccount.maxOfOrNull { candidates -> candidates.size } ?: return
    for (candidateIndex in 0 until maxCandidateCount) {
        candidatesByAccount.forEach { candidates ->
            val selection = candidates.getOrNull(candidateIndex) ?: return@forEach
            if (none { existing -> existing.toLocalThreadId() == selection.toLocalThreadId() }) {
                add(selection)
                if (size >= maxSize) return
            }
        }
        if (size >= maxSize) return
    }
}

private fun defaultHomeUnreadTextSelections(
    conversation: FloatingChatConversation
): List<ChatThreadSelection> {
    return homeUnreadCandidateSelections(conversation).filter { selection ->
        latestHomeUnreadTextMessageForSelection(conversation, selection) != null
    }
}

internal fun homeUnreadCandidateSelections(
    conversation: FloatingChatConversation
): List<ChatThreadSelection> {
    val contactIds = conversation.contacts.asSequence()
        .map { contact -> contact.id }
        .toHashSet()
    val groupIds = conversation.groupContacts.asSequence()
        .map { group -> group.id }
        .toHashSet()
    val selectionsByThreadId = linkedMapOf<String, ChatThreadSelection>()

    fun addSelection(selection: ChatThreadSelection) {
        selectionsByThreadId.putIfAbsent(selection.toLocalThreadId(), selection)
    }

    conversation.messages.forEach { message ->
        val explicitThreadId = message.threadContactId?.takeIf { threadId -> threadId.isNotBlank() }
        when {
            explicitThreadId != null && explicitThreadId in groupIds -> {
                addSelection(ChatThreadSelection.GroupChat(explicitThreadId))
            }
            explicitThreadId != null && explicitThreadId in contactIds -> {
                addSelection(ChatThreadSelection.Private(explicitThreadId))
            }
            message.connectionTarget == FloatingChatConnectionTarget.User -> {
                val targetId = message.connectionTargetId?.takeIf { id -> id.isNotBlank() }
                if (targetId != null && targetId in contactIds) {
                    addSelection(ChatThreadSelection.Private(targetId))
                }
            }
        }
    }

    return selectionsByThreadId.values.toList()
}

private fun latestHomeUnreadTextMessageForSelection(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection
): FloatingChatMessage? {
    if (conversation.accountContacts.isEmpty()) return null
    val selectedAccountId = selectedAccountForThread(conversation, selection).id
    return visibleMessagesForThread(
        conversation = conversation,
        selection = selection,
        selectedAccountId = selectedAccountId
    ).homeUnrepliedTextMessages().lastOrNull()
}

private fun List<FloatingChatMessage>.homeUnrepliedTextMessages(): List<FloatingChatMessage> {
    val lastSelfReplyIndex = indexOfLast { message -> message.isHomeSelfReplyMessage() }
    return drop(lastSelfReplyIndex + 1)
        .filter { message -> message.isHomeUnrepliedTextMessage() }
}

private fun FloatingChatMessage.isHomeSelfReplyMessage(): Boolean {
    return fromMe &&
        kind != FloatingChatMessageKind.AiDraft &&
        presentation != FloatingChatMessagePresentation.System
}

private fun FloatingChatMessage.isHomeUnrepliedTextMessage(): Boolean {
    return !fromMe &&
        type == FloatingChatMessageType.Text &&
        presentation == FloatingChatMessagePresentation.Bubble &&
        connectionTarget == FloatingChatConnectionTarget.User &&
        text.isNotBlank()
}

internal fun homeUnreadThreadSummaries(
    conversation: FloatingChatConversation
): List<HomeUnreadThreadSummary> {
    val accountId = conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: ""
    return homeUnreadThreadSummariesForAccount(
        accountId = accountId,
        conversation = conversation
    )
}

internal fun homeUnreadThreadSummaries(
    accountConversations: List<AccountScopedConversation>
): List<HomeUnreadThreadSummary> {
    return accountConversations.flatMap { scoped ->
        homeUnreadThreadSummariesForAccount(
            accountId = scoped.accountId,
            conversation = scoped.conversation
        )
    }
}

internal fun homeUnreadDemoThreadSummaries(
    conversation: FloatingChatConversation
): List<HomeUnreadThreadSummary> {
    val contactIds = conversation.contacts.map { contact -> contact.id }.toSet()
    val groupIds = conversation.groupContacts.map { group -> group.id }.toSet()
    val fallbackAccountId = conversation.accountContacts.firstOrNull { account -> account.selected }?.id
        ?: conversation.accountContacts.firstOrNull()?.id
        ?: ""
    return conversation.homeUnreadDemoMessages.mapNotNull { message ->
            val resolvedThreadId = message.threadContactId?.takeIf { id -> id.isNotBlank() }
                ?: return@mapNotNull null
            val selection = when {
                resolvedThreadId in groupIds -> ChatThreadSelection.GroupChat(resolvedThreadId)
                resolvedThreadId in contactIds -> ChatThreadSelection.Private(resolvedThreadId)
                else -> return@mapNotNull null
            }
            homeUnreadThreadSummary(
                accountId = accountIdForScopedThreadId(resolvedThreadId) ?: fallbackAccountId,
                conversation = conversation,
                selection = selection,
                unrepliedMessages = listOf(message).homeUnrepliedTextMessages()
            )
        }
}

private fun homeUnreadThreadSummariesForAccount(
    accountId: String,
    conversation: FloatingChatConversation
): List<HomeUnreadThreadSummary> {
    return homeUnreadCandidateSelections(conversation).mapNotNull { selection ->
        val threadId = selection.toLocalThreadId()
        val selectedAccountId = selectedAccountForThread(conversation, selection).id
        val threadMessages = visibleMessagesForThread(
            conversation = conversation,
            selection = selection,
            selectedAccountId = selectedAccountId
        )
        val unrepliedMessages = threadMessages.homeUnrepliedTextMessages()
        homeUnreadThreadSummary(
            accountId = accountId,
            conversation = conversation,
            selection = selection,
            unrepliedMessages = unrepliedMessages
        )
    }
}

private fun homeUnreadThreadSummary(
    accountId: String,
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    unrepliedMessages: List<FloatingChatMessage>
): HomeUnreadThreadSummary? {
    val latest = unrepliedMessages.lastOrNull() ?: return null
    val contact = contactForSelection(conversation, selection) ?: return null
    val groupMember = if (selection.isGroupThread()) {
        latest.connectionTargetId
            ?.let { targetId -> conversation.contacts.firstOrNull { candidate -> candidate.id == targetId } }
    } else {
        null
    }
    val avatarContact = groupMember ?: contact
    val unreadCount = unrepliedMessages.size.coerceAtLeast(1)
    val threadId = selection.toLocalThreadId()
    return HomeUnreadThreadSummary(
        accountId = accountId,
        threadId = threadId,
        selection = selection,
        unreadCount = unreadCount,
        avatarContact = avatarContact,
        message = latest.copy(
            id = "home-unread-${threadId}-${latest.id}",
            fromMe = false,
            senderName = homeUnreadSenderLabel(
                contact = contact,
                groupMember = groupMember,
                accountName = conversation.accountName
            ),
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = avatarContact.id,
            threadContactId = selection.threadContactIdForHome()
        )
    )
}

private fun homeUnreadSenderLabel(
    contact: FloatingChatContact,
    groupMember: FloatingChatContact?,
    accountName: String
): String {
    val prefix = if (groupMember == null) {
        contact.name
    } else {
        "${contact.name} · ${groupMember.name}"
    }
    return "$prefix - $accountName"
}

internal fun unreadThreadIdsAfterOpeningHomeUnreadBubble(
    unreadThreadIds: Set<String>,
    summary: HomeUnreadThreadSummary
): Set<String> {
    return unreadThreadIds - summary.threadId
}

private fun contactForSelection(
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection
): FloatingChatContact? {
    return when (selection) {
        ChatThreadSelection.Group -> conversation.groupContacts.firstOrNull()
        is ChatThreadSelection.GroupChat -> conversation.groupContacts.firstOrNull { group -> group.id == selection.groupId }
        is ChatThreadSelection.Private -> conversation.contacts.firstOrNull { contact -> contact.id == selection.contactId }
    }
}

private fun ChatThreadSelection.homeConnectorTargetId(): String {
    return when (this) {
        ChatThreadSelection.Group -> GroupThreadId
        is ChatThreadSelection.GroupChat -> groupId.groupConnectorId()
        is ChatThreadSelection.Private -> contactId
    }
}

private fun ChatThreadSelection.threadContactIdForHome(): String? {
    return when (this) {
        ChatThreadSelection.Group -> null
        is ChatThreadSelection.GroupChat -> groupId
        is ChatThreadSelection.Private -> contactId
    }
}

internal fun homeUnreadOverviewUsesLatestMessagePerThread(): Boolean = true

internal fun homeUnreadOverviewUsesUnrepliedMessagesAfterLastSelfReply(): Boolean = true

internal fun homeUnreadOverviewBubblesJumpToThread(): Boolean = true

internal fun homeUnreadOverviewClearsUnreadAfterOpen(): Boolean = true

internal fun homeUnreadOverviewKeepsConnectorLines(): Boolean = true

internal fun homeUnreadAvatarGreenDotReflectsThreadState(): Boolean = true

internal fun homeUnreadOverviewUsesSourceScopedConnectorLines(): Boolean = true

internal fun homeOverviewUsesMessageAttachedAvatars(): Boolean = true

internal fun homeOverviewConnectorLayerIsIndependentFromMessageList(): Boolean = true

internal fun homeOverviewAvatarUsesSessionRailLayout(): Boolean = true

internal fun homeOverviewAvatarSizeDp(): Int = leftRailAvatarSizeDp()

private val HomeOverviewAccountPalette = listOf(
    0xFFFF3B5C, 0xFFFF8A00, 0xFFFFD60A, 0xFF35C759,
    0xFF00C7BE, 0xFF00A6FB, 0xFF3A86FF, 0xFF7B61FF,
    0xFFB845FF, 0xFFFF4FA3, 0xFF9ADE00, 0xFF00D1FF
)

internal fun homeOverviewAccountColorsById(
    accounts: List<FloatingChatContact>
): Map<String, Long> = accounts.mapIndexed { index, account ->
    account.id to HomeOverviewAccountPalette[index % HomeOverviewAccountPalette.size]
}.toMap()

internal fun homeOverviewUsesTwelveColorAccountPalette(): Boolean =
    HomeOverviewAccountPalette.size == 12

internal fun homeUnreadOverviewUsesMessageScopedConnectorLines(): Boolean = false

internal fun homeUnreadOverviewShowsAllAccounts(): Boolean = true

internal fun homeUnreadBubbleSwitchesToOwningAccount(): Boolean = true

internal fun homeUnreadOverviewTracksAllUnrepliedThreads(): Boolean = true

internal fun homeUnreadOverviewSuppressesGroupMemberAvatars(): Boolean = true

internal fun homeUnreadOverviewUsesFallbackConnectorSourceWhenRailAvatarIsOffscreen(): Boolean = true

internal fun groupMemberContactForMessage(
    message: FloatingChatMessage,
    selectedThread: ChatThreadSelection,
    homeOverviewVisible: Boolean,
    contactsById: Map<String, FloatingChatContact>,
    groupMemberAvatarsVisible: Boolean
): FloatingChatContact? {
    if (homeOverviewVisible) return null
    if (!selectedThread.isGroupThread()) return null
    if (!groupMemberAvatarsVisible) return null
    if (message.connectionTarget != FloatingChatConnectionTarget.User) return null
    val targetId = message.connectionTargetId ?: return null
    return contactsById[targetId]
}

private const val AccountScopedThreadSeparator = "__"
private const val AccountScopedContactCount = 5
private const val AccountScopedGroupCount = 2
private const val StoreServiceAccountId = "account-store"
private const val StoreServiceContactMultiplier = 3
private const val DefaultHomeUnreadThreadLimit = 5
