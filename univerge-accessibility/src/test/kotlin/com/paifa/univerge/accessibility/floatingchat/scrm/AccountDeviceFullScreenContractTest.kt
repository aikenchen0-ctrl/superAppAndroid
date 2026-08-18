package com.paifa.univerge.accessibility.floatingchat.scrm

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeviceFullScreenContractTest {
    @Test
    fun fullScreenAccountDeviceTabsCoverDeviceAccountAndActions() {
        assertEquals(
            listOf(AccountDeviceFullScreenTab.Devices, AccountDeviceFullScreenTab.Accounts, AccountDeviceFullScreenTab.Actions),
            AccountDeviceFullScreenTab.entries
        )
    }
}
