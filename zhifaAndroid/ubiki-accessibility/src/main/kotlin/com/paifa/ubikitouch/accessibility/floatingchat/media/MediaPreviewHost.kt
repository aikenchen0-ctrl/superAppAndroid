package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPreviewSession
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.shell.rememberFloatingChatMediaOverlayState
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionItem
import com.paifa.ubikitouch.accessibility.floatingchat.tools.loadFavoriteCollectionItems
import com.paifa.ubikitouch.accessibility.floatingchat.tools.updateFavoriteCollectionItems
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
fun FloatingChatMediaPreviewHost(
    session: FloatingChatMediaPreviewSession,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaOverlayState = rememberFloatingChatMediaOverlayState()
    val favoriteMediaIds = mediaOverlayState.favoriteMediaIds
    val storedFavoriteItems = remember(context) {
        mutableStateListOf<FavoriteCollectionItem>().apply {
            addAll(loadFavoriteCollectionItems(context))
        }
    }
    val handleMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit = { message, action ->
        val result = performMediaAction(
            context = context,
            message = message,
            action = action,
            favoriteMediaIds = favoriteMediaIds,
            onOpenActions = { mediaOverlayState.openActions(message) },
            onFavoriteChanged = { favoriteMessage, favorite ->
                updateFavoriteCollectionItems(context, storedFavoriteItems, favoriteMessage, favorite)
            }
        )
        mediaOverlayState.applyActionResult(result)
        if (result.toast) {
            Toast.makeText(context, result.status, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.background(OverlayTokens.blankScrim)) {
        MediaPreviewOverlay(
            mediaMessages = session.mediaMessages,
            initialIndex = session.initialIndex,
            actionStatus = mediaOverlayState.actionStatus,
            favoriteMediaIds = favoriteMediaIds,
            externalDismissSignal = 0L,
            initialDismissSignal = 0L,
            onClose = onClose,
            onOpenActions = { message -> mediaOverlayState.openActions(message) },
            onMediaAction = handleMediaAction,
            modifier = Modifier.fillMaxSize()
        )
        mediaOverlayState.actionMessage?.let { message ->
            FloatingChatMediaActionSheetOverlay(
                message = message,
                onClose = { mediaOverlayState.closeActions() },
                onMediaAction = { action ->
                    handleMediaAction(message, action)
                    if (action != MediaActionContract.More) {
                        mediaOverlayState.closeActions()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
