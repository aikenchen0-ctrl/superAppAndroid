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
        UniVergeAccessibilityService.instance?.requestFloatingChatMediaPick(mediaKind, target)
    }

    fun requestCapture() {
        UniVergeAccessibilityService.instance?.requestFloatingChatMediaCapture()
    }

    fun requestScan() {
        UniVergeAccessibilityService.instance?.requestFloatingChatScan()
    }

    fun requestDocumentPick() {
        UniVergeAccessibilityService.instance?.requestFloatingChatDocumentPick()
    }

    fun deliverPickedMedia(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: Uri,
        previewUri: Uri,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        UniVergeAccessibilityService.instance?.onFloatingChatMediaPicked(
            mediaKind = mediaKind,
            mediaUri = mediaUri.toString(),
            previewUri = previewUri.toString(),
            orientation = orientation,
            aspectRatio = aspectRatio,
            target = target
        )
    }

    fun deliverPickedDocument(document: FloatingChatPickedDocument) {
        UniVergeAccessibilityService.instance?.onFloatingChatDocumentPicked(document)
    }

    fun notifyPickerClosed() {
        UniVergeAccessibilityService.instance?.onFloatingChatMediaPickerClosed()
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
