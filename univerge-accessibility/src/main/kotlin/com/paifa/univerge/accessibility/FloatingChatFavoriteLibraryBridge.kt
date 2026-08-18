package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.floatingchat.tools.FavoriteCollectionItem

internal object FloatingChatFavoriteLibraryBridge {
    @Volatile
    private var snapshot: FloatingChatFavoriteLibrarySnapshot? = null

    fun open() { UniVergeAccessibilityService.instance?.requestFloatingChatFavoriteLibrary() }

    fun updateSnapshot(value: FloatingChatFavoriteLibrarySnapshot) {
        snapshot = value
    }

    fun currentSnapshot(): FloatingChatFavoriteLibrarySnapshot? = snapshot

    fun send(item: FavoriteCollectionItem): Boolean {
        return UniVergeAccessibilityService.instance?.sendFloatingChatFavorite(item) == true
    }
}

internal data class FloatingChatFavoriteLibrarySnapshot(
    val accountId: String,
    val accountName: String
)
