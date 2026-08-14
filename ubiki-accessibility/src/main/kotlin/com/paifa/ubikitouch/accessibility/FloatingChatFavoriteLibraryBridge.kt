package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionItem

internal object FloatingChatFavoriteLibraryBridge {
    @Volatile
    private var snapshot: FloatingChatFavoriteLibrarySnapshot? = null

    fun open() { UbikiAccessibilityService.instance?.requestFloatingChatFavoriteLibrary() }

    fun updateSnapshot(value: FloatingChatFavoriteLibrarySnapshot) {
        snapshot = value
    }

    fun currentSnapshot(): FloatingChatFavoriteLibrarySnapshot? = snapshot

    fun send(item: FavoriteCollectionItem): Boolean {
        return UbikiAccessibilityService.instance?.sendFloatingChatFavorite(item) == true
    }
}

internal data class FloatingChatFavoriteLibrarySnapshot(
    val accountId: String,
    val accountName: String
)
