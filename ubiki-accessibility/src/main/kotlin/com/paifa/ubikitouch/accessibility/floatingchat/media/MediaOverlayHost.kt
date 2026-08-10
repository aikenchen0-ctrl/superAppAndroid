package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPreviewSession
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MediaOverlayHost(
    actionMessage: FloatingChatMessage?,
    previewSession: FloatingChatMediaPreviewSession?,
    documentPreviewMessage: FloatingChatMessage?,
    onCloseActions: () -> Unit,
    onMediaAction: (FloatingChatMessage, MediaActionContract) -> Unit,
    onCloseMediaPreview: () -> Unit,
    onCloseDocumentPreview: () -> Unit,
    onOpenExternalDocument: (FloatingChatMessage) -> Boolean,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    actionMessage?.let { message ->
        FloatingChatMediaActionSheetOverlay(
            message = message,
            onClose = onCloseActions,
            onMediaAction = { action ->
                onMediaAction(message, action)
                if (action != MediaActionContract.More) {
                    onCloseActions()
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
    previewSession?.let { session ->
        FloatingChatMediaPreviewHost(
            session = session,
            onClose = onCloseMediaPreview,
            modifier = modifier.fillMaxSize()
        )
    }
    documentPreviewMessage?.let { message ->
        DocumentPreviewOverlay(
            message = message,
            onClose = onCloseDocumentPreview,
            onOpenExternal = {
                val opened = onOpenExternalDocument(message)
                if (opened) {
                    onCloseDocumentPreview()
                }
                onShowToast(
                    if (opened) {
                        "正在打开外部应用"
                    } else {
                        "无法打开外部应用"
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
