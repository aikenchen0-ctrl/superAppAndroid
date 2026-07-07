package com.paifa.ubikitouch.accessibility

import android.net.Uri
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatPrototype

object FloatingChatMediaPickerBridge {
    const val EXTRA_MEDIA_KIND = "com.paifa.ubikitouch.extra.MEDIA_KIND"
    const val EXTRA_MEDIA_TARGET = "com.paifa.ubikitouch.extra.MEDIA_TARGET"

    fun requestPick(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        UbikiAccessibilityService.instance?.requestFloatingChatMediaPick(mediaKind, target)
    }

    fun requestCapture() {
        UbikiAccessibilityService.instance?.requestFloatingChatMediaCapture()
    }

    fun requestDocumentPick() {
        UbikiAccessibilityService.instance?.requestFloatingChatDocumentPick()
    }

    fun deliverPickedMedia(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: Uri,
        previewUri: Uri,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        UbikiAccessibilityService.instance?.onFloatingChatMediaPicked(
            mediaKind = mediaKind,
            mediaUri = mediaUri.toString(),
            previewUri = previewUri.toString(),
            orientation = orientation,
            aspectRatio = aspectRatio,
            target = target
        )
    }

    fun deliverPickedDocument(document: FloatingChatPickedDocument) {
        UbikiAccessibilityService.instance?.onFloatingChatDocumentPicked(document)
    }

    fun notifyPickerClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatMediaPickerClosed()
    }
}

enum class FloatingChatMediaTarget {
    Chat,
    Moment,
    AccountAvatar
}

data class FloatingChatPickedDocument(
    val uri: String,
    val displayName: String,
    val fileFormat: FloatingChatFileFormat?,
    val fileSizeLabel: String?,
    val previewLines: List<String>,
    val mimeType: String?
)
