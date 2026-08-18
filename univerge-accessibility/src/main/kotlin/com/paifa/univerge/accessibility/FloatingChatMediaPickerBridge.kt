package com.paifa.univerge.accessibility

import android.net.Uri
import com.paifa.univerge.core.model.FloatingChatFileFormat
import com.paifa.univerge.core.model.FloatingChatThumbnailOrientation
import com.paifa.univerge.core.model.FloatingChatPrototype

object FloatingChatMediaPickerBridge {
    const val EXTRA_MEDIA_KIND = "com.paifa.univerge.extra.MEDIA_KIND"
    const val EXTRA_MEDIA_TARGET = "com.paifa.univerge.extra.MEDIA_TARGET"
    const val EXTRA_SCAN_MODE = "com.paifa.univerge.extra.SCAN_MODE"

    fun requestPick(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        UbikiAccessibilityService.instance?.requestFloatingChatMediaPick(mediaKind, target)
    }

    fun requestCapture() {
        UbikiAccessibilityService.instance?.requestFloatingChatMediaCapture()
    }

    fun requestScan() {
        UbikiAccessibilityService.instance?.requestFloatingChatScan()
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
