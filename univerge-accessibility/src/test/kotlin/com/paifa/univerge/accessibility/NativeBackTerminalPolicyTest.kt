package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBackTerminalPolicyTest {
    @Test
    fun committedBackEndNeverCancelsThePreview() {
        assertFalse(
            shouldCancelBackProgressAtVisualTerminal(
                hasBackEnd = true,
                cancellationPending = false,
                backProgressActive = true
            )
        )
    }

    @Test
    fun cancelledBackProgressIsCancelledWhenThereIsNoCommit() {
        assertTrue(
            shouldCancelBackProgressAtVisualTerminal(
                hasBackEnd = false,
                cancellationPending = true,
                backProgressActive = true
            )
        )
    }

    @Test
    fun inactiveBackProgressDoesNotCreateASpuriousCancel() {
        assertFalse(
            shouldCancelBackProgressAtVisualTerminal(
                hasBackEnd = false,
                cancellationPending = false,
                backProgressActive = false
            )
        )
    }

    @Test
    fun nativeMoveLatchesBackCancellationUntilTheVisualFrameConsumesIt() {
        val source = sourceFile().readText()
        val moveCancellationBranch = source
            .substringAfter("else if (latestBackProgress != null && !sentBackCancel)")
            .substringBefore("postCoalescedVisualProgress(")
        val visualRenderer = source
            .substringAfter("private fun renderVisualProgress(")
            .substringBefore("private fun postVisualTerminal(")

        assertTrue(
            "leaving back preview must survive a fast ACTION_UP",
            moveCancellationBranch.contains("visualBackCancelPending = true")
        )
        assertTrue(
            "consuming the cancellation frame must clear the latch",
            visualRenderer.contains("visualBackCancelPending = false")
        )
    }

    private fun sourceFile(): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/univerge/accessibility/NativeEdgeGestureController.kt"
        )
        if (moduleRelative.isFile) return moduleRelative
        return File(
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/NativeEdgeGestureController.kt"
        )
    }
}
