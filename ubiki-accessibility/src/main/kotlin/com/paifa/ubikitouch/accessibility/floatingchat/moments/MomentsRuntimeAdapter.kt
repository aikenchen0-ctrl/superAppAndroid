package com.paifa.ubikitouch.accessibility.floatingchat.moments

import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentDraft
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentPostUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.MomentsRuntimePort

/** Runtime boundary for SCRM, media upload, and task polling implementations. */
internal class MomentsRuntimeAdapter(
    private val syncOperation: suspend () -> List<MomentPostUiState>,
    private val publishOperation: suspend (MomentDraft) -> MomentPostUiState,
    private val likeOperation: suspend (String) -> MomentPostUiState,
    private val commentOperation: suspend (String, String) -> MomentPostUiState
) : MomentsRuntimePort {
    override suspend fun sync(): List<MomentPostUiState> = syncOperation()

    override suspend fun publish(draft: MomentDraft): MomentPostUiState = publishOperation(draft)

    override suspend fun like(postId: String): MomentPostUiState = likeOperation(postId)

    override suspend fun comment(postId: String, text: String): MomentPostUiState = commentOperation(postId, text)
}
