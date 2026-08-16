package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.scrm.ScrmDevice
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatMessage
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountConversation
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoom
import com.paifa.ubikitouch.accessibility.scrm.ScrmWechatAccount
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatOverlayControllerContractTest {
    @Test
    fun scrmRefreshAlwaysLoadsSelectedAccountDetails() {
        val selected = ScrmFloatingAccountRoute("device-5", "wxid_5")

        assertEquals(selected, scrmSelectedConversationRouteToRefresh(selected))
    }

    @Test
    fun localMessagePreloadDoesNotQueryEveryContactThread() {
        val selected = FloatingChatPrototype.ToolThreadSelection.Private("account__scrm-contact:selected")
        val unrelated = (1..4_600).map { index -> "private:account__scrm-contact:friend-$index" }

        val preloadThreadIds = floatingChatPersistedMessageThreadIdsForSelection(selected)

        assertEquals(setOf("private:account__scrm-contact:selected"), preloadThreadIds)
        assertFalse(unrelated.any { threadId -> threadId in preloadThreadIds })
    }

    @Test
    fun scrmConversationRefreshQueuesAccountSwitchRequestedWhileRefreshIsRunning() {
        assertEquals(ScrmConversationRefreshGate.StartNow, scrmConversationRefreshGateDecision(inFlight = false))
        assertEquals(ScrmConversationRefreshGate.QueuePending, scrmConversationRefreshGateDecision(inFlight = true))
        assertEquals(260, scrmAccountSwitchRefreshDebounceMillis())
    }

    @Test
    fun recentlyLoadedAccountConversationSkipsImmediateRefresh() {
        assertTrue(scrmAccountConversationCacheIsFresh(10_000L, 20_000L, 15_000L))
        assertFalse(scrmAccountConversationCacheIsFresh(10_000L, 30_001L, 15_000L))
        assertFalse(scrmAccountConversationCacheIsFresh(null, 20_000L, 15_000L))
    }

    @Test
    fun staleConversationRefreshResultCannotReplaceCurrentAccount() {
        assertFalse(
            shouldApplyScrmConversationRefreshResult(
                requestGeneration = 1L,
                latestGeneration = 2L,
                requestedAccountId = "account-a",
                currentAccountId = "account-b"
            )
        )
        assertFalse(
            shouldApplyScrmConversationRefreshResult(
                requestGeneration = 2L,
                latestGeneration = 2L,
                requestedAccountId = "account-a",
                currentAccountId = "account-b"
            )
        )
        assertTrue(
            shouldApplyScrmConversationRefreshResult(
                requestGeneration = 2L,
                latestGeneration = 2L,
                requestedAccountId = "account-b",
                currentAccountId = "account-b"
            )
        )
    }

    @Test
    fun scrmAccountConversationCacheKeepsLoadedAccountsAndReplacesLatestRoute() {
        val accountOneOld = ScrmFloatingAccountConversation(
            deviceUuid = "device-1",
            weChatId = "wxid_1",
            contacts = listOf(ScrmContact(id = 1, ownerWxid = "wxid_1", wxid = "friend-old", nickname = "Old"))
        )
        val accountTwo = ScrmFloatingAccountConversation(
            deviceUuid = "device-2",
            weChatId = "wxid_2",
            contacts = listOf(ScrmContact(id = 2, ownerWxid = "wxid_2", wxid = "friend-2", nickname = "Two")),
            chatRooms = listOf(ScrmChatRoom(id = 2, ownerWxid = "wxid_2", chatRoomId = "room_2@chatroom"))
        )
        val accountOneLatest = ScrmFloatingAccountConversation(
            deviceUuid = "device-1",
            weChatId = "wxid_1",
            contacts = listOf(ScrmContact(id = 3, ownerWxid = "wxid_1", wxid = "friend-new", nickname = "New"))
        )

        val merged = mergeScrmAccountConversationCache(
            cachedConversations = listOf(accountOneOld, accountTwo),
            loadedConversations = listOf(accountOneLatest)
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { account -> account.weChatId == "wxid_2" && account.chatRooms.isNotEmpty() })
        assertEquals(
            listOf("friend-new"),
            merged.single { account -> account.weChatId == "wxid_1" }.contacts.mapNotNull { contact -> contact.wxid }
        )
    }

    /**
     * 测试流程：缓存中先放入已同步语音和一条本地消息，再接收同 messageId 的转写更新。
     * 新对象应原位替换旧语音，同时保留无服务端 ID 的本地消息。
     */
    @Test
    fun scrmReadOnlyMessageMergeReplacesMatchingRemoteMessageInPlace() {
        val cachedVoice = ScrmChatMessage(
            messageId = 71L,
            messageType = 34,
            content = "{\"text\":\"原始语音\"}"
        )
        val localMessage = ScrmChatMessage(
            clientMessageId = "local-message-1",
            content = "本地待发送消息"
        )
        val transcribedVoice = cachedVoice.copy(
            voiceText = Json.parseToJsonElement("{\"text\":\"转写结果\"}")
        )

        val merged = scrmMergeReadOnlyMessages(
            existing = listOf(cachedVoice, localMessage),
            incoming = transcribedVoice
        )

        assertEquals(listOf(transcribedVoice, localMessage), merged)
    }

    @Test
    fun scrmConversationContactQueryRequestsProfileFieldsForAvatars() {
        val query = scrmConversationContactQuery(
            weChatId = "wxid_1",
            pageNumber = 2
        )

        assertEquals("wxid_1", query.weChatId)
        assertEquals(2, query.page)
        assertEquals(200, query.pageSize)
        assertEquals(true, query.onlyFriends)
        assertEquals(true, query.includeProfile)
    }

    @Test
    fun scrmBackgroundPrefetchLoadsOneUncachedNonSelectedAccountAtATime() {
        val selected = ScrmFloatingAccountRoute("device-1", "wxid_1")
        val cached = ScrmFloatingAccountRoute("device-2", "wxid_2")
        val next = ScrmFloatingAccountRoute("device-3", "wxid_3")

        val routes = scrmBackgroundPrefetchRoutesToLoad(
            accounts = listOf(
                ScrmWechatAccount("wxid_1", "Account 1", "device-1"),
                ScrmWechatAccount("wxid_2", "Account 2", "device-2"),
                ScrmWechatAccount("wxid_3", "Account 3", "device-3"),
                ScrmWechatAccount("wxid_4", "Account 4", "device-4")
            ),
            devices = listOf(
                device(uuid = "device-1", weChatId = "wxid_1", online = true),
                device(uuid = "device-2", weChatId = "wxid_2", online = true),
                device(uuid = "device-3", weChatId = "wxid_3", online = true),
                device(uuid = "device-4", weChatId = "wxid_4", online = true)
            ),
            selectedRoute = selected,
            cachedRouteKeys = setOf(scrmAccountRouteCacheKey(cached)),
            maxRoutes = 1
        )

        assertEquals(listOf(next), routes)
        assertEquals(1, scrmBackgroundPrefetchMaxRoutesPerPass())
        assertEquals(700, scrmBackgroundPrefetchDelayMillis())
    }

    private fun device(
        uuid: String,
        weChatId: String,
        online: Boolean
    ): ScrmDevice {
        return ScrmDevice(
            uuid = uuid,
            isOnline = online,
            status = if (online) 1 else 0,
            weChatId = weChatId,
            androidApi = 35,
            appVersionCode = 1,
            updatedAt = "2026-07-14T00:00:00Z"
        )
    }
}
