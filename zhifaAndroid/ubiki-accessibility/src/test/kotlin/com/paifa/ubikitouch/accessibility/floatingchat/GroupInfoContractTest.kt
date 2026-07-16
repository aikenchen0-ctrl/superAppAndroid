package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupInfoUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.groupInfoAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupInfoContractTest {
    @Test
    fun navigationAndMemberEventsMapToHostActions() {
        assertEquals(GroupInfoAction.Back, groupInfoAction(GroupInfoUiEvent.BackRequested))
        assertEquals(GroupInfoAction.InviteMembers, groupInfoAction(GroupInfoUiEvent.AddMemberRequested))
        assertEquals(GroupInfoAction.RemoveMembers, groupInfoAction(GroupInfoUiEvent.RemoveMemberRequested))
        assertEquals(
            GroupInfoAction.OpenMember("member-1"),
            groupInfoAction(GroupInfoUiEvent.MemberSelected("member-1"))
        )
    }

    @Test
    fun editableValuesMapWithoutPlatformTypes() {
        assertEquals(GroupInfoAction.UpdateGroupName("New name"), groupInfoAction(GroupInfoUiEvent.GroupNameChanged("New name")))
        assertEquals(GroupInfoAction.UpdateAnnouncement("Notice"), groupInfoAction(GroupInfoUiEvent.AnnouncementChanged("Notice")))
        assertEquals(GroupInfoAction.UpdateRemark("Remark"), groupInfoAction(GroupInfoUiEvent.RemarkChanged("Remark")))
        assertEquals(GroupInfoAction.UpdateMyNickname("Me"), groupInfoAction(GroupInfoUiEvent.MyNicknameChanged("Me")))
        assertEquals(GroupInfoAction.UpdateBackground("Blue"), groupInfoAction(GroupInfoUiEvent.BackgroundChanged("Blue")))
    }

    @Test
    fun switchesMapWithoutLosingTheirValues() {
        assertEquals(GroupInfoAction.SetMuted(true), groupInfoAction(GroupInfoUiEvent.MutedChanged(true)))
        assertEquals(GroupInfoAction.SetPinned(false), groupInfoAction(GroupInfoUiEvent.PinnedChanged(false)))
        assertEquals(GroupInfoAction.SetSavedToContacts(true), groupInfoAction(GroupInfoUiEvent.SavedToContactsChanged(true)))
        assertEquals(GroupInfoAction.SetMemberNicknamesVisible(false), groupInfoAction(GroupInfoUiEvent.MemberNicknamesVisibleChanged(false)))
        assertEquals(GroupInfoAction.SetMemberAvatarsVisible(true), groupInfoAction(GroupInfoUiEvent.MemberAvatarsVisibleChanged(true)))
    }

    @Test
    fun commandEventsMapToExplicitHostActions() {
        assertEquals(GroupInfoAction.RenameGroup, groupInfoAction(GroupInfoUiEvent.RenameRequested))
        assertEquals(GroupInfoAction.PublishAnnouncement, groupInfoAction(GroupInfoUiEvent.PublishAnnouncementRequested))
        assertEquals(GroupInfoAction.LoadQrCode, groupInfoAction(GroupInfoUiEvent.QrCodeRequested))
        assertEquals(GroupInfoAction.SearchChatHistory, groupInfoAction(GroupInfoUiEvent.SearchChatHistoryRequested))
        assertEquals(GroupInfoAction.ClearChatHistory, groupInfoAction(GroupInfoUiEvent.ClearChatHistoryRequested))
        assertEquals(GroupInfoAction.Report, groupInfoAction(GroupInfoUiEvent.ReportRequested))
        assertEquals(GroupInfoAction.ExitGroup, groupInfoAction(GroupInfoUiEvent.ExitGroupRequested))
    }
}
