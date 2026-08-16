package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmFriendRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmFriendRequestOperation
import com.paifa.ubikitouch.accessibility.scrm.ScrmHandleFriendRequestRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmPullFriendRequestsRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val ReviewRequestsStatusBarHeightDp = 30

internal enum class ReviewRequestsFullScreenTab(val label: String) {
    Pending("待审核"),
    Processed("已处理")
}

private data class ReviewRequestSubmission(
    val id: Int,
    val action: String,
    val taskId: Long,
    val message: String?
)

/**
 * 对应 iOS 的申请审核入口，读取、拉取和处理好友申请均使用 SCRM OpenAPI。
 * 测试流程：从右侧点击“申请审核”，切换 Tab，刷新后选择通过或拒绝，再在“已处理”查看任务提交状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewRequestsFullScreen(
    route: ScrmFloatingAccountRoute?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ReviewRequestsFullScreenTab.entries.size })
    var requests by remember { mutableStateOf<List<ScrmFriendRequest>>(emptyList()) }
    var submissions by remember { mutableStateOf<List<ReviewRequestSubmission>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submittingId by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    /** 读取服务端已同步的好友申请，待审核页只请求 pendingOnly，已处理页在本地筛除待处理项。 */
    fun load(tab: ReviewRequestsFullScreenTab) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val weChatId = route?.weChatId ?: session.weChatId
                    session.contactApi.getFriendRequests(
                        weChatId = weChatId,
                        count = ReviewRequestListPageSize,
                        pendingOnly = tab == ReviewRequestsFullScreenTab.Pending
                    )
                }
            }.onSuccess { result ->
                requests = if (tab == ReviewRequestsFullScreenTab.Processed) {
                    result.filterNot { it.status == PendingFriendRequestStatus }
                } else {
                    result
                }
                status = "已读取 ${requests.size} 条${tab.label}申请"
            }.onFailure { failure ->
                error = failure.message ?: "读取申请审核列表失败"
            }
            loading = false
        }
    }

    /** 调用拉取接口下发 Android 任务，随后重新读取服务端当前已同步的列表。 */
    fun pull() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.contactApi.pullFriendRequests(
                        ScrmPullFriendRequestsRequest(
                            deviceUuid = route?.deviceUuid ?: session.deviceUuid,
                            weChatId = route?.weChatId ?: session.weChatId
                        )
                    )
                }
            }.onSuccess { task ->
                status = task.message.orEmpty().ifBlank {
                    "已提交拉取任务 #${task.taskId}，等待 Android 回包后将再次读取列表"
                }
                load(ReviewRequestsFullScreenTab.entries[pagerState.currentPage])
            }.onFailure { failure ->
                error = failure.message ?: "拉取好友申请失败"
                loading = false
            }
        }
    }

    /** 用户明确点击后才调用真实处理接口，任务提交后保持服务端状态，不伪造审核成功。 */
    fun submit(request: ScrmFriendRequest, operation: ScrmFriendRequestOperation) {
        scope.launch {
            submittingId = request.id
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val friendId = request.requestWxid?.takeIf { it.isNotBlank() } ?: request.id.toString()
                    session.contactApi.handleFriendRequest(
                        ScrmHandleFriendRequestRequest(
                            deviceUuid = route?.deviceUuid ?: session.deviceUuid,
                            weChatId = route?.weChatId ?: session.weChatId,
                            friendId = friendId,
                            friendNick = request.displayName,
                            remark = request.displayName,
                            replyMsg = if (operation == ScrmFriendRequestOperation.Accept) "已通过" else "暂不添加",
                            operation = operation
                        )
                    )
                }
            }.onSuccess { task ->
                val action = if (operation == ScrmFriendRequestOperation.Accept) "通过" else "拒绝"
                submissions = listOf(
                    ReviewRequestSubmission(request.id, action, task.taskId, task.message)
                ) + submissions
                status = task.message.orEmpty().ifBlank {
                    "已提交${action}任务 #${task.taskId}，等待 Android 回包确认"
                }
                load(ReviewRequestsFullScreenTab.entries[pagerState.currentPage])
            }.onFailure { failure ->
                error = failure.message ?: "提交申请审核失败"
            }
            submittingId = null
        }
    }

    LaunchedEffect(route, pagerState.currentPage) {
        load(ReviewRequestsFullScreenTab.entries[pagerState.currentPage])
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 审核接口页只保留业务状态，外层悬浮根视图统一处理页面进出场。
        FloatingWorkspaceTopAppBar(title = "申请审核", onBack = onBack) {
                IconButton(onClick = ::pull, enabled = !loading && submittingId == null) {
                    Icon(Icons.Filled.Refresh, contentDescription = "拉取并刷新申请")
                }
        }
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            ReviewRequestsFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        status?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        error?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            ReviewRequestsPage(
                tab = ReviewRequestsFullScreenTab.entries[page],
                requests = requests,
                submissions = submissions,
                loading = loading,
                submittingId = submittingId,
                onAccept = { submit(it, ScrmFriendRequestOperation.Accept) },
                onReject = { submit(it, ScrmFriendRequestOperation.Reject) }
            )
        }
    }
}

