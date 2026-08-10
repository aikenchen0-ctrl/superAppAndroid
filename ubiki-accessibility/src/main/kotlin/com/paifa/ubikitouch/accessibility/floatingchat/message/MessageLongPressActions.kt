package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal class MessageLongPressActions(
    private val favoriteMessageIds: MutableMap<String, Boolean>,
    private val reminderMessageIds: MutableMap<String, Boolean>,
    private val hiddenMessageIds: MutableMap<String, Boolean>,
    private val selectedMessageIds: MutableMap<String, Boolean>,
    private val onCopyText: (String) -> Unit,
    private val onShowToast: (String) -> Unit,
    private val onBeginForward: (List<FloatingChatMessage>) -> Unit,
    private val onFavoriteChanged: (FloatingChatMessage, Boolean) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit,
    private val onQuoteMessage: (FloatingChatMessage) -> Unit,
    private val onCloseLongPressMenu: () -> Unit
) {
    fun performLongPressAction(message: FloatingChatMessage, action: MessageLongPressAction) {
        when (action) {
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
            MessageLongPressAction.Reminder -> {
                val nextReminder = reminderMessageIds[message.id] != true
                reminderMessageIds[message.id] = nextReminder
                onShowToast(if (nextReminder) "已提醒" else "已取消提醒")
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
