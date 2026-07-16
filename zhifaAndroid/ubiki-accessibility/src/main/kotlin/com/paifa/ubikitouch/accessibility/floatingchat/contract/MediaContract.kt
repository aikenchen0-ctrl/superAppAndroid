package com.paifa.ubikitouch.accessibility.floatingchat.contract

sealed interface MediaUiEvent {
    data class DocumentClicked(val messageId: String) : MediaUiEvent
    data class PickerClicked(val kind: MediaPickerKind) : MediaUiEvent
    data object CameraClicked : MediaUiEvent
    data object PreviewDismissed : MediaUiEvent
}

interface MediaPlatformPort {
    fun openDocument(messageId: String): Boolean
    fun openPicker(kind: MediaPickerKind)
    fun openCamera()
    fun closePreview()
}
