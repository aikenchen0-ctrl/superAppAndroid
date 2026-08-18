/* 功能概览：集中提供设置页允许用户选择的系统动作列表。 */
package com.paifa.univerge.core.model

// `object` 是 Kotlin 单例，访问 `GestureActionCatalog.systemActions` 不需要 new。
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
        GestureAction.CollapseFloatingChat,
        GestureAction.PlayVideo
    )
}
