package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentDraft
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentPostUiState
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentsRuntimeAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentsRuntimeAdapterTest {
    @Test
    fun delegatesAllRuntimeOperations() = runBlocking {
        val adapter = MomentsRuntimeAdapter(
            syncOperation = { listOf(MomentPostUiState("1", "A", "sync")) },
            publishOperation = { draft -> MomentPostUiState("2", "A", draft.text) },
            likeOperation = { id -> MomentPostUiState(id, "A", "like", liked = true) },
            commentOperation = { id, text -> MomentPostUiState(id, "A", text, commentCount = 1) }
        )
        assertEquals("sync", adapter.sync().single().text)
        assertEquals("draft", adapter.publish(MomentDraft("draft")).text)
        assertEquals(true, adapter.like("1").liked)
        assertEquals(1, adapter.comment("1", "ok").commentCount)
    }
}
