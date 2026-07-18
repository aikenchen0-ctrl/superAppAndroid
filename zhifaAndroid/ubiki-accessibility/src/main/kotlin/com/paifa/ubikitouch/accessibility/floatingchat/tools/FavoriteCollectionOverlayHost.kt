package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageLongPressAction

@Composable
internal fun FavoriteCollectionOverlayHost(
    previewItem: FavoriteCollectionItem?,
    longPressItem: FavoriteCollectionItem?,
    longPressAnchorBounds: Rect?,
    favoriteActions: FavoriteCollectionActions,
    selectedFavoriteItemIds: MutableMap<String, Boolean>,
    onPreviewItemChanged: (FavoriteCollectionItem?) -> Unit,
    onLongPressItemChanged: (FavoriteCollectionItem?, Rect?) -> Unit,
    onMultiSelectModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    previewItem?.let { item ->
        FavoriteCollectionPreviewOverlay(
            item = item,
            onDismiss = { onPreviewItemChanged(null) },
            onForward = {
                favoriteActions.forwardFavoriteItem(item)
                onPreviewItemChanged(null)
            },
            onDelete = {
                favoriteActions.removeFavoriteItem(item)
                onPreviewItemChanged(null)
            },
            modifier = modifier.fillMaxSize()
        )
    }
    longPressItem?.let { item ->
        FavoriteCollectionLongPressMenuOverlay(
            item = item,
            itemBounds = longPressAnchorBounds,
            onDismiss = { onLongPressItemChanged(null, null) },
            onAction = { action ->
                when (action) {
                    MessageLongPressAction.Forward -> favoriteActions.forwardFavoriteItem(item)
                    MessageLongPressAction.Delete -> favoriteActions.removeFavoriteItem(item)
                    MessageLongPressAction.MultiSelect -> {
                        onMultiSelectModeChanged(true)
                        selectedFavoriteItemIds[item.messageId] = true
                    }
                    else -> Unit
                }
                onLongPressItemChanged(null, null)
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
