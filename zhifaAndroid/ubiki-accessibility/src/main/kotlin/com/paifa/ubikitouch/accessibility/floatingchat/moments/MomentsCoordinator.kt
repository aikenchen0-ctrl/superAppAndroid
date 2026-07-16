package com.paifa.ubikitouch.accessibility.floatingchat.moments

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentDraft
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsRuntimePort
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsUiState

internal class MomentsCoordinator(private val runtime: MomentsRuntimePort) {
    suspend fun refresh(): MomentsUiState = runCatching {
        MomentsUiState(posts = runtime.sync())
    }.getOrElse { MomentsUiState(error = it.message ?: "朋友圈同步失败") }

    suspend fun publish(state: MomentsUiState, draft: MomentDraft): MomentsUiState = runCatching {
        state.copy(posts = listOf(runtime.publish(draft)) + state.posts)
    }.getOrElse { state.copy(error = it.message ?: "朋友圈发布失败") }

    suspend fun dispatch(state: MomentsUiState, event: MomentsUiEvent): MomentsUiState = when (event) {
        is MomentsUiEvent.LikeRequested -> runCatching {
            val updated = runtime.like(event.postId)
            state.copy(posts = state.posts.map { if (it.id == updated.id) updated else it })
        }.getOrElse { state.copy(error = it.message ?: "点赞失败") }
        is MomentsUiEvent.CommentRequested -> state
        else -> state
    }
}
