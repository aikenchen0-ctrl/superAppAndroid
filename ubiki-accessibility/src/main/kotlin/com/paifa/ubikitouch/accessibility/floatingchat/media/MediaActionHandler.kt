package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import com.paifa.ubikitouch.core.model.FloatingChatMessage

/**
 * Translates platform-neutral media actions into UI-facing results.
 * UI callers own selection state; Android URI, intent, and MediaStore work stays behind [MediaRuntimePort].
 */
internal fun performMediaAction(
    context: Context,
    message: FloatingChatMessage,
    action: MediaActionContract,
    favoriteMediaIds: MutableMap<String, Boolean>,
    onOpenActions: () -> Unit,
    onFavoriteChanged: (FloatingChatMessage, Boolean) -> Unit = { _, _ -> },
    runtimePort: MediaRuntimePort = AndroidMediaRuntimePort
): MediaActionResult {
    return when (action) {
        MediaActionContract.AnalyzeImage -> MediaActionResult("已打开识图入口")
        MediaActionContract.FindObject -> MediaActionResult("已打开找物入口")
        MediaActionContract.Share -> runtimePort.share(context, message)
        MediaActionContract.Save -> runtimePort.save(context, message)
        MediaActionContract.Favorite -> {
            val nextFavorite = favoriteMediaIds[message.id] != true
            favoriteMediaIds[message.id] = nextFavorite
            onFavoriteChanged(message, nextFavorite)
            MediaActionResult(if (nextFavorite) "已收藏" else "已取消收藏")
        }
        MediaActionContract.More -> {
            onOpenActions()
            MediaActionResult("更多选项", toast = false)
        }
        MediaActionContract.Visibility -> MediaActionResult("可见范围：${message.visibility?.label ?: "未设置"}")
        MediaActionContract.Edit -> MediaActionResult("编辑入口已打开")
        MediaActionContract.Comment -> MediaActionResult("已定位到聊天")
        MediaActionContract.Grid -> MediaActionResult("图片管理入口已打开")
    }
}
