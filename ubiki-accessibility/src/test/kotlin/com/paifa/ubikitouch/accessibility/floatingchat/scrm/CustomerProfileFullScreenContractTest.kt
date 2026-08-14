package com.paifa.ubikitouch.accessibility.floatingchat.scrm

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerProfileFullScreenContractTest {
    @Test
    fun fullScreenCustomerProfileTabsCoverProfileLabelsAndEditor() {
        assertEquals(
            listOf(
                CustomerProfileFullScreenTab.Profile,
                CustomerProfileFullScreenTab.LabelsAndHistory,
                CustomerProfileFullScreenTab.Editor
            ),
            CustomerProfileFullScreenTab.entries
        )
    }

    @Test
    fun labelInputIsTrimmedDeduplicatedAndDropsBlankValues() {
        assertEquals(
            listOf("高意向", "已购买"),
            normalizeCustomerProfileLabels(" 高意向, ,已购买,高意向 ")
        )
    }
}
