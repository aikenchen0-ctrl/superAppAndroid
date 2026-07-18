package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileEditorAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiState
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlin.math.abs

internal sealed interface ContactEditorTarget {
    data class Group(val group: FloatingChatContact) : ContactEditorTarget
    data class User(val contact: FloatingChatContact) : ContactEditorTarget
}

internal fun mergeContactProfileDraft(
    profile: LocalContactProfile,
    draft: ContactProfileUiState,
    action: ContactProfileEditorAction,
    updatedAt: Long
): LocalContactProfile? {
    val updatedDraft = when (action) {
        is ContactProfileEditorAction.UpdateRemark -> draft.copy(
            displayName = action.value.ifBlank { draft.originalName },
            remark = action.value
        )
        is ContactProfileEditorAction.UpdateTags -> draft.copy(tags = action.value)
        is ContactProfileEditorAction.UpdateMemo -> draft.copy(memo = action.value)
        is ContactProfileEditorAction.SetFriendCircleVisibility -> {
            draft.copy(friendCircleVisible = action.visible)
        }
        is ContactProfileEditorAction.SetOnlyChat -> draft.copy(onlyChat = action.enabled)
        else -> return null
    }
    return profile.copy(
        remark = updatedDraft.remark,
        tags = updatedDraft.tags,
        memo = updatedDraft.memo,
        friendCircleVisible = updatedDraft.friendCircleVisible,
        onlyChat = updatedDraft.onlyChat,
        updatedAt = updatedAt
    )
}

internal fun contactProfileKey(accountId: String, contactId: String): String {
    return "$accountId\t$contactId"
}

internal fun groupProfileKey(accountId: String, groupId: String): String {
    return "$accountId\t$groupId"
}

internal fun defaultLocalContactProfileFor(
    accountId: String,
    contact: FloatingChatContact
): LocalContactProfile {
    val seed = positiveContactSeed(contact.id)
    return LocalContactProfile(
        accountId = accountId,
        contactId = contact.id,
        memo = defaultFriendProfileMemo(contact),
        friendCircleVisible = true,
        onlyChat = false,
        phone = friendProfilePhoneFor(seed),
        source = FriendProfileSources[seed % FriendProfileSources.size],
        addedTime = FriendProfileAddedTimes[seed % FriendProfileAddedTimes.size],
        commonGroupCount = seed % 5 + 1,
        updatedAt = 0L
    )
}

internal fun defaultLocalGroupProfileFor(
    accountId: String,
    group: FloatingChatContact
): LocalGroupProfile {
    return LocalGroupProfile(
        accountId = accountId,
        groupId = group.id,
        groupName = group.name,
        remark = group.description.substringBefore(" 路 ").ifBlank { group.description },
        announcement = "",
        myNickname = group.initials.ifBlank { group.name.take(2) },
        mute = false,
        pinned = false,
        saveToContacts = false,
        showMemberNicknames = true,
        showMemberAvatars = true,
        backgroundLabel = "榛樿鑳屾櫙",
        updatedAt = 0L
    )
}

internal fun defaultFriendProfileMemo(contact: FloatingChatContact): String {
    return contact.description.substringBefore(" 路 ").ifBlank { contact.description }
}

private fun friendProfilePhoneFor(seed: Int): String {
    val middle = 1000 + seed % 9000
    val suffix = 1000 + (seed / 7) % 9000
    return "1${FriendProfilePhonePrefixes[seed % FriendProfilePhonePrefixes.size]} $middle $suffix"
}

private fun positiveContactSeed(id: String): Int {
    val hash = id.hashCode()
    return if (hash == Int.MIN_VALUE) 0 else abs(hash)
}

private val FriendProfilePhonePrefixes = listOf("36", "37", "38", "39", "58", "77", "88")
private val FriendProfileSources = listOf("名片分享", "搜索添加", "群聊添加", "二维码", "通讯录")
private val FriendProfileAddedTimes = listOf("2024年3月8日", "2024年6月2日", "2025年1月1日", "2025年9月4日", "2026年2月8日")
