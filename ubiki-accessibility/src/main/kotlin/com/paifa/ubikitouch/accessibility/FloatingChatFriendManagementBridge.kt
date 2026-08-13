package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.FloatingChatContact

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
        UbikiAccessibilityService.instance?.requestFloatingChatFriendManagement()
    }

    internal fun updateSnapshot(value: FloatingChatFriendManagementSnapshot) {
        snapshot = value
    }

    fun notifyClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatFriendManagementClosed()
    }

    fun refresh() {
        UbikiAccessibilityService.instance?.refreshFloatingChatFriendManagement()
    }

    fun pullFriendRequests() {
        UbikiAccessibilityService.instance?.pullFloatingChatFriendRequests()
    }
}
