package com.paifa.ubikitouch.accessibility.scrm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.net.URLConnection

internal class AndroidScrmMediaContentResolver(
    context: Context
) : ScrmMediaContentResolver {
    private val appContext = context.applicationContext

    override fun resolve(payload: ScrmQueuedMediaPayload): ScrmResolvedMediaContent {
        val uri = runCatching { Uri.parse(payload.mediaUrl) }.getOrNull()
            ?: throw ScrmLocalMediaException("Invalid media uri")
        val fileName = payload.fileName
            ?: displayName(uri)
            ?: fallbackFileName(uri, payload.mimeType)
        val contentType = payload.mimeType
            ?: appContext.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(fileName)
            ?: "application/octet-stream"
        val bytes = readBytes(uri)
        return ScrmResolvedMediaContent(
            fileName = fileName,
            contentType = contentType,
            bytes = bytes
        )
    }

    private fun readBytes(uri: Uri): ByteArray {
        val bytes = when (uri.scheme?.lowercase()) {
            "content",
            "android.resource" -> appContext.contentResolver.openInputStream(uri)
                ?.use { input -> input.readBytes() }
            "file" -> uri.path?.let { path -> File(path).readBytes() }
            null,
            "" -> File(uri.toString()).takeIf { it.exists() }?.readBytes()
            else -> null
        } ?: throw ScrmLocalMediaException("Cannot read local media file")
        if (bytes.isEmpty()) {
            throw ScrmLocalMediaException("Local media file is empty")
        }
        return bytes
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme?.equals("content", ignoreCase = true) != true) return null
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun fallbackFileName(uri: Uri, mimeType: String?): String {
        val pathName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && it.contains('.') }
        if (pathName != null) return pathName
        val extension = when {
            mimeType?.contains("jpeg", ignoreCase = true) == true -> "jpg"
            mimeType?.contains("png", ignoreCase = true) == true -> "png"
            mimeType?.contains("video", ignoreCase = true) == true -> "mp4"
            mimeType?.contains("audio", ignoreCase = true) == true -> "m4a"
            else -> "bin"
        }
        return "scrm-media.$extension"
    }
}
