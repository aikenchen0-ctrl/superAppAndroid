package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.MomentMediaKind

internal data class ScrmUploadedMomentMedia(
    val url: String,
    val attachmentType: String,
    val attachmentTypeCode: Int
)

internal fun uploadScrmMomentMedia(
    context: Context,
    api: ScrmMessageApi,
    media: AppMomentMedia?
): ScrmUploadedMomentMedia? {
    media ?: return null
    val mediaUrl = media.uri?.takeIf { it.isNotBlank() }
        ?: media.previewUri?.takeIf { it.isNotBlank() }
        ?: return null
    val attachmentType = when (media.kind) {
        MomentMediaKind.Image -> "image" to ScrmMomentAttachmentType.Image
        MomentMediaKind.Video -> "video" to ScrmMomentAttachmentType.Video
        MomentMediaKind.Link -> return null
    }
    if (mediaUrl.isScrmRemoteUrl()) {
        return ScrmUploadedMomentMedia(mediaUrl, attachmentType.first, attachmentType.second)
    }
    val resolved = AndroidScrmMediaContentResolver(context).resolve(
        ScrmQueuedMediaPayload(
            mediaUrl = mediaUrl,
            mimeType = media.scrmMomentMimeType(),
            fileName = media.label?.takeIf { it.contains('.') }
        )
    )
    val uploaded = api.uploadMedia(ScrmMediaUploadRequest(resolved.fileName, resolved.contentType, resolved.bytes))
    if (!uploaded.success || uploaded.fileUrl.isNullOrBlank()) {
        throw ScrmInvalidResponseException(uploaded.message ?: "SCRM media upload did not return fileUrl")
    }
    return ScrmUploadedMomentMedia(uploaded.fileUrl, attachmentType.first, attachmentType.second)
}

private fun String.isScrmRemoteUrl(): Boolean = startsWith("http://", true) || startsWith("https://", true)

private fun AppMomentMedia.scrmMomentMimeType(): String = when (kind) {
    MomentMediaKind.Image -> "image/jpeg"
    MomentMediaKind.Video -> "video/mp4"
    MomentMediaKind.Link -> "application/octet-stream"
}
