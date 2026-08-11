package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile

/** 编辑器只保存草稿和服务端快照，不持有网络或 Compose 状态。 */
internal data class CustomerProfileDraft(
    val customerLevel: String? = null,
    val sourceChannel: String? = null,
    val sourceDetail: String? = null,
    val profileKey: String? = null,
    val purchaseHistory: String? = null,
    val socialAccounts: String? = null,
    val faceImageUrl: String? = null,
    val notes: String? = null,
    val mappedLabelIds: List<Int> = emptyList(),
    val mappedLabelNames: List<String> = emptyList()
)

internal data class ContactLabelsDraft(
    val selectedLabelIds: Set<Int> = emptySet(),
    val selectedLabelNames: Set<String> = emptySet()
)

internal enum class PermissionChoice { Unknown, Enabled, Disabled }

internal data class FriendPermissionsDraft(
    val onlyChat: PermissionChoice,
    val notSeeFriendMoments: PermissionChoice,
    val notLetFriendSeeMyMoments: PermissionChoice
)

internal fun permissionDraftFromContact(contact: ScrmContact): FriendPermissionsDraft {
    if (!contact.friendPermissionSynced) {
        return FriendPermissionsDraft(
            PermissionChoice.Unknown,
            PermissionChoice.Unknown,
            PermissionChoice.Unknown
        )
    }
    val mask = contact.friendPermissionMask
    return if (mask == 8) {
        FriendPermissionsDraft(PermissionChoice.Enabled, PermissionChoice.Disabled, PermissionChoice.Disabled)
    } else {
        FriendPermissionsDraft(
            onlyChat = PermissionChoice.Disabled,
            notSeeFriendMoments = if (mask and 1 != 0) PermissionChoice.Enabled else PermissionChoice.Disabled,
            notLetFriendSeeMyMoments = if (mask and 2 != 0) PermissionChoice.Enabled else PermissionChoice.Disabled
        )
    }
}

internal fun permissionRequestValues(
    draft: FriendPermissionsDraft
): Pair<Int, Triple<Boolean, Boolean, Boolean>> {
    require(draft.onlyChat != PermissionChoice.Unknown &&
        draft.notSeeFriendMoments != PermissionChoice.Unknown &&
        draft.notLetFriendSeeMyMoments != PermissionChoice.Unknown) {
        "permission choices must be known before saving"
    }
    if (draft.onlyChat == PermissionChoice.Enabled) return 8 to Triple(true, false, false)
    val notSee = draft.notSeeFriendMoments == PermissionChoice.Enabled
    val notLet = draft.notLetFriendSeeMyMoments == PermissionChoice.Enabled
    return (if (notSee) 1 else 0) + (if (notLet) 2 else 0) to Triple(false, notSee, notLet)
}

internal fun permissionRequestValuesOrNull(
    draft: FriendPermissionsDraft
): Pair<Int, Triple<Boolean, Boolean, Boolean>>? =
    runCatching { permissionRequestValues(draft) }.getOrNull()

internal fun normalizeCustomerProfileDraft(draft: CustomerProfileDraft): CustomerProfileDraft = draft.copy(
    customerLevel = draft.customerLevel.cleanOptional(),
    sourceChannel = draft.sourceChannel.cleanOptional(),
    sourceDetail = draft.sourceDetail.cleanOptional(),
    profileKey = draft.profileKey.cleanOptional(),
    purchaseHistory = draft.purchaseHistory.cleanOptional(),
    socialAccounts = draft.socialAccounts.cleanOptional(),
    faceImageUrl = draft.faceImageUrl.cleanOptional(),
    notes = draft.notes.cleanOptional(),
    mappedLabelIds = draft.mappedLabelIds.filter { it > 0 }.distinct().sorted(),
    mappedLabelNames = draft.mappedLabelNames.mapNotNull { it.cleanOptional() }.distinct().sorted()
)

internal fun labelsChangeIsDestructive(before: ContactLabelsDraft, after: ContactLabelsDraft): Boolean =
    before.selectedLabelIds.isNotEmpty() && after.selectedLabelIds.isEmpty() &&
        before.selectedLabelNames.isNotEmpty() && after.selectedLabelNames.isEmpty()

internal fun profileHasChanges(before: ScrmCustomerProfile?, after: CustomerProfileDraft): Boolean {
    val normalized = normalizeCustomerProfileDraft(after)
    val serverDraft = before?.let {
        CustomerProfileDraft(
            customerLevel = it.customerLevel,
            sourceChannel = it.sourceChannel,
            sourceDetail = it.sourceDetail,
            profileKey = it.profileKey,
            purchaseHistory = it.purchaseHistory,
            socialAccounts = it.socialAccounts,
            faceImageUrl = it.faceImageUrl,
            notes = it.notes,
            mappedLabelIds = it.mappedLabelIds,
            mappedLabelNames = it.mappedLabelNames
        )
    }
    return normalized != normalizeCustomerProfileDraft(serverDraft ?: CustomerProfileDraft())
}

private fun String?.cleanOptional(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
