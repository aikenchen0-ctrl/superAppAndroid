package com.paifa.ubikitouch.accessibility.floatingchat.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFormatIconTest {
    @Test
    fun commonExtensionsUseDistinctFileIconCategories() {
        assertEquals(FilePreviewIconKind.Android, filePreviewIconKindFor("client.apk", null))
        assertEquals(FilePreviewIconKind.Android, filePreviewIconKindFor("client.apk1", null))
        assertEquals(FilePreviewIconKind.Android, filePreviewIconKindFor("library.aar", null))
        assertEquals(FilePreviewIconKind.Spreadsheet, filePreviewIconKindFor("report.xlsx", null))
        assertEquals(FilePreviewIconKind.Spreadsheet, filePreviewIconKindFor("report.xls", null))
        assertEquals(FilePreviewIconKind.Document, filePreviewIconKindFor("notes.docs", null))
        assertEquals(FilePreviewIconKind.Archive, filePreviewIconKindFor("bundle.zip", null))
        assertEquals(FilePreviewIconKind.Archive, filePreviewIconKindFor("bundle.7z", null))
        assertEquals(FilePreviewIconKind.Archive, filePreviewIconKindFor("bundle.arr", null))
        assertEquals(FilePreviewIconKind.Audio, filePreviewIconKindFor("voice.mp3", null))
        assertEquals(FilePreviewIconKind.Video, filePreviewIconKindFor("clip.mp4", null))
        assertEquals(FilePreviewIconKind.Image, filePreviewIconKindFor("photo.jpeg", null))
        assertEquals(FilePreviewIconKind.Presentation, filePreviewIconKindFor("slides.ppt", null))
    }

    @Test
    fun imageFileCardCanUseAThumbnailInsteadOfExtensionText() {
        val source = sourceFile("floatingchat/media/DocumentPreview.kt").readText()
        assertTrue(source.contains("rememberAsyncImageThumbnailBitmap("))
        assertTrue(source.contains("FilePreviewIconKind.Image"))
        assertTrue(source.contains("Image("))
    }

    private fun sourceFile(relativePath: String): java.io.File {
        val moduleRelative = java.io.File("src/main/kotlin/com/paifa/ubikitouch/accessibility", relativePath)
        if (moduleRelative.exists()) return moduleRelative
        return java.io.File("ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility", relativePath)
    }
}
