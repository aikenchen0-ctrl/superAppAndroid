package com.paifa.ubikitouch.accessibility.floatingchat.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalStickerPathTest {
    @Test
    fun existingAbsoluteStickerPathIsNormalizedToFileUri() {
        val stickerFile = File.createTempFile("floating-sticker", ".img")
        try {
            assertEquals(stickerFile.toURI().toString(), normalizedRemoteImageUri(stickerFile.absolutePath))
        } finally {
            stickerFile.delete()
        }
    }

    @Test
    fun missingAbsoluteStickerPathIsRejected() {
        val missingPath = File(
            System.getProperty("java.io.tmpdir"),
            "missing-floating-sticker-${System.nanoTime()}"
        ).absolutePath

        assertNull(normalizedRemoteImageUri(missingPath))
    }
}
