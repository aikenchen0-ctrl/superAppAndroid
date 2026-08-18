package com.paifa.univerge.accessibility.floatingchat.message

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FileMessageCardContractTest {
    @Test
    fun fileMessageUsesMaterialCardWithIconBeforeTitleAndDescription() {
        val source = sourceFile("floatingchat/media/DocumentPreview.kt")
        val text = source.readText()

        assertTrue(text.contains("Card("))
        assertTrue(text.contains("CardDefaults.cardColors("))
        assertTrue(text.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(text.contains("RoundedCornerShape(8.dp)"))
        assertTrue(text.contains("FileFormatIcon("))
        assertTrue(text.contains("fileDisplayName(message)"))
        assertTrue(text.contains("message.fileSizeLabel"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/univerge/accessibility",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility",
            relativePath
        )
    }
}
