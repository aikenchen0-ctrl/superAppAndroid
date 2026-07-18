package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.content.Context
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPreviewBridge
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatMediaOverlayState
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal class FavoriteCollectionActions(
    private val context: Context,
    private val favoriteItems: () -> List<FavoriteCollectionItem>,
    private val favoriteMessageIds: MutableMap<String, Boolean>,
    private val favoriteMediaIds: MutableMap<String, Boolean>,
    private val storedFavoriteItems: MutableList<FavoriteCollectionItem>,
    private val selectedFavoriteItemIds: MutableMap<String, Boolean>,
    private val runtimeState: FloatingChatOverlayRuntimeState,
    private val mediaOverlayState: FloatingChatMediaOverlayState,
    private val onPreviewItem: (FavoriteCollectionItem?) -> Unit,
    private val onForwardMessage: (FloatingChatMessage) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit
) {
    fun removeFavoriteItem(item: FavoriteCollectionItem) {
        favoriteMessageIds.remove(item.messageId)
        favoriteMediaIds.remove(item.messageId)
        val nextItems = favoriteCollectionItemsAfterRemove(favoriteItems(), item)
        storedFavoriteItems.clear()
        storedFavoriteItems.addAll(nextItems)
        selectedFavoriteItemIds.remove(item.messageId)
        saveFavoriteCollectionItems(context, nextItems)
    }

    fun previewFavoriteItem(item: FavoriteCollectionItem) {
        val message = favoriteCollectionPreviewMessage(item)
        if (favoriteCollectionItemUsesMediaPreview(item)) {
            FloatingChatMediaPreviewBridge.open(
                mediaMessages = listOf(message),
                initialIndex = 0,
                runtimeState = runtimeState
            )
            mediaOverlayState.clearStatus()
        } else {
            onPreviewItem(item)
        }
    }

    fun forwardFavoriteItem(item: FavoriteCollectionItem) {
        onForwardMessage(favoriteCollectionPreviewMessage(item))
    }

    fun deleteSelectedFavoriteItems() {
        selectedFavoriteCollectionItems(favoriteItems(), selectedFavoriteItemIds).forEach { item ->
            removeFavoriteItem(item)
        }
        selectedFavoriteItemIds.clear()
        onMultiSelectModeChanged(false)
    }

    fun forwardSelectedFavoriteItems() {
        selectedFavoriteCollectionItems(favoriteItems(), selectedFavoriteItemIds)
            .firstOrNull()
            ?.let(::forwardFavoriteItem)
        selectedFavoriteItemIds.clear()
        onMultiSelectModeChanged(false)
    }
}
