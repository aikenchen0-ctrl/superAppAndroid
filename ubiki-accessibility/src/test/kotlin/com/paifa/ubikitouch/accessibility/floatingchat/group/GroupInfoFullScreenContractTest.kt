package com.paifa.ubikitouch.accessibility.floatingchat.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupInfoFullScreenContractTest {
    @Test
    fun fullScreenTabsExposeProfileMembersAndSettings() {
        assertEquals(
            listOf(GroupInfoFullScreenTab.Profile, GroupInfoFullScreenTab.Members, GroupInfoFullScreenTab.Settings),
            GroupInfoFullScreenTab.entries
        )
    }

    @Test
    fun atLeastOneMemberIdentityFieldMustRemainVisible() {
        assertFalse(groupInfoCanHideMemberIdentity(showAvatars = false, showNames = false))
        assertTrue(groupInfoCanHideMemberIdentity(showAvatars = true, showNames = false))
        assertTrue(groupInfoCanHideMemberIdentity(showAvatars = false, showNames = true))
    }
}
