package com.paifa.ubikitouch.accessibility.floatingchat.media

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerImageContentContractTest {
    @Test
    fun stickerUsesDedicatedTransparentAspectRatioContent() {
        val source = sourceFile("floatingchat/media/StickerImageContent.kt")
        assertTrue("Missing dedicated sticker image content", source.isFile)

        val text = source.readText()
        assertTrue(text.contains("internal fun StickerImageContent("))
        assertTrue(text.contains(".width(100.dp)"))
        assertTrue(text.contains(".aspectRatio(stickerAspectRatio)"))
        assertTrue(text.contains("mediaBitmap.width"))
        assertTrue(text.contains("mediaBitmap.height"))
        assertTrue(text.contains("ContentScale.Fit"))
        assertFalse(text.contains(".background("))
        assertFalse(text.contains(".border("))
        assertFalse(text.contains("TextLabel("))
        assertFalse(text.contains("FloatingChatImageActionPill("))
        assertFalse(text.contains("MediaThumbnailSurface("))
        assertFalse(text.contains("mediaWatermarkText("))
    }

    @Test
    fun stickerRenderersDoNotReuseOrdinaryImageChrome() {
        val mediaRenderer = sourceFile("floatingchat/message/renderers/MediaMessageRenderer.kt").readText()
        val legacyRenderer = sourceFile("floatingchat/message/renderers/LegacyMessageRenderer.kt").readText()

        assertStickerBranchUsesDedicatedContent(
            renderer = mediaRenderer,
            branchEnd = "FloatingChatMessageType.ImageThumbnail,"
        )
        assertStickerBranchUsesDedicatedContent(
            renderer = legacyRenderer,
            branchEnd = "else -> ImageThumbnailContent("
        )
    }

    @Test
    fun stickerLoaderUsesResourceFallbackAndPreservesAlpha() {
        val loader = sourceFile("floatingchat/media/MediaThumbnailBitmapLoader.kt").readText()

        assertTrue(loader.contains("internal fun rememberAsyncStickerThumbnailBitmap("))
        assertTrue(loader.contains("message.thumbnailUrl ?: message.resourceUrl"))
        assertTrue(loader.contains("Bitmap.Config.ARGB_8888"))
        assertTrue(loader.contains("STICKER_THUMBNAIL_CACHE_NAMESPACE"))
    }

    private fun assertStickerBranchUsesDedicatedContent(renderer: String, branchEnd: String) {
        val stickerBranch = renderer
            .substringAfter("FloatingChatMessageType.StickerGif ->")
            .substringBefore(branchEnd)

        assertTrue(stickerBranch.contains("StickerImageContent("))
        assertFalse(stickerBranch.contains("ImageThumbnailContent("))
        assertFalse(stickerBranch.contains("copy(type = FloatingChatMessageType.ImageThumbnail)"))
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
