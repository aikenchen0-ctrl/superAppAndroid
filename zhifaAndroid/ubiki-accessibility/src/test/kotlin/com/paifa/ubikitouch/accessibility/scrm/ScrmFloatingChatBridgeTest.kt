package com.paifa.ubikitouch.accessibility.scrm

import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScrmFloatingChatBridgeTest {
    @Test
    fun apiContactsAndWechatAccountsReplacePrototypeConversationLists() {
        val selectedAccountId = scrmFloatingAccountId(
            deviceUuid = "device-1",
            weChatId = "wxid_account_1"
        )
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = listOf(
                ScrmContact(
                    id = 1,
                    ownerWxid = "wxid_account_1",
                    wxid = "wxid_friend_1",
                    nickname = "Alice",
                    remarks = "VIP Alice",
                    avatar = "https://cdn.example.com/alice.png",
                    isFriend = 1
                ),
                ScrmContact(
                    id = 2,
                    ownerWxid = "wxid_account_1",
                    wxid = "wxid_friend_2",
                    nickname = "Bob",
                    isFriend = 1
                )
            ),
            accounts = listOf(
                ScrmWechatAccount(
                    wxid = "wxid_account_1",
                    nickname = "Main WeChat",
                    clientUuid = "device-1",
                    accountStatus = 1
                ),
                ScrmWechatAccount(
                    wxid = "wxid_account_2",
                    nickname = "Backup WeChat",
                    clientUuid = "device-2",
                    accountStatus = 1
                )
            ),
            devices = listOf(
                device(uuid = "device-1", weChatId = "wxid_account_1", online = true),
                device(uuid = "device-2", weChatId = "wxid_account_2", online = false)
            ),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(
            listOf(
                scrmFloatingScopedThreadId(selectedAccountId, scrmFloatingContactId("wxid_friend_1")),
                scrmFloatingScopedThreadId(selectedAccountId, scrmFloatingContactId("wxid_friend_2"))
            ),
            conversation.contacts.map { contact -> contact.id }
        )
        assertEquals(listOf("VIP Alice", "Bob"), conversation.contacts.map { contact -> contact.name })
        assertEquals("https://cdn.example.com/alice.png", conversation.contacts.first().avatarUrl)
        assertFalse(conversation.contacts.any { contact -> contact.id == "li-si" })
        assertEquals(emptyList<Any>(), conversation.groupContacts)
        assertEquals(emptyList<Any>(), conversation.messages)

        val accountIds = conversation.accountContacts.map { account -> account.id }
        assertEquals(
            listOf(
                scrmFloatingAccountId(deviceUuid = "device-1", weChatId = "wxid_account_1"),
                scrmFloatingAccountId(deviceUuid = "device-2", weChatId = "wxid_account_2")
            ),
            accountIds
        )
        assertEquals(listOf("Main WeChat", "Backup WeChat"), conversation.accountContacts.map { account -> account.name })
        assertEquals(true, conversation.accountContacts.first().selected)
        assertEquals(false, conversation.accountContacts[1].selected)

        assertEquals(
            ScrmFloatingAccountRoute(deviceUuid = "device-2", weChatId = "wxid_account_2"),
            scrmFloatingAccountRouteForContactId(conversation.accountContacts[1].id)
        )
        assertEquals(
            "wxid_friend_1",
            scrmFloatingContactConversationId(conversation.contacts.first().id)
        )
    }

    @Test
    fun accountConversationDataKeepsAllContactsAndChatroomsScopedToEachAccount() {
        val accountOne = scrmFloatingAccountId("device-1", "wxid_account_1")
        val accountTwo = scrmFloatingAccountId("device-2", "wxid_account_2")
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = (1..8).map { index ->
                        ScrmContact(
                            id = index,
                            ownerWxid = "wxid_account_1",
                            wxid = "wxid_a_$index",
                            nickname = "A$index",
                            isFriend = 1
                        )
                    },
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_a@chatroom",
                            name = "Account A Room",
                            avatar = "http://mmbiz.qpic.cn/room-a.png",
                            memberCount = 12
                        )
                    ),
                    chatRoomMembers = mapOf(
                        "room_a@chatroom" to (1..10).map { index ->
                            ScrmChatRoomMember(
                                id = index,
                                chatRoomId = "room_a@chatroom",
                                memberWxid = if (index == 1) "wxid_account_1" else "wxid_member_$index",
                                displayName = "Member $index",
                                avatar = "http://mmbiz.qpic.cn/member-$index.png"
                            )
                        }
                    )
                ),
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-2",
                    weChatId = "wxid_account_2",
                    contacts = (1..3).map { index ->
                        ScrmContact(
                            id = index + 100,
                            ownerWxid = "wxid_account_2",
                            wxid = "wxid_b_$index",
                            nickname = "B$index",
                            isFriend = 1
                        )
                    }
                )
            ),
            accounts = listOf(
                ScrmWechatAccount("wxid_account_1", "Account 1", "device-1", accountStatus = 1),
                ScrmWechatAccount("wxid_account_2", "Account 2", "device-2", accountStatus = 1)
            ),
            devices = listOf(
                device("device-1", "wxid_account_1", online = true),
                device("device-2", "wxid_account_2", online = true)
            ),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(11, conversation.contacts.size)
        assertEquals(1, conversation.groupContacts.size)
        assertEquals(8, conversation.contacts.count { contact -> contact.id.startsWith("${accountOne}__") })
        assertEquals(3, conversation.contacts.count { contact -> contact.id.startsWith("${accountTwo}__") })
        assertEquals(
            scrmFloatingScopedThreadId(accountOne, scrmFloatingGroupId("room_a@chatroom")),
            conversation.groupContacts.single().id
        )
        assertEquals("https://mmbiz.qpic.cn/room-a.png", conversation.groupContacts.single().avatarUrl)
        assertEquals(
            (1..9).map { index -> "https://mmbiz.qpic.cn/member-$index.png" },
            conversation.groupContacts.single().groupMemberAvatarUrls
        )
        assertEquals("https://mmbiz.qpic.cn/member-1.png", conversation.accountContacts.first().avatarUrl)
        assertEquals("room_a@chatroom", scrmFloatingContactConversationId(conversation.groupContacts.single().id))
    }

    @Test
    fun wechatAccountAvatarFieldOverridesDerivedMemberAvatarWhenPresent() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = listOf(
                        ScrmContact(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            wxid = "wxid_account_1",
                            nickname = "Self",
                            avatar = "http://mmbiz.qpic.cn/derived.png"
                        )
                    )
                )
            ),
            accounts = listOf(
                ScrmWechatAccount(
                    wxid = "wxid_account_1",
                    nickname = "Account 1",
                    clientUuid = "device-1",
                    avatar = "http://mmbiz.qpic.cn/account.png",
                    accountStatus = 1
                )
            ),
            devices = listOf(device("device-1", "wxid_account_1", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals("https://mmbiz.qpic.cn/account.png", conversation.accountContacts.single().avatarUrl)
    }

    @Test
    fun duplicateRemoteContactsAndRoomsAreMergedBeforeRenderingRails() {
        val accountId = scrmFloatingAccountId("device-1", "wxid_account_1")
        val contactId = scrmFloatingScopedThreadId(accountId, scrmFloatingContactId("qq-13462583081"))
        val groupId = scrmFloatingScopedThreadId(accountId, scrmFloatingGroupId("room_1@chatroom"))
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = listOf(
                        ScrmContact(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            friendNo = "qq-13462583081",
                            nickname = "No Avatar",
                            isFriend = 1
                        ),
                        ScrmContact(
                            id = 2,
                            ownerWxid = "wxid_account_1",
                            friendNo = "qq-13462583081",
                            nickname = "Has Avatar",
                            avatar = "http://mmbiz.qpic.cn/contact.png",
                            isFriend = 1
                        )
                    ),
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_1@chatroom",
                            name = "Room",
                            memberCount = 9
                        ),
                        ScrmChatRoom(
                            id = 2,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_1@chatroom",
                            name = "Room With Avatar",
                            avatar = "http://mmbiz.qpic.cn/room.png",
                            memberCount = 9
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account_1", "Account 1", "device-1", accountStatus = 1)),
            devices = listOf(device("device-1", "wxid_account_1", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(listOf(contactId), conversation.contacts.map { contact -> contact.id })
        assertEquals("https://mmbiz.qpic.cn/contact.png", conversation.contacts.single().avatarUrl)
        assertEquals(listOf(groupId), conversation.groupContacts.map { group -> group.id })
        assertEquals("https://mmbiz.qpic.cn/room.png", conversation.groupContacts.single().avatarUrl)
    }

    @Test
    fun selectedSessionAccountIsIncludedWhenAccountListDoesNotContainIt() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accounts = emptyList(),
            devices = emptyList(),
            selectedDeviceUuid = "device-selected",
            selectedWeChatId = "wxid_selected"
        )

        val onlyAccount = conversation.accountContacts.single()
        assertEquals(
            scrmFloatingAccountId(deviceUuid = "device-selected", weChatId = "wxid_selected"),
            onlyAccount.id
        )
        assertEquals(true, onlyAccount.selected)
        assertNotNull(scrmFloatingAccountRouteForContactId(onlyAccount.id))
    }

    @Test
    fun selectedFloatingAccountRouteOverridesSessionFallbackForRefresh() {
        val selectedAccountId = scrmFloatingAccountId(
            deviceUuid = "device-2",
            weChatId = "wxid_account_2"
        )

        val route = scrmFloatingAccountRouteForSelection(
            selectedAccountId = selectedAccountId,
            fallbackDeviceUuid = "device-1",
            fallbackWeChatId = "wxid_account_1"
        )

        assertEquals(ScrmFloatingAccountRoute("device-2", "wxid_account_2"), route)
    }

    @Test
    fun invalidFloatingAccountRouteFallsBackToSessionRouteForRefresh() {
        val route = scrmFloatingAccountRouteForSelection(
            selectedAccountId = "account-main",
            fallbackDeviceUuid = "device-1",
            fallbackWeChatId = "wxid_account_1"
        )

        assertEquals(ScrmFloatingAccountRoute("device-1", "wxid_account_1"), route)
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
            updatedAt = "2026-07-13T00:00:00Z"
        )
    }
}
