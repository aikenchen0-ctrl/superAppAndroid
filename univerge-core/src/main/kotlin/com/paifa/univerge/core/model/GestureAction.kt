/*
 * 功能概览：统一描述手势触发的系统动作、悬浮聊天动作和启动应用动作。
 * Kotlin 语法提示：`sealed class` 让编译器知道所有分支，配合 `when` 可获得穷尽性检查；`data object` 是带数据类语义的单例。
 */
package com.paifa.univerge.core.model

sealed class GestureAction(open val id: String) {
    data object None : GestureAction("none")
    data object Back : GestureAction("back")
    data object Home : GestureAction("home")
    data object Recents : GestureAction("recents")
    data object Notifications : GestureAction("notifications")
    data object QuickSettings : GestureAction("quick_settings")
    data object Screenshot : GestureAction("screenshot")
    data object LockScreen : GestureAction("lock_screen")
    data object VolumeUp : GestureAction("volume_up")
    data object VolumeDown : GestureAction("volume_down")
    data object ExpandFloatingChat : GestureAction("expand_floating_chat")
    data object CollapseFloatingChat : GestureAction("collapse_floating_chat")
    data object PlayVideo : GestureAction("play_video")
    data class LaunchApp(val packageName: String) : GestureAction("launch_app:$packageName")

    companion object {
        // 将 SharedPreferences 中保存的字符串还原为类型安全的动作对象。
        fun fromId(id: String): GestureAction {
            return when {
                id == None.id -> None
                id == Back.id -> Back
                id == Home.id -> Home
                id == Recents.id -> Recents
                id == Notifications.id -> Notifications
                id == QuickSettings.id -> QuickSettings
                id == Screenshot.id -> Screenshot
                id == LockScreen.id -> LockScreen
                id == VolumeUp.id -> VolumeUp
                id == VolumeDown.id -> VolumeDown
                id == ExpandFloatingChat.id -> ExpandFloatingChat
                id == CollapseFloatingChat.id -> CollapseFloatingChat
                id == PlayVideo.id -> PlayVideo
                id.startsWith("launch_app:") -> LaunchApp(id.removePrefix("launch_app:"))
                else -> None
            }
        }
    }
}
