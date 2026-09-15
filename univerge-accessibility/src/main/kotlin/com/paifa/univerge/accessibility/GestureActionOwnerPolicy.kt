package com.paifa.univerge.accessibility

internal enum class GestureActionSource {
    Command,
    Overlay,
    Native,
    NativeBottom,
    Compose,
    ComposeBottom,
    Server
}

/** Rejects terminal events that arrive after their physical input owner changed. */
internal fun isGestureActionSourceAllowed(
    source: GestureActionSource,
    floatingChatExpanded: Boolean,
    externalActivityVisible: Boolean,
    gestureServerOwnsInput: Boolean = false
): Boolean {
    if (source == GestureActionSource.Command) return true
    val floatingChatOwnsSurface = floatingChatOwnsGestureSurface(
        floatingChatExpanded = floatingChatExpanded,
        externalActivityVisible = externalActivityVisible
    )
    if (gestureServerOwnsInput) {
        // A terminal callback from the main service may arrive after the
        // isolated server has taken ownership. Reject it to prevent duplicate
        // actions; Compose is only valid when it owns the floating surface.
        return source == GestureActionSource.Server ||
            (floatingChatOwnsSurface &&
                (source == GestureActionSource.Compose || source == GestureActionSource.ComposeBottom))
    }
    return when (source) {
        GestureActionSource.Compose,
        GestureActionSource.ComposeBottom -> floatingChatOwnsSurface
        GestureActionSource.Native,
        GestureActionSource.NativeBottom,
        GestureActionSource.Overlay -> !floatingChatOwnsSurface
        GestureActionSource.Server -> false
        GestureActionSource.Command -> true
    }
}
