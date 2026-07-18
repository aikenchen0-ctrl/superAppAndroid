package com.paifa.ubikitouch.accessibility.floatingchat.moments

import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.AppMomentPost
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPreviewBridge
import com.paifa.ubikitouch.accessibility.FloatingChatMediaTarget
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.core.model.FloatingChatPrototype

internal class MomentToolActions(
    private val momentPosts: MutableList<AppMomentPost>,
    private val runtimeState: FloatingChatOverlayRuntimeState,
    private val onPersistMomentPost: (AppMomentPost) -> Unit,
    private val onPendingMomentMediaChanged: (AppMomentMedia?) -> Unit
) {
    fun upsertMomentPost(post: AppMomentPost) {
        val existingIndex = momentPosts.indexOfFirst { it.id == post.id }
        if (existingIndex >= 0) {
            momentPosts[existingIndex] = post
        } else {
            momentPosts.add(0, post)
        }
        onPersistMomentPost(post)
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
            runtimeState = runtimeState
        )
    }
}
