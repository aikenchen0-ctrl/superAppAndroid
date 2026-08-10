package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import android.content.Context
import android.widget.Toast
import com.paifa.ubikitouch.accessibility.floatingchat.chat.accountIdForScopedThreadId
import com.paifa.ubikitouch.accessibility.scrm.ScrmAddFriendRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactTaskRunner
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.scrmContactsPanelRouteForSelectedAccount
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.accessibility.scrm.toScrmContactsPanelMessage
import com.paifa.ubikitouch.core.model.FloatingChatContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ContactRemoteTaskActions(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val scrmProfileManager: ScrmSettingsManager,
    private val selectedAccountId: () -> String,
    private val onContactEditorClosed: () -> Unit,
    private val onGroupMemberAddFriendStateChanged: (GroupMemberAddFriendTaskState) -> Unit
) {
    fun deleteFriendFromProfile(contact: FloatingChatContact) {
        val friendId = scrmFloatingContactConversationId(contact.id)
        if (friendId == null) {
            Toast.makeText(context, "当前联系人缺少可删除的 wxid", Toast.LENGTH_SHORT).show()
            return
        }
        val accountId = accountIdForScopedThreadId(contact.id) ?: selectedAccountId()
        val route = scrmContactsPanelRouteForSelectedAccount(
            selectedAccountId = accountId,
            fallbackDeviceUuid = null,
            fallbackWeChatId = null
        )
        if (route == null) {
            Toast.makeText(context, "当前账号缺少 SCRM 路由，无法删除好友", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            Toast.makeText(context, "正在提交删除好友", Toast.LENGTH_SHORT).show()
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = scrmProfileManager.loadSelectedSessionOrBootstrap()
                    ScrmContactTaskRunner(session.taskApi).submitAndAwait(
                        reloadContactsOnSuccess = true
                    ) {
                        session.contactApi.deleteFriend(
                            friendId = friendId,
                            deviceUuid = route.deviceUuid,
                            weChatId = route.weChatId
                        )
                    }
                }
            }.onSuccess { outcome ->
                Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                onContactEditorClosed()
            }.onFailure { error ->
                Toast.makeText(context, error.toScrmContactsPanelMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addFriendFromGroupMember(member: FloatingChatContact) {
        val friendId = scrmFloatingContactConversationId(member.id)
        if (friendId == null) {
            onGroupMemberAddFriendStateChanged(
                GroupMemberAddFriendTaskState(
                    targetId = member.id,
                    loading = false,
                    status = null,
                    error = "当前群成员缺少可添加的 wxid"
                )
            )
            Toast.makeText(context, "当前群成员缺少可添加的 wxid", Toast.LENGTH_SHORT).show()
            return
        }
        val accountId = accountIdForScopedThreadId(member.id) ?: selectedAccountId()
        val route = scrmContactsPanelRouteForSelectedAccount(
            selectedAccountId = accountId,
            fallbackDeviceUuid = null,
            fallbackWeChatId = null
        )
        if (route == null) {
            val message = "当前账号缺少 SCRM 路由，无法添加好友"
            onGroupMemberAddFriendStateChanged(
                GroupMemberAddFriendTaskState(
                    targetId = member.id,
                    loading = false,
                    status = null,
                    error = message
                )
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            onGroupMemberAddFriendStateChanged(
                GroupMemberAddFriendTaskState(
                    targetId = member.id,
                    loading = true,
                    status = "正在发送好友申请",
                    error = null
                )
            )
            Toast.makeText(context, "正在发送好友申请", Toast.LENGTH_SHORT).show()
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = scrmProfileManager.loadSelectedSessionOrBootstrap()
                    ScrmContactTaskRunner(session.taskApi).submitAndAwait(
                        reloadContactsOnSuccess = true
                    ) {
                        session.contactApi.addFriend(
                            ScrmAddFriendRequest(
                                deviceUuid = route.deviceUuid,
                                weChatId = route.weChatId,
                                friendWxid = friendId,
                                message = "你好，我是通过群聊添加你的"
                            )
                        )
                    }
                }
            }.onSuccess { outcome ->
                val status = friendApplySubmittedStatus(outcome.message)
                onGroupMemberAddFriendStateChanged(
                    GroupMemberAddFriendTaskState(
                        targetId = member.id,
                        loading = false,
                        status = status,
                        error = null
                    )
                )
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                val message = error.toScrmContactsPanelMessage()
                onGroupMemberAddFriendStateChanged(
                    GroupMemberAddFriendTaskState(
                        targetId = member.id,
                        loading = false,
                        status = null,
                        error = message
                    )
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal data class GroupMemberAddFriendTaskState(
    val targetId: String,
    val loading: Boolean,
    val status: String?,
    val error: String?
)
