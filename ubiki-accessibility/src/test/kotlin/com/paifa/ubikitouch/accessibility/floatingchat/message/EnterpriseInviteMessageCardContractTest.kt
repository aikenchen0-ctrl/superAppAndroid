package com.paifa.ubikitouch.accessibility.floatingchat.message

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterpriseInviteMessageCardContractTest {
    @Test
    fun enterpriseInviteUsesMaterialCardWithIconTitleAndDescription() {
        val source = sourceFile("floatingchat/message/EnterpriseInviteMessageCard.kt").readText()
        assertTrue(source.contains("Card("))
        assertTrue(source.contains("CardDefaults.cardColors("))
        assertTrue(source.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(source.contains("Icons.Filled.Business"))
        assertTrue(source.contains("message.text"))
        assertTrue(source.contains("message.detail"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File("src/main/kotlin/com/paifa/ubikitouch/accessibility", relativePath)
        if (moduleRelative.exists()) return moduleRelative
        return File("ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility", relativePath)
    }
}
