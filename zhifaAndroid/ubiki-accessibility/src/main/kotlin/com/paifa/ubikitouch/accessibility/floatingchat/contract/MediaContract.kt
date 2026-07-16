package com.paifa.ubikitouch.accessibility.floatingchat.contract

sealed interface MediaUiEvent {
    data class DocumentClicked(val messageId: String) : MediaUiEvent
}

interface MediaPlatformPort {
    fun openDocument(messageId: String): Boolean
    fun openPicker(kind: MediaPickerKind)
    fun openCamera()
    fun closePreview()
}
