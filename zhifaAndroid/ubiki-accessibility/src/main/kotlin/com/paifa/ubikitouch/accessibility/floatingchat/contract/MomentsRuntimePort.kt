package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class MomentDraft(
    val text: String,
    val mediaUri: String? = null
)

interface MomentsRuntimePort {
    suspend fun sync(): List<MomentPostUiState>
    suspend fun publish(draft: MomentDraft): MomentPostUiState
    suspend fun like(postId: String): MomentPostUiState
    suspend fun comment(postId: String, text: String): MomentPostUiState
}
