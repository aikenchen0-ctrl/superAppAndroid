package com.paifa.univerge.accessibility.floatingchat.group

import com.paifa.univerge.accessibility.scrm.ScrmGroupInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupInvitationContractTest {
    @Test
    fun invitationTabsMapToDocumentedPendingFilter() {
        assertTrue(groupInvitationPendingOnlyFor(GroupInvitationTab.Pending))
        assertFalse(groupInvitationPendingOnlyFor(GroupInvitationTab.All))
    }

    @Test
    fun selfInviteRequiresTalkerAndUsesAgreeEndpoint() {
        val invitation = ScrmGroupInvitation(
            chatRoomId = "room@chatroom",
            source = "self",
            reason = "<msg />",
            msgSvrId = 9L
        )

        assertEquals(GroupInvitationAction.Agree, groupInvitationActionFor(invitation))
        assertFalse(groupInvitationCanSubmit(invitation))
        assertTrue(groupInvitationCanSubmit(invitation.copy(inviter = "room@chatroom")))
    }

    @Test
    fun memberInviteUsesApproveEndpointAndRequiresMessageContent() {
        val invitation = ScrmGroupInvitation(
            chatRoomId = "room@chatroom",
            source = "member_apply",
            reason = "<msg />",
            msgSvrId = 12L,
            msgId = 2L
        )

        assertEquals(GroupInvitationAction.Approve, groupInvitationActionFor(invitation))
        assertTrue(groupInvitationCanSubmit(invitation))
        assertFalse(groupInvitationCanSubmit(invitation.copy(reason = null)))
    }
}
