package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal class MediaActionDispatchActions(
    private val context: Context,
    private val favoriteMediaIds: MutableMap<String, Boolean>,
    private val onOpenActions: (FloatingChatMessage) -> Unit,
    private val onFavoriteChanged: (FloatingChatMessage, Boolean) -> Unit,
    private val onActionResult: (MediaActionResult) -> Unit,
    private val onShowToast: (String) -> Unit
) {
    fun handleMediaAction(message: FloatingChatMessage, action: MediaActionContract) {
        val result = performMediaAction(
            context = context,
            message = message,
            action = action,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { onOpenActions(message) },
            onFavoriteChanged = onFavoriteChanged
        )
        onActionResult(result)
        if (result.toast) {
            onShowToast(result.status)
        }
    }
}
