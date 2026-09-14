package com.paifa.univerge.accessibility

/**
 * Orders cleanup for a native transaction that lost its terminal event.
 * The callbacks are injected so the ordering remains testable without an
 * AccessibilityService or a WindowManager.
 */
internal class NativeGestureSessionAbort(
    private val cancelCore: () -> Unit,
    private val cancelBackProgress: () -> Unit,
    private val endPreview: () -> Unit,
    private val clearFields: () -> Unit
) {
    fun abort(
        coreActive: Boolean,
        backProgressActive: Boolean,
        previewActive: Boolean
    ): Boolean {
        val active = coreActive || backProgressActive || previewActive
        if (active) {
            cancelCore()
            if (backProgressActive) cancelBackProgress()
            if (previewActive) endPreview()
        }
        clearFields()
        return active
    }
}
