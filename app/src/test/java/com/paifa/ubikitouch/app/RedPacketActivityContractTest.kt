package com.paifa.ubikitouch.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RedPacketActivityContractTest {
    @Test
    fun redPacketScreenProvidesRequiredControls() {
        val source = File("src/main/java/com/paifa/ubikitouch/app/RedPacketActivity.kt").readText()

        assertTrue(source.contains("StatusBarHeight = 30.dp"))
        assertTrue(source.contains("Text(\"红包\""))
        assertTrue(source.contains("发给"))
        assertTrue(source.contains("value = \"66\""))
        assertTrue(source.contains("恭喜发财，大吉大利"))
        assertTrue(source.contains("红包个数"))
        assertTrue(source.contains("Icons.Filled.Remove"))
        assertTrue(source.contains("Icons.Filled.Add"))
        assertTrue(source.contains("塞钱进红包"))
        assertTrue(source.contains("0xFFFA5151"))
    }
}
