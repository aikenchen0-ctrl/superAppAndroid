package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class FloatingChatShellState(
    val expanded: Boolean = false,
    val activeAccountId: String? = null,
    val activePanel: FloatingChatPanel = FloatingChatPanel.Chat,
    val previewVisible: Boolean = false
)

enum class FloatingChatPanel {
    Chat,
    Contacts,
    Moments,
    Favorites,
    Tools
}

sealed interface FloatingChatShellEvent {
    data object ExpandRequested : FloatingChatShellEvent
    data object CollapseRequested : FloatingChatShellEvent
    data class AccountSelected(val accountId: String) : FloatingChatShellEvent
    data class PanelSelected(val panel: FloatingChatPanel) : FloatingChatShellEvent
    data object PreviewDismissed : FloatingChatShellEvent
}

sealed interface FloatingChatEffect {
    data class OpenDocument(val messageId: String) : FloatingChatEffect
    data class RequestPermission(val permission: PermissionKind) : FloatingChatEffect
    data class OpenMediaPicker(val kind: MediaPickerKind) : FloatingChatEffect
    data class ShowMessage(val text: String) : FloatingChatEffect
}

enum class PermissionKind {
    Camera,
    Microphone,
    Location
}

enum class MediaPickerKind {
    Image,
    Video,
    Document
}

sealed interface FloatingChatWindowCommand {
    data object Expand : FloatingChatWindowCommand
    data object Collapse : FloatingChatWindowCommand
    data object DismissPreview : FloatingChatWindowCommand
}
