package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.chat.accountIdForScopedThreadId
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal data class MessageAsideAnalysis(
    val emotion: String,
    val stance: String,
    val subtext: String
)

internal enum class MessageZoomMode {
    Text,
    Media,
    Unsupported
}

internal fun messageZoomMode(type: FloatingChatMessageType): MessageZoomMode {
    return when (type) {
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText -> MessageZoomMode.Text
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.CapturedPhoto,
        FloatingChatMessageType.VideoPreview,
        FloatingChatMessageType.ChannelsVideo -> MessageZoomMode.Media
        else -> MessageZoomMode.Unsupported
    }
}

internal fun parseMessageAsideAnalysis(response: String): MessageAsideAnalysis {
    val fields = response.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val normalized = line
                .removePrefix("-")
                .removePrefix("*")
                .trim()
                .replace("**", "")
            val separatorIndex = normalized.indexOfFirst { char -> char == '：' || char == ':' }
            if (separatorIndex <= 0) return@mapNotNull null
            normalized.substring(0, separatorIndex).trim() to normalized.substring(separatorIndex + 1).trim()
        }
        .filter { (_, value) -> value.isNotBlank() }
        .toMap()
    val emotion = fields["情绪"]
    val stance = fields["立场"]
    val subtext = fields["话外音"]
    if (emotion == null || stance == null || subtext == null) {
        throw IllegalStateException("AI 分析结果缺少情绪、立场或话外音")
    }
    return MessageAsideAnalysis(emotion = emotion, stance = stance, subtext = subtext)
}

/**
 * 转文字应跟随被点击消息的账号作用域；旧的非作用域消息才回退到当前选中账号。
 * 测试流程：在“全部未回消息”中点击其他账号的语音消息，操作菜单选择“转文字”。
 */
internal fun messageOperationAccountId(
    message: FloatingChatMessage,
    fallbackAccountId: String
): String {
    return message.threadContactId
        ?.let(::accountIdForScopedThreadId)
        ?: fallbackAccountId
}

internal fun voiceTranscriptionAccountId(
    message: FloatingChatMessage,
    fallbackAccountId: String
): String = messageOperationAccountId(message, fallbackAccountId)

internal class MessageLongPressActions(
    private val favoriteMessageIds: MutableMap<String, Boolean>,
    private val hiddenMessageIds: MutableMap<String, Boolean>,
    private val selectedMessageIds: MutableMap<String, Boolean>,
    private val onCopyText: (String) -> Unit,
    private val onShowToast: (String) -> Unit,
    private val onBeginForward: (List<FloatingChatMessage>) -> Unit,
    private val onFavoriteChanged: (FloatingChatMessage, Boolean) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit,
    private val onQuoteMessage: (FloatingChatMessage) -> Unit,
    private val onListenMessage: (FloatingChatMessage) -> Unit,
    private val onZoomMessage: (FloatingChatMessage) -> Unit,
    private val onTranscribeMessage: (FloatingChatMessage) -> Unit,
    private val onRevokeMessageRequested: (FloatingChatMessage) -> Unit,
    private val onScrmOperationRequested: (FloatingChatMessage) -> Unit,
    private val onCloseLongPressMenu: () -> Unit
) {
    fun performLongPressAction(message: FloatingChatMessage, action: MessageLongPressAction) {
        when (action) {
            MessageLongPressAction.Transcribe -> {
                // 接口：由悬浮聊天宿主使用消息的 SCRM remoteMessageId 提交真实语音转写任务。
                onTranscribeMessage(message)
            }
            MessageLongPressAction.Revoke -> {
                onRevokeMessageRequested(message)
            }
            MessageLongPressAction.Listen -> {
                onListenMessage(message)
            }
            MessageLongPressAction.Zoom -> {
                onZoomMessage(message)
            }
            MessageLongPressAction.Copy -> {
                onCopyText(message.longPressCopyText())
                onShowToast("已复制")
            }
            MessageLongPressAction.Forward -> {
                onBeginForward(listOf(message))
                onCloseLongPressMenu()
            }
            MessageLongPressAction.Favorite -> {
                val nextFavorite = favoriteMessageIds[message.id] != true
                favoriteMessageIds[message.id] = nextFavorite
                onFavoriteChanged(message, nextFavorite)
                onShowToast(if (nextFavorite) "已收藏" else "已取消收藏")
            }
            MessageLongPressAction.Delete -> {
                hiddenMessageIds[message.id] = true
                selectedMessageIds.remove(message.id)
                onShowToast("已删除")
            }
            MessageLongPressAction.MultiSelect -> {
                onMultiSelectModeChanged(true)
                selectedMessageIds[message.id] = true
            }
            MessageLongPressAction.Quote -> {
                onQuoteMessage(message)
            }
            MessageLongPressAction.ScrmOperations -> {
                // UI test: long-press a message -> More. This only opens a request preview.
                // Do not invoke a write task until a human explicitly performs the final test.
                onScrmOperationRequested(message)
            }
        }
        onCloseLongPressMenu()
    }

    fun deleteSelectedMessages(messages: List<FloatingChatMessage>) {
        messages.forEach { message ->
            hiddenMessageIds[message.id] = true
            selectedMessageIds.remove(message.id)
        }
        onMultiSelectModeChanged(false)
        onShowToast("已删除选中消息")
    }

    fun favoriteSelectedMessages(messages: List<FloatingChatMessage>) {
        messages.forEach { message ->
            favoriteMessageIds[message.id] = true
            onFavoriteChanged(message, true)
        }
        onMultiSelectModeChanged(false)
        onShowToast("已收藏选中消息")
    }
}
