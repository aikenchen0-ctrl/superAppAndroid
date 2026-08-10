package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.FloatingChatMediaTarget
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.account.accountProfileCardMessage
import com.paifa.ubikitouch.accessibility.floatingchat.account.accountProfileMessageForToolAction
import com.paifa.ubikitouch.accessibility.floatingchat.message.OutgoingMessageActions
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal class ToolMessageActions(
    private val conversation: () -> FloatingChatConversation,
    private val fallbackConversation: () -> FloatingChatConversation,
    private val selectedAccount: () -> FloatingChatContact,
    private val accountProfile: (FloatingChatContact) -> FloatingChatAccountProfile,
    private val outgoingMessageActions: OutgoingMessageActions,
    private val onPendingAvatarAccountIdChanged: (String?) -> Unit,
    private val onBottomPanelModeChanged: (BottomPanelMode) -> Unit,
    private val onAssistantPanelOpened: () -> Unit,
    private val onAiVoicePanelOpened: () -> Unit
) {
    fun pickAccountAvatar(accountId: String) {
        onPendingAvatarAccountIdChanged(accountId)
        FloatingChatMediaPickerBridge.requestPick(
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            target = FloatingChatMediaTarget.AccountAvatar
        )
    }

    fun addToolMessage(
        action: FloatingChatToolAction,
        customize: (FloatingChatMessage) -> FloatingChatMessage = { it }
    ) {
        val account = selectedAccount()
        outgoingMessageActions.addToolMessage(action) { baseMessage ->
            customize(
                accountProfileMessageForToolAction(
                    action = action,
                    profile = accountProfile(account),
                    baseMessage = baseMessage
                )
            )
        }
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    fun sendAccountCard(accountId: String) {
        val account = conversation().accountContacts.firstOrNull { it.id == accountId }
            ?: fallbackConversation().accountContacts.firstOrNull { it.id == accountId }
            ?: return
        outgoingMessageActions.addAccountCardMessage(account) { baseMessage ->
            accountProfileCardMessage(profile = accountProfile(account), baseMessage = baseMessage)
        }
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    fun sendToolMessage(action: FloatingChatToolAction) {
        when (val dispatch = toolActionDispatchFor(action)) {
            ToolActionDispatch.PickGalleryMedia -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatMediaPickerBridge.requestPick(FloatingChatPrototype.PickedMediaKind.Any)
            }
            ToolActionDispatch.CaptureBlinkVoice -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatBlinkVoiceBridge.requestCapture()
            }
            ToolActionDispatch.CaptureCameraMedia -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatMediaPickerBridge.requestCapture()
            }
            ToolActionDispatch.PickDocument -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatMediaPickerBridge.requestDocumentPick()
            }
            ToolActionDispatch.OpenAssistantPanel -> onAssistantPanelOpened()
            ToolActionDispatch.OpenAiVoicePanel -> onAiVoicePanelOpened()
            is ToolActionDispatch.OpenBottomPanel -> onBottomPanelModeChanged(dispatch.mode)
            ToolActionDispatch.AddSimulatedMessage -> addToolMessage(action)
            ToolActionDispatch.None -> Unit
        }
    }
}
