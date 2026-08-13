package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction

internal sealed interface ToolActionDispatch {
    data object PickGalleryMedia : ToolActionDispatch
    data object CaptureBlinkVoice : ToolActionDispatch
    data object CaptureCameraMedia : ToolActionDispatch
    data object PickDocument : ToolActionDispatch
    data object OpenAssistantPanel : ToolActionDispatch
    data object OpenAiVoicePanel : ToolActionDispatch
    data object OpenApiWorkbench : ToolActionDispatch
    data object OpenFinderPublish : ToolActionDispatch
    data object OpenMaterialLibrary : ToolActionDispatch
    data object OpenFavoriteLibrary : ToolActionDispatch
    data class OpenBottomPanel(val mode: BottomPanelMode) : ToolActionDispatch
    data object AddSimulatedMessage : ToolActionDispatch
    data object None : ToolActionDispatch
}

internal fun toolActionDispatchFor(action: FloatingChatToolAction): ToolActionDispatch {
    return when (action) {
        FloatingChatToolAction.Gallery -> ToolActionDispatch.PickGalleryMedia
        FloatingChatToolAction.Blink -> ToolActionDispatch.CaptureBlinkVoice
        FloatingChatToolAction.Camera -> ToolActionDispatch.CaptureCameraMedia
        FloatingChatToolAction.QuickPhrase -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.QuickPhrase)
        FloatingChatToolAction.Moments -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Moments)
        FloatingChatToolAction.Finder -> ToolActionDispatch.OpenFinderPublish
        FloatingChatToolAction.MomentMaterials -> ToolActionDispatch.OpenMaterialLibrary
        FloatingChatToolAction.RedPacket -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.RedPacket)
        FloatingChatToolAction.Transfer -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Transfer)
        FloatingChatToolAction.Location -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Location)
        FloatingChatToolAction.Favorite -> ToolActionDispatch.OpenFavoriteLibrary
        FloatingChatToolAction.Card -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Card)
        FloatingChatToolAction.Files -> ToolActionDispatch.PickDocument
        FloatingChatToolAction.Assistant -> ToolActionDispatch.OpenAssistantPanel
        FloatingChatToolAction.AiVoice -> ToolActionDispatch.OpenAiVoicePanel
        FloatingChatToolAction.Contacts -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Contacts)
        FloatingChatToolAction.Device -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.AccountDevice)
        FloatingChatToolAction.Notes -> ToolActionDispatch.OpenBottomPanel(BottomPanelMode.CustomerProfile)
        FloatingChatToolAction.Command -> ToolActionDispatch.OpenApiWorkbench
        in simulatedMessageToolActions() -> ToolActionDispatch.AddSimulatedMessage
        else -> ToolActionDispatch.None
    }
}
