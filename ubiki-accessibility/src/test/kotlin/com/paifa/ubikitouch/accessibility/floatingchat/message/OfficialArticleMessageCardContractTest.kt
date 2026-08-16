package com.paifa.ubikitouch.accessibility.floatingchat.message

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialArticleMessageCardContractTest {
    @Test
    fun officialArticleUsesStructuredMaterial3CardContent() {
        val source = sourceFile("floatingchat/message/OfficialArticleMessageCard.kt")
        assertTrue("Missing official-account article card", source.isFile)

        val text = source.readText()
        assertTrue(text.contains("internal fun OfficialArticleMessageCard("))
        assertTrue(text.contains("Card("))
        assertTrue(text.contains("CardDefaults.cardColors("))
        assertTrue(text.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
        assertTrue(text.contains("RoundedCornerShape(8.dp)"))
        assertTrue(text.contains("rememberAsyncImageThumbnailBitmap("))
        assertTrue(text.contains("bannerImageUrl"))
        assertTrue(text.contains("imageUrl"))
        assertTrue(text.contains("item.title"))
        assertTrue(text.contains("Icons.Filled.Article"))
        assertTrue(text.contains("公众号"))
        assertTrue(text.contains("item.description.ifBlank { item.title }"))
        assertTrue(text.contains("it.width.toFloat() / it.height.toFloat()"))
        assertTrue(text.contains("aspectRatio("))
        assertTrue(text.contains("formatOfficialArticleTime("))
        assertFalse(text.contains("ResourceUrlLine("))
    }

    @Test
    fun articleRendererDoesNotReuseGenericLinkCard() {
        val renderer = sourceFile("floatingchat/message/renderers/CardMessageRenderer.kt").readText()
        val articleBranch = renderer
            .substringAfter("FloatingChatMessageType.Article ->")
            .substringBefore("FloatingChatMessageType.ChannelsLive")

        assertTrue(articleBranch.contains("OfficialArticleMessageCard(message)"))
        assertFalse(articleBranch.contains("LinkMessageCard(message)"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
    }
}
