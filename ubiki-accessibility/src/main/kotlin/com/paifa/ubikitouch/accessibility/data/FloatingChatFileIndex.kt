package com.paifa.ubikitouch.accessibility.data

import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import java.security.MessageDigest
import java.util.Locale

internal object FloatingChatFileIndex {
    fun contentKeyForUri(
        uri: String,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        modifiedAtMillis: Long? = null
    ): String {
        val normalized = listOf(
            uri.trim(),
            mimeType.orEmpty().lowercase(Locale.US),
            sizeBytes?.toString().orEmpty(),
            modifiedAtMillis?.toString().orEmpty()
        ).joinToString("|")
        return "uri-sha256:${normalized.sha256Hex()}"
    }

    fun fileAttachmentsForMessage(
        message: FloatingChatMessage,
        createdAt: Long = System.currentTimeMillis()
    ): List<LocalChatMessageAttachment> {
        val resourceUri = message.resourceUrl?.trim().orEmpty()
        if (resourceUri.isBlank()) return emptyList()

        val role = when (message.type) {
            FloatingChatMessageType.ImageThumbnail -> "image"
            FloatingChatMessageType.VideoPreview -> "video"
            FloatingChatMessageType.FilePreview -> "file"
            FloatingChatMessageType.Voice -> "voice"
            else -> return emptyList()
        }
        val mimeType = message.mediaMimeType ?: defaultMimeTypeFor(message.type, resourceUri)
        val contentKey = contentKeyForUri(
            uri = resourceUri,
            mimeType = mimeType,
            sizeBytes = null,
            modifiedAtMillis = null
        )
        val fileId = fileIdForContentKey(contentKey)
        val file = LocalChatFile(
            fileId = fileId,
            contentKey = contentKey,
            uri = resourceUri,
            previewUri = message.thumbnailUrl?.takeIf { it.isNotBlank() && it != resourceUri },
            mimeType = mimeType,
            displayName = message.fileName ?: resourceUri.substringAfterLast('/').substringAfterLast(':'),
            durationMs = message.mediaDurationMs,
            createdAt = createdAt
        )
        val ref = LocalChatMessageFileRef(
            messageId = message.id,
            fileId = fileId,
            role = role,
            position = 0
        )
        return listOf(LocalChatMessageAttachment(file = file, ref = ref))
    }

    fun forwardedFileRefs(
        sourceRefs: List<LocalChatMessageFileRef>,
        targetMessageId: String
    ): List<LocalChatMessageFileRef> {
        return sourceRefs.map { ref ->
            ref.copy(messageId = targetMessageId)
        }
    }

    private fun fileIdForContentKey(contentKey: String): String {
        return "file-${contentKey.substringAfter(':').take(24)}"
    }

    private fun defaultMimeTypeFor(type: FloatingChatMessageType, uri: String): String? {
        return when (type) {
            FloatingChatMessageType.ImageThumbnail -> "image/jpeg"
            FloatingChatMessageType.VideoPreview -> "video/mp4"
            FloatingChatMessageType.Voice -> "audio/mp4"
            FloatingChatMessageType.FilePreview -> mimeTypeFromFileName(uri)
            else -> null
        }
    }

    private fun mimeTypeFromFileName(value: String): String? {
        return when (value.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.US)) {
            "txt" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "doc", "docx" -> "application/msword"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a", "aac" -> "audio/mp4"
            else -> null
        }
    }
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
