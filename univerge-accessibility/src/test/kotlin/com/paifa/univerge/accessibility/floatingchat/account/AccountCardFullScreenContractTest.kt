package com.paifa.univerge.accessibility.floatingchat.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountCardFullScreenContractTest {
    @Test
    fun cardFullScreenUsesAccountSelectionAndPreviewPages() {
        assertEquals(
            listOf(AccountCardFullScreenTab.Accounts, AccountCardFullScreenTab.Preview),
            AccountCardFullScreenTab.entries
        )
    }
}
