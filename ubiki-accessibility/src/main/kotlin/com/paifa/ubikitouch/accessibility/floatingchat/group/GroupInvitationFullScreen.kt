package com.paifa.ubikitouch.accessibility.floatingchat.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.scrm.ScrmAccountMutationRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAgreeChatRoomInviteRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmApproveChatRoomInviteRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomManagementApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmGroupInvitation
import com.paifa.ubikitouch.accessibility.scrm.ScrmGroupInvitationQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupInvitationFullScreen(
    route: ScrmFloatingAccountRoute?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var invitations by remember { mutableStateOf<List<ScrmGroupInvitation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submittingId by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { GroupInvitationTab.entries.size })

    fun load(tab: GroupInvitationTab) {
        if (route == null) {
            loading = false
            error = "当前账号缺少 SCRM 路由"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    manager.loadSelectedSessionOrBootstrap().chatRoomApi.getGroupInvitations(
                        ScrmGroupInvitationQuery(
                            weChatId = route.weChatId,
                            pendingOnly = groupInvitationPendingOnlyFor(tab)
                        )
                    )
                }
            }.onSuccess {
                invitations = it
                status = "已加载 ${it.size} 条待处理邀请"
            }.onFailure {
                error = it.message ?: "加载入群邀请失败"
            }
            loading = false
        }
    }

    fun submit(invitation: ScrmGroupInvitation) {
        if (route == null || !groupInvitationCanSubmit(invitation)) {
            error = "邀请记录缺少执行所需字段"
            return
        }
        scope.launch {
            submittingId = invitation.id
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val api = manager.loadSelectedSessionOrBootstrap().chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持入群邀请操作")
                    when (groupInvitationActionFor(invitation)) {
                        GroupInvitationAction.Agree -> api.agreeChatRoomInvite(
                            ScrmAgreeChatRoomInviteRequest(
                                deviceUuid = route.deviceUuid,
                                weChatId = route.weChatId,
                                talker = invitation.inviter.orEmpty(),
                                msgSvrId = invitation.msgSvrId,
                                msgContent = invitation.reason.orEmpty()
                            )
                        )
                        GroupInvitationAction.Approve -> api.approveChatRoomInvite(
                            ScrmApproveChatRoomInviteRequest(
                                deviceUuid = route.deviceUuid,
                                weChatId = route.weChatId,
                                msgSvrId = invitation.msgSvrId,
                                roomId = invitation.chatRoomId,
                                msgContent = invitation.reason.orEmpty(),
                                msgId = invitation.msgId
                            )
                        )
                    }
                }
            }.onSuccess {
                status = it.message.orEmpty().ifBlank { "请求已提交，等待手机端回写结果" }
                load(GroupInvitationTab.entries[pagerState.currentPage])
            }.onFailure {
                error = it.message ?: "提交入群邀请操作失败"
            }
            submittingId = null
        }
    }

    fun pull() {
        if (route == null) return
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val api = manager.loadSelectedSessionOrBootstrap().chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持刷新入群邀请")
                    api.pullChatRoomInvites(ScrmAccountMutationRequest(route.deviceUuid, route.weChatId))
                }
            }.onSuccess {
                status = it.message.orEmpty().ifBlank { "刷新请求已提交，等待手机端回写" }
                load(GroupInvitationTab.entries[pagerState.currentPage])
            }.onFailure { error = it.message ?: "刷新入群邀请失败" }
            loading = false
        }
    }

    LaunchedEffect(route, pagerState.currentPage) {
        load(GroupInvitationTab.entries[pagerState.currentPage])
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // UI：群邀请卡与 UI组件共享全屏工具栏，避免独立状态栏空白区域造成布局跳变。
        // 测试流程：打开群邀请卡后确认从底部进入，点击左上返回后确认向顶部退出。
        FloatingWorkspaceTopAppBar(
            title = "群邀请卡",
            onBack = onBack,
            actions = {
                IconButton(onClick = ::pull, enabled = !loading && route != null) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            GroupInvitationTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal) }
                )
            }
        }
        status?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.error) }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                invitations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无${GroupInvitationTab.entries[pagerState.currentPage].label}入群邀请", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(invitations, key = { it.id }) { invitation ->
                        GroupInvitationCard(invitation, submittingId == invitation.id, onSubmit = { submit(invitation) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInvitationCard(invitation: ScrmGroupInvitation, submitting: Boolean, onSubmit: () -> Unit) {
    val action = groupInvitationActionFor(invitation)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                invitation.inviteName.orEmpty().ifBlank { invitation.chatRoomId.orEmpty().ifBlank { "群聊邀请" } },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(invitation.inviter.orEmpty().ifBlank { "未知邀请人" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            invitation.invited.takeIf { it.isNotEmpty() }?.let { members ->
                Text("邀请成员：${members.joinToString { it.nickName ?: it.userName.orEmpty() }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (groupInvitationCanSubmit(invitation)) {
                Button(onClick = onSubmit, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (submitting) "提交中" else if (action == GroupInvitationAction.Agree) "同意加入" else "审批邀请")
                }
            } else {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("邀请信息不完整，无法提交") }
            }
        }
    }
}
