package com.paifa.ubikitouch.accessibility.floatingchat.scrm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountOption
import com.paifa.ubikitouch.accessibility.scrm.ScrmCapabilities
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactTaskRunner
import com.paifa.ubikitouch.accessibility.scrm.ScrmDevice
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsSummary
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncChatRoomsRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncContactsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AccountDeviceSnapshot(
    val summary: ScrmSettingsSummary,
    val devices: List<ScrmDevice>,
    val accounts: List<ScrmAccountOption>,
    val capabilities: ScrmCapabilities?
)

private enum class AccountDeviceAction(
    val title: String,
    val subtitle: String
) {
    Refresh("刷新帐号与设备状态", "更新在线状态、设备和能力"),
    SyncContacts("同步好友资料", "从当前微信同步好友和头像"),
    SyncChatRooms("同步群聊资料", "从当前微信同步群聊和成员"),
    StartLogin("登录新的微信帐号", "创建扫码登录会话，有效期 5 分钟"),
    ActiveLogin("查看正在登录的帐号", "查看登录二维码是否仍然有效"),
    RestartWeChat("重启设备上的微信", "微信异常时使用，聊天记录不会删除")
}

@Composable
internal fun AccountDevicePanel(
    manager: ScrmSettingsManager,
    onClose: () -> Unit
) {
    var snapshot by remember { mutableStateOf<AccountDeviceSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshVersion by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        status = null
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val session = manager.loadSelectedSessionOrBootstrap()
                val devices = session.readApi.getDevices()
                val accounts = session.readApi.getWechatAccounts().mapNotNull { account ->
                    val weChatId = account.wxid?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val deviceUuid = account.clientUuid?.takeIf(String::isNotBlank)
                    ScrmAccountOption(
                        weChatId = weChatId,
                        nickname = account.nickname.orEmpty().ifBlank { "未命名账号" },
                        deviceUuid = deviceUuid,
                        isDeviceOnline = devices.any { it.uuid == deviceUuid && it.isOnline },
                        accountStatus = account.accountStatus
                    )
                }
                AccountDeviceSnapshot(
                    summary = manager.loadSummary(),
                    devices = devices,
                    accounts = accounts,
                    capabilities = session.readApi.getCapabilities(session.deviceUuid, session.weChatId)
                )
            }
        }
        result.onSuccess { snapshot = it }.onFailure { error ->
            status = error.message ?: "账号设备数据加载失败"
        }
        loading = false
    }

    fun runAction(action: AccountDeviceAction) {
        when (action) {
            AccountDeviceAction.Refresh -> refreshVersion += 1
            AccountDeviceAction.StartLogin,
            AccountDeviceAction.ActiveLogin,
            AccountDeviceAction.RestartWeChat -> {
                status = "当前 Android SCRM 接口契约未提供“${action.title}”操作"
            }
            AccountDeviceAction.SyncContacts,
            AccountDeviceAction.SyncChatRooms -> coroutineScope.launch {
                loading = true
                status = "正在${action.title}"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val session = manager.loadSelectedSessionOrBootstrap()
                        val submitted = when (action) {
                            AccountDeviceAction.SyncContacts -> session.contactApi.syncContacts(
                                ScrmSyncContactsRequest(session.deviceUuid, session.weChatId)
                            )
                            AccountDeviceAction.SyncChatRooms -> session.chatRoomApi.syncChatRooms(
                                ScrmSyncChatRoomsRequest(session.deviceUuid, session.weChatId)
                            )
                            else -> error("Unsupported account device action")
                        }
                        ScrmContactTaskRunner(session.taskApi).submitAndAwait(
                            reloadContactsOnSuccess = false
                        ) { submitted }
                    }
                }.onSuccess { outcome ->
                    status = if (outcome.completed) {
                        "${action.title}完成：${outcome.message}"
                    } else {
                        "${action.title}已提交：${outcome.message}"
                    }
                    refreshVersion += 1
                }.onFailure { error ->
                    status = error.message ?: "${action.title}失败"
                }
                loading = false
            }
        }
    }

    LaunchedEffect(refreshVersion) { refresh() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhoneIphone, contentDescription = null, tint = OverlayTokens.accent)
            Spacer(Modifier.width(8.dp))
            TextLabel("帐号设备", 17.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refreshVersion += 1 }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新帐号与设备", tint = OverlayTokens.toolIcon)
            }
        }
        TextLabel(if (loading) "正在读取当前状态" else "当前帐号、设备和能力状态", 12.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        status?.let { message ->
            TextLabel(message, 12.sp, color = OverlayTokens.alertCore, maxLines = 3, modifier = Modifier.padding(top = 8.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { AccountDeviceSectionTitle("当前状态") }
            item { AccountDeviceOverview(snapshot) }
            item { AccountDeviceSectionTitle("帐号操作") }
            AccountDeviceAction.entries.forEach { action ->
                item(key = action.name) {
                    AccountDeviceActionRow(action = action, enabled = !loading, onClick = { runAction(action) })
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onClose) {
                TextLabel("关闭", 13.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AccountDeviceOverview(snapshot: AccountDeviceSnapshot?) {
    val summary = snapshot?.summary
    val selectedAccount = snapshot?.accounts?.firstOrNull {
        it.deviceUuid == summary?.selectedDeviceUuid && it.weChatId == summary?.selectedWeChatId
    }
    val selectedDevice = snapshot?.devices?.firstOrNull { it.uuid == summary?.selectedDeviceUuid }
    AccountDeviceCard {
        AccountDeviceField("当前帐号", selectedAccount?.nickname ?: "等待同步微信帐号", summary?.selectedWeChatId.orEmpty())
        AccountDeviceField("当前设备", selectedDevice?.phoneModel.orEmpty().ifBlank { "等待同步设备" }, summary?.selectedDeviceUuid.orEmpty())
        AccountDeviceField("身份鉴权", if (summary?.isConfigured == true) "正常" else "异常", if (summary?.isConfigured == true) "SCRM 身份已配置" else "请先配置 SCRM 服务")
        val capabilityValue = snapshot?.capabilities?.let { "可用 ${it.readyCount}，阻塞 ${it.blockedCount}" } ?: "不可用"
        AccountDeviceField("功能能力", capabilityValue, "点击下方操作可重新检查")
    }
}

@Composable
private fun AccountDeviceField(label: String, value: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        TextLabel(label, 11.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
        TextLabel(value, 13.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.SemiBold, maxLines = 1)
        if (subtitle.isNotBlank()) TextLabel(subtitle, 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 1)
    }
}

@Composable
private fun AccountDeviceActionRow(action: AccountDeviceAction, enabled: Boolean, onClick: () -> Unit) {
    val icon = when (action) {
        AccountDeviceAction.Refresh -> Icons.Filled.Refresh
        AccountDeviceAction.SyncContacts -> Icons.Filled.AccountCircle
        AccountDeviceAction.SyncChatRooms -> Icons.Filled.Groups
        AccountDeviceAction.StartLogin, AccountDeviceAction.ActiveLogin -> Icons.Filled.AccountCircle
        AccountDeviceAction.RestartWeChat -> Icons.Filled.ErrorOutline
    }
    val tint = if (action == AccountDeviceAction.RestartWeChat) OverlayTokens.alertCore else OverlayTokens.accent
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = OverlayTokens.control,
        border = BorderStroke(1.dp, OverlayTokens.hairline)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                TextLabel(action.title, 13.sp, color = OverlayTokens.panelPrimaryText, weight = FontWeight.SemiBold, maxLines = 1)
                TextLabel(action.subtitle, 11.sp, color = OverlayTokens.panelSecondaryText, maxLines = 2)
            }
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OverlayTokens.toolIcon, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AccountDeviceSectionTitle(text: String) {
    TextLabel(text, 13.sp, modifier = Modifier.padding(top = 4.dp), color = OverlayTokens.panelPrimaryText, weight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun AccountDeviceCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OverlayTokens.control,
        border = BorderStroke(1.dp, OverlayTokens.hairline)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}
