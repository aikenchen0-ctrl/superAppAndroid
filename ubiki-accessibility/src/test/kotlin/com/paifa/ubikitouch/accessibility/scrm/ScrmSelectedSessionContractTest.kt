package com.paifa.ubikitouch.accessibility.scrm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmSelectedSessionContractTest {
    @Test
    fun selectedSessionExposesTheSharedClientAsMessageOperationApi() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/scrm/ScrmSettingsManager.kt"
        ).readText()

        assertTrue(source.contains("val messageOperationApi: ScrmMessageOperationApi"))
        assertTrue(source.contains("messageOperationApi = client"))
    }
}
