package com.paifa.ubikitouch.accessibility.floatingchat.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class MoreToolPanelContractTest {
    @Test
    fun moreToolPanelDoesNotRepeatTheQuickPhraseDestination() {
        assertTrue(moreToolPanelUsesUniqueDestinations())
    }
}
