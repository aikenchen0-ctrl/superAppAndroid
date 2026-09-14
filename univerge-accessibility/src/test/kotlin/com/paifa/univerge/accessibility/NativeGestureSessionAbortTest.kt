package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGestureSessionAbortTest {
    @Test
    fun abortCancelsCoreBackPreviewBeforeClearingTheSession() {
        val calls = mutableListOf<String>()
        val abort = NativeGestureSessionAbort(
            cancelCore = { calls += "core" },
            cancelBackProgress = { calls += "back" },
            endPreview = { calls += "preview" },
            clearFields = { calls += "clear" }
        )

        assertTrue(abort.abort(coreActive = true, backProgressActive = true, previewActive = true))

        assertEquals(listOf("core", "back", "preview", "clear"), calls)
    }

    @Test
    fun abortIsIdempotentWhenTheTransactionAlreadyEnded() {
        var clearCount = 0
        val abort = NativeGestureSessionAbort(
            cancelCore = { error("core must not be cancelled") },
            cancelBackProgress = { error("back must not be cancelled") },
            endPreview = { error("preview must not end") },
            clearFields = { clearCount++ }
        )

        assertFalse(abort.abort(coreActive = false, backProgressActive = false, previewActive = false))
        assertEquals(1, clearCount)
    }
}