@Composable
private fun ReviewRequestsPage(
    tab: ReviewRequestsFullScreenTab,
    requests: List<ScrmFriendRequest>,
    submissions: List<ReviewRequestSubmission>,
    loading: Boolean,
    submittingId: Int?,
    onAccept: (ScrmFriendRequest) -> Unit,
    onReject: (ScrmFriendRequest) -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tab == ReviewRequestsFullScreenTab.Pending) {
            item { ReviewRequestsHeader("待审核申请", "通过或拒绝会提交 Android 真实任务，结果以服务端回包为准。") }
            if (requests.isEmpty()) {
                item { ReviewRequestsEmptyState("暂无待审核申请") }
            } else {
                items(requests, key = { it.id }) { request ->
                    PendingReviewRequestCard(
                        request = request,
                        submitting = submittingId == request.id,
                        onAccept = { onAccept(request) },
                        onReject = { onReject(request) }
                    )
                }
            }
        } else {
            item { ReviewRequestsHeader("已处理申请", "显示服务端已处理记录及本次任务提交状态。") }
            if (requests.isEmpty() && submissions.isEmpty()) {
                item { ReviewRequestsEmptyState("暂无已处理申请") }
            }
            items(requests, key = { "request-${it.id}" }) { request ->
                ProcessedReviewRequestCard(request)
            }
            items(submissions, key = { "task-${it.taskId}" }) { submission ->
                SubmittedReviewTaskCard(submission)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ReviewRequestsHeader(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text(subtitle, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PendingReviewRequestCard(
    request: ScrmFriendRequest,
    submitting: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(request.displayName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                    request.requestWxid?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            request.requestMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
            ReviewRequestMetadata(request)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReject, enabled = !submitting, modifier = Modifier.weight(1f)) {
                    Text(if (submitting) "提交中" else "拒绝")
                }
                Button(onClick = onAccept, enabled = !submitting, modifier = Modifier.weight(1f)) {
                    Text(if (submitting) "提交中" else "通过")
                }
            }
        }
    }
}

@Composable
private fun ProcessedReviewRequestCard(request: ScrmFriendRequest) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(request.displayName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text(friendRequestStatusText(request.status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            request.responseMessage?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            ReviewRequestMetadata(request)
        }
    }
}

@Composable
private fun SubmittedReviewTaskCard(submission: ReviewRequestSubmission) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("已提交${submission.action}任务", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                Text("申请 #${submission.id}，任务 #${submission.taskId}", color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall)
                submission.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall) }
            }
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReviewRequestMetadata(request: ScrmFriendRequest) {
    val details = listOfNotNull(
        request.source?.takeIf { it.isNotBlank() }?.let { "来源：$it" },
        request.region?.takeIf { it.isNotBlank() }?.let { "地区：$it" },
        (request.requestTime ?: request.createdAt)?.takeIf { it.isNotBlank() }
    )
    if (details.isNotEmpty()) {
        Text(details.joinToString("  "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewRequestsEmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun friendRequestStatusText(status: Int): String = when (status) {
    PendingFriendRequestStatus -> "待审核"
    AcceptedFriendRequestStatus -> "已通过"
    RejectedFriendRequestStatus -> "已拒绝"
    else -> "已处理"
}

private const val ReviewRequestListPageSize = 100
private const val PendingFriendRequestStatus = 0
private const val AcceptedFriendRequestStatus = 1
private const val RejectedFriendRequestStatus = 2
