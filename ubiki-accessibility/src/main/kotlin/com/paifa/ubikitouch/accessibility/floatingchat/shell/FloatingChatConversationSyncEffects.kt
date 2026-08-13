package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatNavigationState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.syncChatNavigationState
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun FloatingChatConversationSyncEffects(
    conversation: FloatingChatConversation,
    runtimeState: FloatingChatOverlayRuntimeState,
    onLiveConversationChanged: (FloatingChatConversation) -> Unit,
    chatNavigationState: ChatNavigationState,
    onChatNavigationStateChanged: (ChatNavigationState) -> Unit,
    onLocalMessagesReplaced: (List<FloatingChatMessage>) -> Unit,
    onLocalMessageSequenceChanged: (Int) -> Unit,
    onLocalMessagesSynced: () -> Unit
) {
    LaunchedEffect(conversation) {
        onLiveConversationChanged(conversation)
    }

    LaunchedEffect(runtimeState.conversationUpdateEvent) {
        val event = runtimeState.conversationUpdateEvent ?: return@LaunchedEffect
        onLiveConversationChanged(event.conversation)
        onChatNavigationStateChanged(
            syncChatNavigationState(
                current = chatNavigationState,
                conversation = event.conversation,
                controllerAccountId = event.selectedAccountId,
                controllerThread = event.selectedThread
            )
        )
        runtimeState.clearConversationUpdate(event.token)
    }

    LaunchedEffect(runtimeState.localMessagesUpdateEvent) {
        val event = runtimeState.localMessagesUpdateEvent ?: return@LaunchedEffect
        onLocalMessagesReplaced(event.messages)
        onLocalMessageSequenceChanged(event.messageSequence)
        onLocalMessagesSynced()
        runtimeState.clearLocalMessagesUpdate(event.token)
    }
}
