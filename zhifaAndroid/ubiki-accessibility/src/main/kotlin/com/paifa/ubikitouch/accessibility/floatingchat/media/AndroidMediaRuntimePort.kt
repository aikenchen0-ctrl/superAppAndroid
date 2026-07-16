package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.content.Intent
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
}
