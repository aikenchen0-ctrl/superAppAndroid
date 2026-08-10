package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun FloatingChatPreviewChromeEffects(
    runtimeState: FloatingChatOverlayRuntimeState,
    mediaOverlayState: FloatingChatMediaOverlayState,
    onPreviewChromeChanged: (Boolean) -> Unit
) {
    LaunchedEffect(mediaOverlayState.actionMessage) {
        runtimeState.previewVisible = false
        runtimeState.mediaActionSheetVisible = mediaOverlayState.actionMessage != null
        onPreviewChromeChanged(false)
    }

    LaunchedEffect(runtimeState.previewSession, runtimeState.documentPreviewMessage) {
        val previewVisible = runtimeState.previewSession != null || runtimeState.documentPreviewMessage != null
        runtimeState.previewVisible = previewVisible
        if (previewVisible) {
            runtimeState.mediaActionSheetVisible = false
            mediaOverlayState.closeActions()
        }
        onPreviewChromeChanged(previewVisible)
    }

    LaunchedEffect(runtimeState.dismissSignal) {
        if (runtimeState.dismissSignal <= 0L) return@LaunchedEffect
        if (runtimeState.previewSession != null) {
            runtimeState.closeMediaPreview()
        } else if (runtimeState.documentPreviewMessage != null) {
            runtimeState.closeDocumentPreview()
        } else if (mediaOverlayState.actionMessage != null) {
            mediaOverlayState.closeActions()
        }
    }
}
