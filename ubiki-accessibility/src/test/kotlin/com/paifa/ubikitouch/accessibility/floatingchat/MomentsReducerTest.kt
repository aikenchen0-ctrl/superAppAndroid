package com.paifa.ubikitouch.accessibility.floatingchat

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentPostUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.reduceMoments
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsReducerTest {
    @Test
    fun likeUpdatesOnlyTargetPost() {
        val state = MomentsUiState(posts = listOf(
            MomentPostUiState("a", "A", "first"),
            MomentPostUiState("b", "B", "second")
        ))
        val next = reduceMoments(state, MomentsUiEvent.LikeRequested("b"))
        assertFalse(next.posts.first { it.id == "a" }.liked)
        assertTrue(next.posts.first { it.id == "b" }.liked)
    }
}
