package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.core.model.FloatingChatConversation

@Composable
internal fun ChatThreadEffects(
    effectiveConversation: FloatingChatConversation,
    selectedThread: ChatThreadSelection,
    selectedAccountId: String,
    unreadThreadIds: MutableMap<String, Boolean>,
    runtimeState: FloatingChatOverlayRuntimeState,
    onSelectedThreadChanged: (ChatThreadSelection) -> Unit,
    onThreadContextChanged: (ChatThreadSelection, String) -> Unit
) {
    LaunchedEffect(effectiveConversation.groupContacts, effectiveConversation.contacts) {
        onSelectedThreadChanged(initialChatThreadSelection(effectiveConversation, selectedThread))
        val defaultUnreadThreadIds = defaultHomeUnreadThreadIds(effectiveConversation)
        unreadThreadIds.keys
            .filterNot { threadId -> threadId in defaultUnreadThreadIds }
            .forEach { threadId -> unreadThreadIds.remove(threadId) }
        defaultUnreadThreadIds.forEach { threadId ->
            if (!unreadThreadIds.containsKey(threadId)) {
                unreadThreadIds[threadId] = true
            }
        }
    }

    LaunchedEffect(selectedThread, selectedAccountId) {
        runtimeState.selectedThread = selectedThread
        onThreadContextChanged(selectedThread, selectedAccountId)
    }
}
