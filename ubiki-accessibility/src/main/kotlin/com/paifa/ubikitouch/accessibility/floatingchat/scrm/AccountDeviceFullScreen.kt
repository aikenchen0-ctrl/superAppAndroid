package com.paifa.ubikitouch.accessibility.floatingchat.scrm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactTaskRunner
import com.paifa.ubikitouch.accessibility.scrm.ScrmDevice
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncChatRoomsRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncContactsRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmWechatAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val AccountDeviceAnimationDurationMillis = 260

/** 与 iOS“帐号设备”工具一致的高性能分页。 */
internal enum class AccountDeviceFullScreenTab(val label: String) {
    Devices("设备"),
    Accounts("账号"),
    Actions("操作")
}

private data class AccountDevicePageState(
    val devices: List<ScrmDevice> = emptyList(),
    val accounts: List<ScrmWechatAccount> = emptyList(),
    val selectedDeviceUuid: String? = null,
    val selectedWeChatId: String? = null
)

/**
 * iOS OpenAPI Business 的“基础/设备、微信登录会话、同步”功能的 Android Material 3 全屏页。
 *
 * 测试流程：从右侧“账号设备”进入，检查 30dp 安全区和三个分页；刷新后比对设备/账号；
 * 依次触发同步、登录会话和重启，确认页面显示 SCRM 服务端原始回执或明确错误。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDeviceFullScreen(
    manager: ScrmSettingsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pageState by remember { mutableStateOf(AccountDevicePageState()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { AccountDeviceFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }

    suspend fun loadSnapshot() {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val session = manager.loadSelectedSessionOrBootstrap()
                val summary = manager.loadSummary()
                AccountDevicePageState(
                    devices = session.readApi.getDevices(),
                    accounts = session.readApi.getWechatAccounts(),
                    selectedDeviceUuid = summary.selectedDeviceUuid ?: session.deviceUuid,
                    selectedWeChatId = summary.selectedWeChatId ?: session.weChatId
                )
            }
        }.onSuccess { snapshot ->
            pageState = snapshot
            status = "已刷新 ${snapshot.devices.size} 台设备和 ${snapshot.accounts.size} 个账号"
        }.onFailure { throwable ->
            error = throwable.message ?: "账号设备数据加载失败"
        }
        loading = false
    }

    fun submitTask(title: String, task: suspend () -> String) {
        scope.launch {
            loading = true
            error = null
            status = "正在$title"
            runCatching { withContext(Dispatchers.IO) { task() } }
                .onSuccess { result -> status = result; reloadKey += 1 }
                .onFailure { throwable -> error = throwable.message ?: "${title}失败" }
            loading = false
        }
    }

    LaunchedEffect(reloadKey) { loadSnapshot() }
    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            translationY.snapTo(pageHeightPx)
            translationY.animateTo(0f, tween(AccountDeviceAnimationDurationMillis))
            entered = true
        }
    }
    fun close() = scope.launch {
        translationY.animateTo(pageHeightPx, tween(AccountDeviceAnimationDurationMillis))
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { this.translationY = translationY.value }
    ) {
        Spacer(Modifier.height(30.dp))
        TopAppBar(
            title = { Text("账号设备", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal) },
            navigationIcon = {
                IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            actions = {
                IconButton(onClick = { reloadKey += 1 }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, "刷新账号设备")
                }
            }
        )
        TabRow(pagerState.currentPage) {
            AccountDeviceFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = index == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        AccountDeviceStatus(loading, error ?: status, error != null)
        HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
            when (AccountDeviceFullScreenTab.entries[page]) {
                AccountDeviceFullScreenTab.Devices -> DeviceListPage(pageState.devices, pageState.selectedDeviceUuid)
                AccountDeviceFullScreenTab.Accounts -> AccountListPage(pageState.accounts, pageState.selectedWeChatId)
                AccountDeviceFullScreenTab.Actions -> ActionListPage(
                    enabled = !loading,
                    onSyncContacts = {
                        submitTask("同步好友资料") {
                            val session = manager.loadSelectedSessionOrBootstrap()
                            val result = ScrmContactTaskRunner(session.taskApi).submitAndAwait(false) {
                                session.contactApi.syncContacts(ScrmSyncContactsRequest(session.deviceUuid, session.weChatId))
                            }
                            "好友同步回执：${result.message}"
                        }
                    },
                    onSyncGroups = {
                        submitTask("同步群聊资料") {
                            val session = manager.loadSelectedSessionOrBootstrap()
                            val result = ScrmContactTaskRunner(session.taskApi).submitAndAwait(false) {
                                session.chatRoomApi.syncChatRooms(ScrmSyncChatRoomsRequest(session.deviceUuid, session.weChatId))
                            }
                            "群聊同步回执：${result.message}"
                        }
                    },
                    onStartLogin = {
                        submitTask("创建扫码登录会话") {
                            val session = manager.loadSelectedSessionOrBootstrap()
                            val body = buildJsonObject {
                                put("deviceUuid", pageState.selectedDeviceUuid ?: session.deviceUuid)
                                put("loginMode", "qrcode"); put("expireSeconds", 300)
                                put("remark", "Android 悬浮账号设备")
                            }
                            session.openApiRaw.requestRaw("POST", "/openapi/v1/wechat-login/start", body = Json.encodeToString(body)).toString()
                        }
                    },
                    onActiveLogin = {
                        submitTask("读取登录会话") {
                            val session = manager.loadSelectedSessionOrBootstrap()
                            session.openApiRaw.requestRaw("GET", "/openapi/v1/wechat-login/active").toString()
                        }
                    },
                    onRestartWeChat = {
                        submitTask("重启设备微信") {
                            val session = manager.loadSelectedSessionOrBootstrap()
                            val body = buildJsonObject {
                                put("deviceUuid", pageState.selectedDeviceUuid ?: session.deviceUuid)
                                put("weChatId", pageState.selectedWeChatId ?: session.weChatId)
                                put("reason", "Android 悬浮账号设备手动操作")
                            }
                            session.openApiRaw.requestRaw("POST", "/openapi/v1/devices/restart-wechat", body = Json.encodeToString(body)).toString()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountDeviceStatus(loading: Boolean, message: String?, isError: Boolean) {
    if (message == null) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceListPage(devices: List<ScrmDevice>, selectedDeviceUuid: String?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AccountDeviceSectionTitle("设备 (${devices.size})") }
        items(devices, key = { it.uuid.orEmpty() }) { device ->
            ListItem(
                headlineContent = { Text(device.phoneModel.orEmpty().ifBlank { "未命名设备" }, fontWeight = FontWeight.Normal) },
                supportingContent = { Text("${device.uuid.orEmpty()} · Android ${device.androidApi}") },
                trailingContent = { Text(if (device.uuid == selectedDeviceUuid) "当前" else if (device.isOnline) "在线" else "离线") }
            )
        }
    }
}

@Composable
private fun AccountListPage(accounts: List<ScrmWechatAccount>, selectedWeChatId: String?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AccountDeviceSectionTitle("微信账号 (${accounts.size})") }
        items(accounts, key = { it.wxid.orEmpty() }) { account ->
            ListItem(
                headlineContent = { Text(account.nickname.orEmpty().ifBlank { "未命名账号" }, fontWeight = FontWeight.Normal) },
                supportingContent = { Text("${account.wxid.orEmpty()} · ${account.clientUuid.orEmpty()}") },
                trailingContent = { Text(if (account.wxid == selectedWeChatId) "当前" else "") }
            )
        }
    }
}

@Composable
private fun ActionListPage(
    enabled: Boolean,
    onSyncContacts: () -> Unit,
    onSyncGroups: () -> Unit,
    onStartLogin: () -> Unit,
    onActiveLogin: () -> Unit,
    onRestartWeChat: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AccountDeviceSectionTitle("数据同步") }
        item { AccountDeviceActionButton("同步好友资料", enabled, onSyncContacts) }
        item { AccountDeviceActionButton("同步群聊资料", enabled, onSyncGroups) }
        item { AccountDeviceSectionTitle("微信登录会话") }
        item { AccountDeviceActionButton("创建扫码登录会话", enabled, onStartLogin) }
        item { AccountDeviceActionButton("查看活跃登录会话", enabled, onActiveLogin) }
        item { AccountDeviceSectionTitle("设备操作") }
        item { AccountDeviceActionButton("重启设备微信", enabled, onRestartWeChat, destructive = true) }
    }
}

@Composable
private fun AccountDeviceActionButton(label: String, enabled: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    if (destructive) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Normal)
        }
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun AccountDeviceSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}
