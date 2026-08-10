package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.accountScopedConversation
import com.paifa.ubikitouch.accessibility.floatingchat.chat.initialChatThreadSelection
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun FloatingChatConversationSyncEffects(
    conversation: FloatingChatConversation,
    runtimeState: FloatingChatOverlayRuntimeState,
    onLiveConversationChanged: (FloatingChatConversation) -> Unit,
    onActiveAccountIdChanged: (String) -> Unit,
    onSelectedThreadChanged: (ChatThreadSelection) -> Unit,
    onHomeOverviewVisibleChanged: (Boolean) -> Unit,
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
        onActiveAccountIdChanged(event.selectedAccountId)
        onSelectedThreadChanged(
            initialChatThreadSelection(
                conversation = accountScopedConversation(
                    conversation = event.conversation,
                    activeAccountId = event.selectedAccountId
                ),
                preferredSelection = event.selectedThread
            )
        )
        onHomeOverviewVisibleChanged(false)
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
