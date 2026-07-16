package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.LocationUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.PaymentUiEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolContractTest {
    @Test
    fun paymentAndLocationEventsRemainPlatformIndependent() {
        assertEquals("12", (PaymentUiEvent.AmountChanged("12")).value)
        assertEquals("loc-1", (LocationUiEvent.SendRequested("loc-1")).optionId)
    }
}
