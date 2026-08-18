package com.paifa.univerge.accessibility.floatingchat.group

import com.paifa.univerge.accessibility.scrm.ScrmGroupInvitation

internal enum class GroupInvitationAction {
    Agree,
    Approve
}

internal enum class GroupInvitationTab(val label: String) {
    Pending("待处理"),
    All("全部记录")
}

internal fun groupInvitationPendingOnlyFor(tab: GroupInvitationTab): Boolean {
    return tab == GroupInvitationTab.Pending
}

internal fun groupInvitationActionFor(invitation: ScrmGroupInvitation): GroupInvitationAction {
    return if (invitation.source.orEmpty().contains("self", ignoreCase = true)) {
        GroupInvitationAction.Agree
    } else {
        GroupInvitationAction.Approve
    }
}

internal fun groupInvitationCanSubmit(invitation: ScrmGroupInvitation): Boolean {
    if (invitation.msgSvrId <= 0L || invitation.reason.isNullOrBlank()) return false
    return groupInvitationActionFor(invitation) != GroupInvitationAction.Agree || !invitation.inviter.isNullOrBlank()
}
