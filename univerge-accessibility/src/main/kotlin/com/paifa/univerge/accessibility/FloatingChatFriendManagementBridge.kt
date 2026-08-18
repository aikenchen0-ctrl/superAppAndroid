package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.FloatingChatContact

data class FloatingChatFriendManagementSnapshot(
    val accounts: List<FloatingChatContact>,
    val selectedAccountId: String?,
    val contacts: List<FloatingChatContact>,
    val groups: List<FloatingChatContact>
)

object FloatingChatFriendManagementBridge {
    @Volatile
    var snapshot = FloatingChatFriendManagementSnapshot(emptyList(), null, emptyList(), emptyList())
        private set

    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatFriendManagement()
    }

    internal fun updateSnapshot(value: FloatingChatFriendManagementSnapshot) {
        snapshot = value
    }

    fun notifyClosed() {
        UniVergeAccessibilityService.instance?.onFloatingChatFriendManagementClosed()
    }

    fun refresh() {
        UniVergeAccessibilityService.instance?.refreshFloatingChatFriendManagement()
    }

    fun pullFriendRequests() {
        UniVergeAccessibilityService.instance?.pullFloatingChatFriendRequests()
    }
}
