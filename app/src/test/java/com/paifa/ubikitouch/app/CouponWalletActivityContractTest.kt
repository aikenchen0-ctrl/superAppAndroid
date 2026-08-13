package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponWalletActivityContractTest {
    @Test
    fun couponWalletDefinesTheRequiredTopBarAndEntries() {
        val source = File("src/main/java/com/paifa/ubikitouch/app/CouponWalletActivity.kt").readText()

        assertTrue(source.contains("StatusBarHeight = 30.dp"))
        assertTrue(source.contains("text = \"卡包\""))
        assertTrue(source.contains("交通卡"))
        assertTrue(source.contains("券和礼品卡"))
        assertTrue(source.contains("票证"))
        assertTrue(source.contains("会员卡"))
        assertTrue(source.contains("最近使用"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(source.contains("Icons.Filled.MoreVert"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.KeyboardArrowRight"))
    }
}
