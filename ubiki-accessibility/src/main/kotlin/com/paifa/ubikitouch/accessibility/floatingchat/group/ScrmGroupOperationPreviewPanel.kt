package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingDialogCloseButton
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomManagersRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomMembersByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomSwitchRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomTextRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmCreateChatRoomByFilterRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmJoinChatRoomByQrRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSendJielongRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountMutationRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmApproveChatRoomInviteRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAgreeChatRoomInviteRequest
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
    JoinByQr("二维码入群"),
    SendJielong("群接龙"),
    PullInvites("刷新入群邀请"),
    ApproveInvite("审批入群邀请"),
    AgreeInvite("同意自己入群邀请")
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
    var confirmed by remember(group.id) { mutableStateOf(false) }
    var settingEnabled by remember(group.id) { mutableStateOf(true) }
    var inviteMessageId by remember(group.id) { mutableStateOf("") }
    var inviteTalker by remember(group.id) { mutableStateOf("") }
    var filterSearch by remember(group.id) { mutableStateOf("") }
    var filterLabelIds by remember(group.id) { mutableStateOf("") }
    var filterLabelNames by remember(group.id) { mutableStateOf("") }
    var filterCustomerLevel by remember(group.id) { mutableStateOf("") }
    var filterSourceChannel by remember(group.id) { mutableStateOf("") }
    val estimatedAffectedCount = when (operation) {
        ScrmGroupPreviewOperation.CreateByFilter -> 2
        ScrmGroupPreviewOperation.InviteByFilter,
        ScrmGroupPreviewOperation.KickByFilter -> 1
        else -> if (targetMemberWxid.isBlank()) 0 else 1
    }

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
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFFF0F6EC), RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("仅 UI 预览", color = Color(0xFF4E7A55), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("写操作暂不发送，接入后将在此处显示确认、执行中和任务结果。", color = Color(0xFF6B766E), fontSize = 10.sp)
                }
                if (operation == null) {
                    GroupOperationSection("成员与群权限", listOf(ScrmGroupPreviewOperation.CreateByFilter, ScrmGroupPreviewOperation.InviteByFilter, ScrmGroupPreviewOperation.KickByFilter, ScrmGroupPreviewOperation.AddManagers, ScrmGroupPreviewOperation.RemoveManagers, ScrmGroupPreviewOperation.TransferOwner)) { operation = it; confirmed = false; status = null }
                    GroupOperationSection("群设置", listOf(ScrmGroupPreviewOperation.NewMessageNotify, ScrmGroupPreviewOperation.PinChat, ScrmGroupPreviewOperation.SaveToContacts, ScrmGroupPreviewOperation.SelfNickname, ScrmGroupPreviewOperation.JoinVerification, ScrmGroupPreviewOperation.SendJielong)) { operation = it; confirmed = false; status = null }
                    GroupOperationSection("入群", listOf(ScrmGroupPreviewOperation.JoinByQr, ScrmGroupPreviewOperation.PullInvites, ScrmGroupPreviewOperation.ApproveInvite, ScrmGroupPreviewOperation.AgreeInvite)) { operation = it; confirmed = false; status = null }
                } else {
                    if (operation in setOf(
                            ScrmGroupPreviewOperation.CreateByFilter,
                            ScrmGroupPreviewOperation.InviteByFilter,
                            ScrmGroupPreviewOperation.KickByFilter
                        )
                    ) {
                        Text("筛选条件", color = Color(0xFF30343A), fontSize = 12.sp)
                        Text("空字段不参与筛选，本次仅组装最多 $estimatedAffectedCount 个目标的请求预览。", color = Color(0xFF777E86), fontSize = 10.sp)
                        OutlinedTextField(filterSearch, { filterSearch = it }, modifier = Modifier.fillMaxWidth(), label = { Text("关键词（选填）") }, singleLine = true)
                        OutlinedTextField(filterLabelIds, { filterLabelIds = it }, modifier = Modifier.fillMaxWidth(), label = { Text("标签 ID（英文逗号分隔）") }, singleLine = true)
                        OutlinedTextField(filterLabelNames, { filterLabelNames = it }, modifier = Modifier.fillMaxWidth(), label = { Text("标签名称（英文逗号分隔）") }, singleLine = true)
                        OutlinedTextField(filterCustomerLevel, { filterCustomerLevel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("客户等级（选填）") }, singleLine = true)
                        OutlinedTextField(filterSourceChannel, { filterSourceChannel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("来源渠道（选填）") }, singleLine = true)
                    }
                    if (operation in setOf(ScrmGroupPreviewOperation.NewMessageNotify, ScrmGroupPreviewOperation.PinChat, ScrmGroupPreviewOperation.SaveToContacts, ScrmGroupPreviewOperation.JoinVerification)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("目标设置", color = Color(0xFF30343A), fontSize = 12.sp)
                                Text(if (settingEnabled) "开启此群设置" else "关闭此群设置", color = Color(0xFF777E86), fontSize = 10.sp)
                            }
                            Switch(checked = settingEnabled, onCheckedChange = { settingEnabled = it })
                        }
                    }
                    if (operation in setOf(
                            ScrmGroupPreviewOperation.AddManagers,
                            ScrmGroupPreviewOperation.RemoveManagers,
                            ScrmGroupPreviewOperation.TransferOwner
                        )
                    ) {
                        OutlinedTextField(targetMemberWxid, { targetMemberWxid = it }, modifier = Modifier.fillMaxWidth(), label = { Text("成员 wxid") }, singleLine = true)
                    }
                    if (operation in setOf(ScrmGroupPreviewOperation.ApproveInvite, ScrmGroupPreviewOperation.AgreeInvite)) {
                        OutlinedTextField(inviteMessageId, { inviteMessageId = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("邀请消息服务端 ID") }, singleLine = true)
                        if (operation == ScrmGroupPreviewOperation.AgreeInvite) {
                            OutlinedTextField(inviteTalker, { inviteTalker = it }, modifier = Modifier.fillMaxWidth(), label = { Text("邀请来源 talker") }, singleLine = true)
                        }
                        OutlinedTextField(inputText, { inputText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("邀请消息内容") }, minLines = 2)
                    }
                    if (operation in setOf(
                            ScrmGroupPreviewOperation.SelfNickname,
                            ScrmGroupPreviewOperation.JoinByQr,
                            ScrmGroupPreviewOperation.SendJielong
                        )
                    ) {
                        OutlinedTextField(inputText, { inputText = it }, modifier = Modifier.fillMaxWidth(), label = { Text(when (operation) { ScrmGroupPreviewOperation.JoinByQr -> "二维码内容"; ScrmGroupPreviewOperation.SendJielong -> "接龙内容"; else -> "群内昵称" }) }, singleLine = operation != ScrmGroupPreviewOperation.SendJielong, minLines = if (operation == ScrmGroupPreviewOperation.SendJielong) 2 else 1)
                    }
                    Text("该操作会改变群状态或成员关系。请求仅组装，不发送。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("我已确认目标群和影响范围", fontSize = 11.sp, color = Color(0xFF5F666D))
                    }
                    status?.let { Text(it) }
                }
            }
        },
        confirmButton = {
            if (operation == null) TextButton(onClick = onDismiss) { Text("关闭") }
            else Button(enabled = confirmed, onClick = {
                status = prepareGroupOperationRequest(
                    operation = operation ?: return@Button,
                    deviceUuid = route?.deviceUuid,
                    weChatId = route?.weChatId,
                    chatRoomId = chatRoomId,
                    memberWxid = targetMemberWxid,
                    inviteTalker = inviteTalker,
                    text = inputText,
                    enabled = settingEnabled,
                    inviteMessageId = inviteMessageId.toLongOrNull(),
                    filterSearch = filterSearch,
                    filterLabelIds = filterLabelIds,
                    filterLabelNames = filterLabelNames,
                    filterCustomerLevel = filterCustomerLevel,
                    filterSourceChannel = filterSourceChannel
                )
            }) { Text("组装请求") }
        },
        dismissButton = { TextButton(onClick = { if (operation == null) onDismiss() else { operation = null; confirmed = false } }) { Text(if (operation == null) "取消" else "返回") } }
    )
}

