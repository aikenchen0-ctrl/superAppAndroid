package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeVisualDispatchTest {
    @Test
    fun coalescedProgressKeepsOnlyTheLatestFrame() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" },
            onTerminal = { rendered += "terminal" }
        )

        dispatch.postCoalescedVisualProgress("first")
        dispatch.postCoalescedVisualProgress("latest")

        assertEquals("only one main-thread frame should be queued", 1, queued.size)
        queued.removeAt(0).invoke()

        assertEquals(listOf("frame:latest"), rendered)
    }

    @Test
    fun terminalInvalidatesAnUnconsumedProgressFrame() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" },
            onTerminal = { rendered += "terminal" }
        )

        dispatch.postCoalescedVisualProgress("stale")
        dispatch.postVisualTerminal()
        queued.toList().forEach { it.invoke() }

        assertEquals(listOf("terminal"), rendered)
    }

    @Test
    fun progressAfterTerminalRunsAfterTheOldFrameIsInvalidated() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" },
            onTerminal = { rendered += "terminal" }
        )

        dispatch.postCoalescedVisualProgress("old")
        dispatch.postVisualTerminal()
        dispatch.postCoalescedVisualProgress("new")
        queued.toList().forEach { it.invoke() }

        assertEquals(listOf("terminal", "frame:new"), rendered)
    }

    @Test
    fun progressAfterTerminalDoesNotDropCustomTerminalCallback() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" },
            onTerminal = { rendered += "default-terminal" }
        )

        dispatch.postCoalescedVisualProgress("old")
        dispatch.postVisualTerminal { rendered += "custom-terminal" }
        dispatch.postCoalescedVisualProgress("new")
        queued.toList().forEach { it.invoke() }

        assertEquals(listOf("custom-terminal", "frame:new"), rendered)
    }

    @Test
    fun duplicateTerminalsBeforeMainDispatchAreCoalesced() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" }
        )

        dispatch.postVisualTerminal { rendered += "first-terminal" }
        dispatch.postVisualTerminal { rendered += "second-terminal" }
        queued.toList().forEach { it.invoke() }

        assertEquals(listOf("first-terminal"), rendered)
    }

    @Test
    fun terminalsFromDifferentGesturesAreBothDelivered() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" }
        )

        dispatch.postVisualTerminal(gestureKey = 101L) { rendered += "first-terminal" }
        dispatch.postVisualTerminal(gestureKey = 202L) { rendered += "second-terminal" }
        queued.toList().forEach { it.invoke() }

        assertEquals(listOf("first-terminal", "second-terminal"), rendered)
    }

    @Test
    fun clearingPendingVisualsInvalidatesQueuedFramesAndTerminals() {
        val queued = mutableListOf<() -> Unit>()
        val rendered = mutableListOf<String>()
        val dispatch = NativeVisualDispatch<String>(
            post = { runnable -> queued += runnable },
            onProgress = { frame -> rendered += "frame:$frame" }
        )

        dispatch.postCoalescedVisualProgress("stale")
        dispatch.postVisualTerminal(gestureKey = 303L) { rendered += "terminal" }
        dispatch.clearPendingVisuals()
        queued.toList().forEach { it.invoke() }

        assertEquals(emptyList<String>(), rendered)
    }
}
