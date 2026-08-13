package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFlowActivityContractTest {
    @Test
    fun transferFlowProvidesRecipientSelectionAndTransferForm() {
        val source = File("src/main/java/com/paifa/ubikitouch/app/TransferFlowActivity.kt").readText()

        assertTrue(source.contains("StatusBarHeight = 30.dp"))
        assertTrue(source.contains("选择收款人"))
        assertTrue(source.contains("下一步"))
        assertTrue(source.contains("转账"))
        assertTrue(source.contains("转账给"))
        assertTrue(source.contains("value = \"88\""))
        assertTrue(source.contains("金额"))
        assertTrue(source.contains("备注"))
        assertTrue(source.contains("0xFFF6C343"))
        assertTrue(source.contains("支付密码"))
        assertTrue(source.contains("FloatingChatTransferBridge.submit"))
    }
}
