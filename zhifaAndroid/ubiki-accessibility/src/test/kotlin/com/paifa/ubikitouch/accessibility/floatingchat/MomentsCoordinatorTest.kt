package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentDraft
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentPostUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsRuntimePort
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiState
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentsCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentsCoordinatorTest {
    @Test
    fun likeUpdatesOnlyTargetPost() = runBlocking {
        val port = object : MomentsRuntimePort {
            override suspend fun sync() = emptyList<MomentPostUiState>()
            override suspend fun publish(draft: MomentDraft) = MomentPostUiState("new", "A", draft.text)
            override suspend fun like(postId: String) = MomentPostUiState(postId, "A", "updated", liked = true)
            override suspend fun comment(postId: String, text: String) = MomentPostUiState(postId, "A", text)
        }
        val coordinator = MomentsCoordinator(port)
        val state = MomentsUiState(listOf(MomentPostUiState("a", "A", "a"), MomentPostUiState("b", "B", "b")))
        val next = coordinator.dispatch(state, MomentsUiEvent.LikeRequested("b"))
        assertEquals("a", next.posts.first { it.id == "a" }.text)
        assertEquals("updated", next.posts.first { it.id == "b" }.text)
    }

    @Test
    fun commentUpdatesOnlyTargetPost() = runBlocking {
        val port = object : MomentsRuntimePort {
            override suspend fun sync() = emptyList<MomentPostUiState>()
            override suspend fun publish(draft: MomentDraft) = MomentPostUiState("new", "A", draft.text)
            override suspend fun like(postId: String) = MomentPostUiState(postId, "A", "liked")
            override suspend fun comment(postId: String, text: String) = MomentPostUiState(postId, "A", text)
        }
        val coordinator = MomentsCoordinator(port)
        val state = MomentsUiState(listOf(MomentPostUiState("a", "A", "a"), MomentPostUiState("b", "B", "b")))
        val next = coordinator.dispatch(state, MomentsUiEvent.CommentRequested("b", "hello"))
        assertEquals("a", next.posts.first { it.id == "a" }.text)
        assertEquals("hello", next.posts.first { it.id == "b" }.text)
    }

    @Test
    fun commentFailureIsExposedWithoutChangingPosts() = runBlocking {
        val port = object : MomentsRuntimePort {
            override suspend fun sync() = emptyList<MomentPostUiState>()
            override suspend fun publish(draft: MomentDraft) = MomentPostUiState("new", "A", draft.text)
            override suspend fun like(postId: String) = MomentPostUiState(postId, "A", "liked")
            override suspend fun comment(postId: String, text: String): MomentPostUiState = error("network down")
        }
        val coordinator = MomentsCoordinator(port)
        val state = MomentsUiState(listOf(MomentPostUiState("a", "A", "original")))
        val next = coordinator.dispatch(state, MomentsUiEvent.CommentRequested("a", "hello"))
        assertEquals("original", next.posts.single().text)
        assertEquals("network down", next.error)
    }
}
