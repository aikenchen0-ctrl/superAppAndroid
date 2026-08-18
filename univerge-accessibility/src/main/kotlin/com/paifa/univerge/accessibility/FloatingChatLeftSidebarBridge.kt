package com.paifa.univerge.accessibility

import android.content.Context
import com.paifa.univerge.core.model.FloatingChatContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 与 IosFloat 的 LeftSidebarDisplayMode 原始值保持一致，供左侧栏和全屏设置页共享。 */
enum class LeftSidebarDisplayMode(
    val rawValue: String,
    val title: String
) {
    All("friendsAndGroups", "好友和群聊"),
    Friends("friends", "好友用户"),
    Groups("groups", "群聊");

    companion object {
        fun fromRawValue(rawValue: String?): LeftSidebarDisplayMode =
            entries.firstOrNull { it.rawValue == rawValue } ?: All
    }
}

data class FloatingChatLeftSidebarSnapshot(
    val accounts: List<FloatingChatContact> = emptyList(),
    val selectedAccountId: String? = null,
    val contacts: List<FloatingChatContact> = emptyList(),
    val groups: List<FloatingChatContact> = emptyList()
)

/**
 * 左侧全部的 UI 与浮窗会话数据边界。
 * 测试流程：从右侧“左侧全部”打开，切换任一模式后返回聊天页，确认左栏同步筛选且重启服务后保留选择。
 */
object FloatingChatLeftSidebarBridge {
    private const val PREFERENCES_NAME = "floating_chat_left_sidebar"
    private const val DISPLAY_MODE_KEY = "ChatWindowLeftSidebarDisplayMode"

    private val mutableSnapshot = MutableStateFlow(FloatingChatLeftSidebarSnapshot())
    val snapshot: StateFlow<FloatingChatLeftSidebarSnapshot> = mutableSnapshot.asStateFlow()

    private val mutableDisplayMode = MutableStateFlow(LeftSidebarDisplayMode.All)
    val displayMode: StateFlow<LeftSidebarDisplayMode> = mutableDisplayMode.asStateFlow()

    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatLeftSidebar()
    }

    internal fun updateSnapshot(value: FloatingChatLeftSidebarSnapshot) {
        mutableSnapshot.value = value
    }

    /** 读取 iOS 同名键的原始值，未知值回退到 iOS 的默认“好友和群聊”。 */
    fun loadDisplayMode(context: Context) {
        val rawValue = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(DISPLAY_MODE_KEY, null)
        mutableDisplayMode.value = LeftSidebarDisplayMode.fromRawValue(rawValue)
    }

    /** 用户选择后立即持久化，并通过 StateFlow 驱动已显示的左侧栏刷新。 */
    fun selectDisplayMode(context: Context, mode: LeftSidebarDisplayMode) {
        if (mutableDisplayMode.value == mode) return
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(DISPLAY_MODE_KEY, mode.rawValue)
            .apply()
        mutableDisplayMode.value = mode
    }
}
