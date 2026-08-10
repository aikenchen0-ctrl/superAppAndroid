package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.GroupMemberUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.groupMemberAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMemberContractTest {
    @Test
    fun memberEventsMapToPlatformIndependentActions() {
        assertEquals(GroupMemberAction.Back, groupMemberAction(GroupMemberUiEvent.BackRequested))
        assertEquals(GroupMemberAction.OpenChat, groupMemberAction(GroupMemberUiEvent.OpenChatRequested))
        assertEquals(GroupMemberAction.OpenProfile, groupMemberAction(GroupMemberUiEvent.OpenProfileRequested))
        assertEquals(GroupMemberAction.OpenMoments, groupMemberAction(GroupMemberUiEvent.OpenMomentsRequested))
        assertEquals(GroupMemberAction.StartVideoCall, groupMemberAction(GroupMemberUiEvent.StartVideoCallRequested))
        assertEquals(GroupMemberAction.AddFriend, groupMemberAction(GroupMemberUiEvent.AddFriendRequested))
    }
}
