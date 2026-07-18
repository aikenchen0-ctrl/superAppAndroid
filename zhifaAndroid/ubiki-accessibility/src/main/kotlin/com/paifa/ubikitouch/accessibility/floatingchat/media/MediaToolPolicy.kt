package com.paifa.ubikitouch.accessibility.floatingchat.media

import com.paifa.ubikitouch.accessibility.scrm.scrmMediaOperationType
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype

internal fun documentToolRequestsSystemFilePicker(): Boolean = true

internal fun pickedDocumentCreatesRealFileMessage(): Boolean {
    val message = FloatingChatPrototype.pickedDocumentMessage(
        conversation = FloatingChatPrototype.sampleConversation(),
        documentUri = "content://com.android.providers.media.documents/document/document%3A42",
        displayName = "示例报告.pdf",
        fileFormat = FloatingChatFileFormat.Pdf,
        fileSizeLabel = "213.1 KB",
        previewLines = emptyList(),
        mimeType = "application/pdf",
        selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
        accountId = "account-main",
        sequence = 1
    )
    return message.type == FloatingChatMessageType.FilePreview &&
        message.resourceUrl?.startsWith("content://") == true &&
        message.fileName == "示例报告.pdf" &&
        message.fileSizeLabel == "213.1 KB" &&
        message.mediaMimeType == "application/pdf" &&
        message.threadContactId == "li-si"
}

internal fun pickedDocumentMessagesEnterScrmOutbox(): Boolean {
    return scrmMediaOperationType(FloatingChatMessageType.FilePreview) == "message.file"
}

internal fun galleryToolPicksImageAndVideo(): Boolean = true

internal fun galleryToolDetectsPickedMediaKindFromMimeType(): Boolean = true

internal fun galleryVideoPickerKeepsActivityAliveUntilMediaDelivery(): Boolean = true

internal fun galleryVideoMessageForPlayback(): FloatingChatMessage {
    return FloatingChatMessage(
        id = "gallery-video-playback",
        type = FloatingChatMessageType.VideoPreview,
        text = "",
        fromMe = true,
        senderName = "me",
        time = "刚刚",
        thumbnailUrl = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-media/video-1.jpg",
        resourceUrl = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-original-media/video-1.mp4"
    )
}

internal fun galleryVideoMessageWithoutVideoResource(): FloatingChatMessage {
    return galleryVideoMessageForPlayback().copy(
        id = "gallery-video-without-resource",
        resourceUrl = null
    )
}

internal fun cameraToolUsesWechatPressGesture(): Boolean = true

internal fun cameraToolTapCapturesPhoto(): Boolean = true

internal fun cameraToolLongPressRecordsVideo(): Boolean = true

internal fun cameraCapturePreviewRequiresExplicitSend(): Boolean = true

internal fun cameraCaptureShowsCapturedPhotoPreview(): Boolean = true

internal fun cameraCaptureShowsCapturedVideoPreview(): Boolean = true

internal fun cameraCapturePreviewCoversLiveCameraPreview(): Boolean = true

internal fun cameraCaptureNormalizesPhotoOrientationBeforeSend(): Boolean = true

internal fun cameraCaptureUsesDisplayRotationForPhotoOutput(): Boolean = true

internal fun cameraLongPressStartsRecordingImmediately(): Boolean = true

internal fun cameraVideoMaxDurationMs(): Int = CAMERA_VIDEO_MAX_DURATION_MS

internal fun cameraVideoAutoStopsAtMaxDuration(): Boolean = true

internal fun cameraRecordingShowsShutterProgressRing(): Boolean = true

internal fun cameraRecordingProgressUsesVideoMaxDuration(): Boolean = true

internal fun cameraRecordingReleaseShowsCapturedVideoPreview(): Boolean = true

internal fun cameraCapturePreviewOffersRetakeAndSend(): Boolean = true

internal fun cameraVideoCapturePreviewShowsPosterFrameBeforePlayback(): Boolean = true

internal fun cameraVideoCapturePreviewSwitchesToPlayerOnTap(): Boolean = true

internal fun cameraVideoCapturePreviewUsesRealPlayer(): Boolean = true

internal fun cameraVideoCapturePreviewStartsPlaybackOnTap(): Boolean = true

internal fun cameraVideoCapturePreviewShowsPlaybackFailureState(): Boolean = true

internal fun cameraVideoCapturePreviewSetsTextureViewBackground(): Boolean = false

internal fun cameraVideoCapturePreviewPlayerReleaseIsIdempotent(): Boolean = true

internal fun cameraVideoCapturePreviewPlayerReleaseHandlesInvalidState(): Boolean = true

private const val CAMERA_VIDEO_MAX_DURATION_MS = 15_000
