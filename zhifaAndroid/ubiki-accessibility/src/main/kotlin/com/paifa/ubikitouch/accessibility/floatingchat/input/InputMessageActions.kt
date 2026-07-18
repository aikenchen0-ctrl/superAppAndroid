package com.paifa.ubikitouch.accessibility.floatingchat.input

import com.paifa.ubikitouch.accessibility.floatingchat.message.OutgoingMessageActions
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal class InputMessageActions(
    private val outgoingMessageActions: OutgoingMessageActions,
    private val quotedMessage: () -> FloatingChatMessage?,
    private val inputText: () -> String,
    private val onQuotedMessageChanged: (FloatingChatMessage?) -> Unit,
    private val onInputTextChanged: (String) -> Unit,
    private val onBlinkGeneratedInputClearableChanged: (Boolean) -> Unit,
    private val onBottomPanelModeChanged: (BottomPanelMode) -> Unit,
    private val onBlinkInputStatusChanged: (String?, Boolean) -> Unit,
    private val onBlinkInputStatusVersionIncremented: () -> Unit
) {
    fun sendTextToCurrentThread(outgoingText: String) {
        outgoingMessageActions.addTextMessage(outgoingText, quotedMessage())
        onQuotedMessageChanged(null)
    }

    fun sendInputMessage() {
        val outgoingText = inputText().trim()
        if (outgoingText.isNotEmpty()) {
            sendTextToCurrentThread(outgoingText)
            onInputTextChanged("")
            onBlinkGeneratedInputClearableChanged(false)
            onBottomPanelModeChanged(BottomPanelMode.None)
        }
    }

    fun sendVoiceMessage(audioUri: String, durationMs: Int) {
        outgoingMessageActions.addVoiceMessage(audioUri, durationMs)
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    fun showBlinkInputStatus(message: String, autoDismiss: Boolean) {
        onBlinkInputStatusChanged(message, autoDismiss)
        onBlinkInputStatusVersionIncremented()
    }

    fun clearBlinkInputStatus() {
        onBlinkInputStatusChanged(null, false)
    }
}
