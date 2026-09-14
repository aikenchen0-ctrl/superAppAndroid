package com.paifa.univerge.accessibility

internal enum class GestureActionSource {
    Command,
    Overlay,
    Native,
    NativeBottom,
    Compose,
    ComposeBottom
}

/** Rejects terminal events that arrive after their physical input owner changed. */
internal fun isGestureActionSourceAllowed(
    source: GestureActionSource,
    floatingChatExpanded: Boolean,
    externalActivityVisible: Boolean
): Boolean {
    if (source == GestureActionSource.Command) return true
    val floatingChatOwnsSurface = floatingChatOwnsGestureSurface(
        floatingChatExpanded = floatingChatExpanded,
        externalActivityVisible = externalActivityVisible
    )
    return when (source) {
        GestureActionSource.Compose,
        GestureActionSource.ComposeBottom -> floatingChatOwnsSurface
        GestureActionSource.Native,
        GestureActionSource.NativeBottom,
        GestureActionSource.Overlay -> !floatingChatOwnsSurface
        GestureActionSource.Command -> true
    }
}
