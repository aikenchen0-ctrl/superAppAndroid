package com.paifa.univerge.accessibility

import com.paifa.univerge.accessibility.floatingchat.tools.FavoriteCollectionItem
import com.paifa.univerge.accessibility.floatingchat.tools.favoriteCollectionItemsForAccount
import com.paifa.univerge.accessibility.floatingchat.tools.refreshFavoriteCollectionSourcesFromMessages
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountScopedContentTest {
    @Test
    fun favoritesOnlyContainItemsOwnedBySelectedAccount() {
        val accountA = FavoriteCollectionItem(
            accountId = "account-a",
            messageId = "message-a",
            type = FloatingChatMessageType.Text,
            title = "A 的收藏",
            description = "仅账号 A 可见",
            source = "联系人 A · 今天"
        )
        val accountB = accountA.copy(
            accountId = "account-b",
            messageId = "message-b",
            title = "B 的收藏"
        )

        assertEquals(listOf(accountB), favoriteCollectionItemsForAccount(listOf(accountA, accountB), "account-b"))
    }

    @Test
    fun momentsOnlyContainPostsOwnedBySelectedAccount() {
        val accountA = AppMomentPost(
            accountId = "account-a",
            author = "账号 A",
            content = "A 的朋友圈",
            time = "刚刚"
        )
        val accountB = accountA.copy(accountId = "account-b", content = "B 的朋友圈")

        assertEquals(listOf(accountA), momentPostsForAccount(listOf(accountA, accountB), "account-a"))
    }

    @Test
    fun refreshingCurrentAccountDoesNotRewriteAnotherAccountsFavorite() {
        val otherAccountItem = FavoriteCollectionItem(
            accountId = "account-a",
            messageId = "shared-message-id",
            type = FloatingChatMessageType.Text,
            title = "收藏内容",
            description = "描述",
            source = "账号 A 联系人 · 09:00"
        )
        val currentAccountMessage = FloatingChatMessage(
            id = "shared-message-id",
            type = FloatingChatMessageType.Text,
            text = "当前账号消息",
            fromMe = false,
            senderName = "账号 B 联系人",
            time = "10:00"
        )

        val refreshed = refreshFavoriteCollectionSourcesFromMessages(
            items = listOf(otherAccountItem),
            messages = listOf(currentAccountMessage),
            accountId = "account-b"
        )

        assertEquals(otherAccountItem, refreshed.single())
    }
}
