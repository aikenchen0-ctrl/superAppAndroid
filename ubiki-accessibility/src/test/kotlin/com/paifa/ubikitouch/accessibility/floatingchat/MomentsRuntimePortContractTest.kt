package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentDraft
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentPostUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsRuntimePort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentsRuntimePortContractTest {
    @Test
    fun runtimePortPreservesUiDataWithoutPlatformTypes() = runBlocking {
        val port = object : MomentsRuntimePort {
            override suspend fun sync() = listOf(MomentPostUiState("p1", "A", "hello"))
            override suspend fun publish(draft: MomentDraft) = MomentPostUiState("p2", "A", draft.text)
            override suspend fun like(postId: String) = MomentPostUiState(postId, "A", "liked", liked = true)
            override suspend fun comment(postId: String, text: String) = MomentPostUiState(postId, "A", text)
        }
        assertEquals("hello", port.publish(MomentDraft("hello")).text)
        assertEquals("p1", port.sync().single().id)
    }
}
