package com.paifa.ubikitouch.core.model

object GestureActionCatalog {
    val systemActions: List<GestureAction> = listOf(
        GestureAction.None,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.Notifications,
        GestureAction.QuickSettings,
        GestureAction.Screenshot,
        GestureAction.LockScreen,
        GestureAction.VolumeUp,
        GestureAction.VolumeDown,
        GestureAction.ExpandFloatingChat,
        GestureAction.CollapseFloatingChat
    )
}