@Composable
private fun GroupOperationSection(
    title: String,
    operations: List<ScrmGroupPreviewOperation>,
    onSelect: (ScrmGroupPreviewOperation) -> Unit
) {
    Text(title, color = Color(0xFF858C94), fontSize = 11.sp)
    operations.forEach { item ->
        TextButton(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth()) {
            Text(item.title, modifier = Modifier.weight(1f), color = Color(0xFF30343A))
            Text("预览", color = Color(0xFFB26A00), fontSize = 10.sp)
        }
    }
}

private fun prepareGroupOperationRequest(
    operation: ScrmGroupPreviewOperation,
    deviceUuid: String?,
    weChatId: String?,
    chatRoomId: String?,
    memberWxid: String,
    inviteTalker: String,
    text: String,
    enabled: Boolean,
    inviteMessageId: Long?,
    filterSearch: String,
    filterLabelIds: String,
    filterLabelNames: String,
    filterCustomerLevel: String,
    filterSourceChannel: String
): String {
    if (deviceUuid.isNullOrBlank() || weChatId.isNullOrBlank()) return "无法组装：缺少当前账号 SCRM 路由"
    if (operation != ScrmGroupPreviewOperation.CreateByFilter && operation != ScrmGroupPreviewOperation.JoinByQr && operation != ScrmGroupPreviewOperation.PullInvites && chatRoomId.isNullOrBlank()) return "无法组装：缺少群 ID"
    val roomId = chatRoomId.orEmpty()
    return when (operation) {
        ScrmGroupPreviewOperation.CreateByFilter -> {
            val filter = parseGroupMemberFilter(filterLabelIds, filterLabelNames)
                ?: return "无法组装：标签 ID 必须为正整数，使用英文逗号分隔"
            ScrmCreateChatRoomByFilterRequest(
                deviceUuid,
                weChatId,
                search = filterSearch.trim().ifBlank { null },
                filterLabelIds = filter.labelIds,
                filterLabelNames = filter.labelNames,
                customerLevel = filterCustomerLevel.trim().ifBlank { null },
                sourceChannel = filterSourceChannel.trim().ifBlank { null },
                maxCount = 2
            )
            "建群请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.InviteByFilter -> {
            val filter = parseGroupMemberFilter(filterLabelIds, filterLabelNames)
                ?: return "无法组装：标签 ID 必须为正整数，使用英文逗号分隔"
            ScrmChatRoomMembersByFilterRequest(
                deviceUuid,
                weChatId,
                roomId,
                search = filterSearch.trim().ifBlank { null },
                filterLabelIds = filter.labelIds,
                filterLabelNames = filter.labelNames,
                customerLevel = filterCustomerLevel.trim().ifBlank { null },
                sourceChannel = filterSourceChannel.trim().ifBlank { null },
                skipExistingMembers = true,
                maxCount = 1
            )
            "筛选邀请请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.KickByFilter -> {
            val filter = parseGroupMemberFilter(filterLabelIds, filterLabelNames)
                ?: return "无法组装：标签 ID 必须为正整数，使用英文逗号分隔"
            ScrmChatRoomMembersByFilterRequest(
                deviceUuid,
                weChatId,
                roomId,
                search = filterSearch.trim().ifBlank { null },
                filterLabelIds = filter.labelIds,
                filterLabelNames = filter.labelNames,
                customerLevel = filterCustomerLevel.trim().ifBlank { null },
                sourceChannel = filterSourceChannel.trim().ifBlank { null },
                onlyExistingMembers = true,
                maxCount = 1
            )
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
            ScrmChatRoomSwitchRequest(deviceUuid, weChatId, roomId, enabled = enabled)
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
        ScrmGroupPreviewOperation.SendJielong -> {
            if (text.isBlank()) return "无法组装：请输入接龙内容"
            ScrmSendJielongRequest(deviceUuid, weChatId, roomId, content = text)
            "群接龙请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.PullInvites -> {
            ScrmAccountMutationRequest(deviceUuid, weChatId)
            "入群邀请刷新请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.ApproveInvite -> {
            if (inviteMessageId == null || inviteMessageId <= 0L) return "无法组装：请输入有效邀请消息 ID"
            if (text.isBlank()) return "无法组装：请输入邀请消息内容"
            ScrmApproveChatRoomInviteRequest(deviceUuid, weChatId, inviteMessageId, roomId = roomId, msgContent = text)
            "入群邀请审批请求已组装，未发送"
        }
        ScrmGroupPreviewOperation.AgreeInvite -> {
            if (inviteMessageId == null || inviteMessageId <= 0L) return "无法组装：请输入有效邀请消息 ID"
            if (inviteTalker.isBlank()) return "无法组装：请输入邀请来源 talker"
            if (text.isBlank()) return "无法组装：请输入邀请消息内容"
            ScrmAgreeChatRoomInviteRequest(deviceUuid, weChatId, inviteTalker, inviteMessageId, text)
            "同意入群邀请请求已组装，未发送"
        }
    }
}

internal data class GroupMemberFilter(
    val labelIds: List<Int>,
    val labelNames: List<String>
)

internal fun parseGroupMemberFilter(labelIdsText: String, labelNamesText: String): GroupMemberFilter? {
    val labelIdTokens = labelIdsText.split(',').map(String::trim).filter(String::isNotEmpty)
    val labelIds = labelIdTokens.map { it.toIntOrNull() ?: return null }
    if (labelIds.any { it <= 0 }) return null
    return GroupMemberFilter(
        labelIds = labelIds,
        labelNames = labelNamesText.split(',').map(String::trim).filter(String::isNotEmpty)
    )
}
