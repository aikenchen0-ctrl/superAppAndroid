package com.paifa.ubikitouch.app

internal class FloatingChatMediaPickerLifecycle(
    private val notifyPickerClosed: () -> Unit,
    private val finishPicker: () -> Unit
) {
    private var processingPickedMedia = false
    private var pickerClosedNotified = false
    private var finishRequested = false

    fun startPickedMediaProcessing() {
        processingPickedMedia = true
    }

    fun completePickedMediaProcessing(deliverPickedMedia: () -> Unit) {
        processingPickedMedia = false
        notifyPickerClosedOnce()
        deliverPickedMedia()
        finishPickerOnce()
    }

    fun cancelPicker() {
        processingPickedMedia = false
        notifyPickerClosedOnce()
        finishPickerOnce()
    }

    fun onDestroy(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations && !processingPickedMedia && !finishRequested) {
            notifyPickerClosedOnce()
        }
    }

    private fun notifyPickerClosedOnce() {
        if (pickerClosedNotified) return
        pickerClosedNotified = true
        notifyPickerClosed()
    }

    private fun finishPickerOnce() {
        if (finishRequested) return
        finishRequested = true
        finishPicker()
    }
}
