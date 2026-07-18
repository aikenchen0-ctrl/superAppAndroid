package com.paifa.ubikitouch.accessibility.floatingchat.media

internal fun videoAspectRatioFromDimensions(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0
): Float? {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val rotated = normalizedRotation == 90 || normalizedRotation == 270
    val displayWidth = if (rotated) safeHeight else safeWidth
    val displayHeight = if (rotated) safeWidth else safeHeight
    return if (displayWidth > 0 && displayHeight > 0) {
        displayWidth.toFloat() / displayHeight.toFloat()
    } else {
        null
    }
}

/** Compatibility entry point retained for existing UI contract tests. */
internal fun mediaAspectRatioFromDimensions(
    width: Int,
    height: Int,
    rotationDegrees: Int = 0
): Float? {
    return videoAspectRatioFromDimensions(width, height, rotationDegrees)
}
