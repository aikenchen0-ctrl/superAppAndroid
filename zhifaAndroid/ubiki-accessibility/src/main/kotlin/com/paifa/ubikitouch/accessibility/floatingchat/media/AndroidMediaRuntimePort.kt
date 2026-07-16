package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal object AndroidMediaRuntimePort {
    fun share(context: Context, message: FloatingChatMessage): MediaActionResult {
        val uri = mediaActionUri(message) ?: return MediaActionResult("没有可分享的媒体")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType(message)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享媒体").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            MediaActionResult("已打开分享")
        }.getOrElse {
            MediaActionResult("没有可用的分享应用")
        }
    }

    fun save(context: Context, message: FloatingChatMessage): MediaActionResult {
        val sourceUri = mediaActionUri(message) ?: return MediaActionResult("没有可保存的媒体")
        if (sourceUri.scheme !in setOf("content", "file")) return MediaActionResult("网络媒体暂不支持直接保存")
        return runCatching {
            val resolver = context.contentResolver
            val isVideo = message.type.name == "VideoPreview"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "ubiki_${message.id}_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}")
                put(MediaStore.MediaColumns.MIME_TYPE, mediaMimeType(message))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory${File.separator}UbikiTouch")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val targetUri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            try {
                resolver.openInputStream(sourceUri).use { input ->
                    resolver.openOutputStream(targetUri).use { output ->
                        requireNotNull(input)
                        requireNotNull(output)
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) resolver.update(targetUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                MediaActionResult("已保存到相册")
            } catch (error: Throwable) {
                resolver.delete(targetUri, null, null)
                throw error
            }
        }.getOrElse { MediaActionResult("保存失败") }
    }
}
