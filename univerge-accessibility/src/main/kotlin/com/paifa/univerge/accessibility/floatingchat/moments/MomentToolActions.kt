package com.paifa.univerge.accessibility.floatingchat.moments

import com.paifa.univerge.accessibility.AppMomentMedia
import com.paifa.univerge.accessibility.AppMomentPost
import com.paifa.univerge.accessibility.FloatingChatMediaPickerBridge
import com.paifa.univerge.accessibility.FloatingChatMediaPreviewBridge
import com.paifa.univerge.accessibility.FloatingChatMediaTarget
import com.paifa.univerge.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.univerge.core.model.FloatingChatPrototype

internal class MomentToolActions(
    private val accountId: () -> String,
    private val momentPosts: MutableList<AppMomentPost>,
    private val runtimeState: FloatingChatOverlayRuntimeState,
    private val onPersistMomentPost: (AppMomentPost) -> Unit,
    private val onPendingMomentMediaChanged: (AppMomentMedia?) -> Unit
) {
    fun upsertMomentPost(post: AppMomentPost) {
        val scopedPost = post.copy(accountId = accountId())
        val existingIndex = momentPosts.indexOfFirst { existing ->
            existing.accountId == scopedPost.accountId && existing.id == scopedPost.id
        }
        if (existingIndex >= 0) {
            momentPosts[existingIndex] = scopedPost
        } else {
            momentPosts.add(0, scopedPost)
        }
        onPersistMomentPost(scopedPost)
    }

    fun pickMomentMedia() {
        FloatingChatMediaPickerBridge.requestPick(
            mediaKind = FloatingChatPrototype.PickedMediaKind.Any,
            target = FloatingChatMediaTarget.Moment
        )
    }

    fun clearMomentMedia() {
        onPendingMomentMediaChanged(null)
    }

    fun previewMomentMedia(post: AppMomentPost) {
        val mediaMessage = post.toFloatingChatMediaMessage() ?: return
        FloatingChatMediaPreviewBridge.open(
            mediaMessages = listOf(mediaMessage),
            initialIndex = 0,
            runtimeState = runtimeState,
            accountId = accountId()
        )
    }
}
