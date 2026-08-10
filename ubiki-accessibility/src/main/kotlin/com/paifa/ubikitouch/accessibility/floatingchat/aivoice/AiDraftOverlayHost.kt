package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.paifa.ubikitouch.accessibility.FloatingChatAiDraftAction
import com.paifa.ubikitouch.accessibility.floatingchat.message.AiDraftActionOverlay
import com.paifa.ubikitouch.accessibility.floatingchat.message.AiDraftEditOverlay
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun AiDraftOverlayHost(
    actionMessage: FloatingChatMessage?,
    editMessage: FloatingChatMessage?,
    draftMessageActions: AiDraftMessageActions,
    draftGenerationActions: AiDraftGenerationActions,
    onActionMessageChanged: (FloatingChatMessage?) -> Unit,
    onEditMessageChanged: (FloatingChatMessage?) -> Unit,
    modifier: Modifier = Modifier
) {
    actionMessage?.let { message ->
        AiDraftActionOverlay(
            message = message,
            onDismiss = { onActionMessageChanged(null) },
            onAction = { action ->
                when (action) {
                    FloatingChatAiDraftAction.Edit -> {
                        onEditMessageChanged(message)
                    }
                    FloatingChatAiDraftAction.Regenerate -> {
                        draftGenerationActions.generate(replaceMessage = message)
                    }
                    FloatingChatAiDraftAction.Cancel -> {
                        draftMessageActions.removeDraftMessage(message)
                    }
                    FloatingChatAiDraftAction.Send -> {
                        draftMessageActions.sendDraftMessage(message)
                    }
                }
                onActionMessageChanged(null)
            },
            modifier = modifier.fillMaxSize()
        )
    }
    editMessage?.let { message ->
        AiDraftEditOverlay(
            message = message,
            onDismiss = { onEditMessageChanged(null) },
            onSave = { text ->
                draftMessageActions.updateDraftText(message, text)
                onEditMessageChanged(null)
            },
            onSend = { text -> draftMessageActions.sendDraftMessage(message, text) },
            modifier = modifier.fillMaxSize()
        )
    }
}
