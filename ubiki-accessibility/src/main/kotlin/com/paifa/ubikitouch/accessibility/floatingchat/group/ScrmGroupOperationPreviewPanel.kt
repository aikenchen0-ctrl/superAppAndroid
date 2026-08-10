package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingDialogCloseButton
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomManagersRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomMembersByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomSwitchRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomTextRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmCreateChatRoomByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmJoinChatRoomByQrRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmTransferChatRoomOwnerRequest
import com.paifa.ubikitouch.accessibility.scrm.scrmContactsPanelRouteForSelectedAccount
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.ubikitouch.core.model.FloatingChatContact

private enum class ScrmGroupPreviewOperation(val title: String) {
    CreateByFilter("按筛选建群"),
    InviteByFilter("按筛选邀请"),
    KickByFilter("按筛选踢出"),
    AddManagers("添加管理员"),
    RemoveManagers("移除管理员"),
    TransferOwner("转让群主"),
    NewMessageNotify("新消息通知"),
    PinChat("群聊置顶"),
    SaveToContacts("保存到通讯录"),
    SelfNickname("群内昵称"),
    JoinVerification("入群验证"),
    JoinByQr("二维码入群")
}

/**
 * UI 对接：群资料页 -> 群管理操作。
 * 人工测试：先核对群 ID、预计影响人数和成员 wxid，点击“组装请求”后仅验证状态文案。
 * 禁止在本组件内调用 chatRoomManagementApi 的写方法。
 */
@Composable
internal fun ScrmGroupOperationPreviewPanel(
    accountId: String,
    group: FloatingChatContact,
    members: List<FloatingChatContact>,
    onDismiss: () -> Unit
) {
    val route = remember(accountId) {
        scrmContactsPanelRouteForSelectedAccount(accountId, null, null)
    }
    val chatRoomId = remember(group.id) { scrmFloatingContactConversationId(group.id) }
    var operation by remember(group.id) { mutableStateOf<ScrmGroupPreviewOperation?>(null) }
    var targetMemberWxid by remember(group.id) { mutableStateOf("") }
    var inputText by remember(group.id) { mutableStateOf("") }
    var status by remember(group.id) { mutableStateOf<String?>(null) }
    val estimatedAffectedCount = if (targetMemberWxid.isBlank()) 0 else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(operation?.title ?: "群管理操作", modifier = Modifier.weight(1f))
                FloatingDialogCloseButton(onClose = onDismiss)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标群：${group.name}")
                Text("群 ID：${chatRoomId ?: "缺少 SCRM 群路由"}")
                Text("预计影响：$estimatedAffectedCount 人")
                if (operation == null) {
                    ScrmGroupPreviewOperation.values().forEach { item ->
                        TextButton(onClick = { operation = item }, modifier = Modifier.fillMaxWidth()) { Text(item.title) }
                    }
                } else {
                    if (operation in setOf(
                            ScrmGroupPreviewOperation.AddManagers,
                            ScrmGroupPreviewOperation.RemoveManagers,
                            ScrmGroupPreviewOperation.TransferOwner
                        )
                    ) {
                        OutlinedTextField(targetMemberWxid, { targetMemberWxid = it }, modifier = Modifier.fillMaxWidth(), label = { Text("成员 wxid") }, singleLine = true)
                    }
                    if (operation in setOf(
                            ScrmGroupPreviewOperation.SelfNickname,
                            ScrmGroupPreviewOperation.JoinByQr
                        )
                    ) {
                        OutlinedTextField(inputText, { inputText = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (operation == ScrmGroupPreviewOperation.JoinByQr) "二维码内容" else "群内昵称") }, singleLine = true)
                    }
                    Text("该操作会改变群状态或成员关系。请求仅组装，不发送。")
                    status?.let { Text(it) }
                }
            }
        },
        confirmButton = {
            if (operation == null) TextButton(onClick = onDismiss) { Text("关闭") }
            else Button(onClick = {
                status = prepareGroupOperationRequest(
                    operation = operation ?: return@Button,
                    deviceUuid = route?.deviceUuid,
                    weChatId = route?.weChatId,
                    chatRoomId = chatRoomId,
                    memberWxid = targetMemberWxid,
                    text = inputText
                )
            }) { Text("组装请求") }
        },
        dismissButton = { TextButton(onClick = { if (operation == null) onDismiss() else operation = null }) { Text(if (operation == null) "取消" else "返回") } }
    )
}

private fun prepareGroupOperationRequest(
    operation: ScrmGroupPreviewOperation,
    deviceUuid: String?,
    weChatId: String?,
    chatRoomId: String?,
    memberWxid: String,
    text: String
): String {
    if (deviceUuid.isNullOrBlank() || weChatId.isNullOrBlank()) return "无法组装：缺少当前账号 SCRM 路由"
    if (operation != ScrmGroupPreviewOperation.CreateByFilter && operation != ScrmGroupPreviewOperation.JoinByQr && chatRoomId.isNullOrBlank()) return "无法组装：缺少群 ID"
    val roomId = chatRoomId.orEmpty()
    return when (operation) {
        ScrmGroupPreviewOperation.CreateByFilter -> {
            ScrmCreateChatRoomByFilterRequest(deviceUuid, weChatId, maxCount = 2)
            "建群请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.InviteByFilter -> {
            ScrmChatRoomMembersByFilterRequest(deviceUuid, weChatId, roomId, skipExistingMembers = true, maxCount = 1)
            "筛选邀请请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.KickByFilter -> {
            ScrmChatRoomMembersByFilterRequest(deviceUuid, weChatId, roomId, onlyExistingMembers = true, maxCount = 1)
            "筛选踢出请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.AddManagers,
        ScrmGroupPreviewOperation.RemoveManagers -> {
            if (memberWxid.isBlank()) return "无法组装：请输入成员 wxid"
            ScrmChatRoomManagersRequest(deviceUuid, weChatId, roomId, listOf(memberWxid))
            "管理员请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.TransferOwner -> {
            if (memberWxid.isBlank()) return "无法组装：请输入新群主 wxid"
            ScrmTransferChatRoomOwnerRequest(deviceUuid, weChatId, roomId, memberWxid)
            "转让群主请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.NewMessageNotify,
        ScrmGroupPreviewOperation.PinChat,
        ScrmGroupPreviewOperation.SaveToContacts,
        ScrmGroupPreviewOperation.JoinVerification -> {
            ScrmChatRoomSwitchRequest(deviceUuid, weChatId, roomId, enabled = true)
            "群设置请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.SelfNickname -> {
            if (text.isBlank()) return "无法组装：请输入群内昵称"
            ScrmChatRoomTextRequest(deviceUuid, weChatId, roomId, text)
            "群内昵称请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.JoinByQr -> {
            if (text.isBlank()) return "无法组装：请输入二维码内容"
            ScrmJoinChatRoomByQrRequest(deviceUuid, weChatId, qrContent = text)
            "二维码入群请求已组装，未发送"
        }
    }
}
