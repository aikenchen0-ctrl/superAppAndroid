package com.paifa.univerge.accessibility.floatingchat.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.core.model.FloatingChatContact
import kotlinx.coroutines.launch

internal enum class AccountCardFullScreenTab(val label: String) {
    Accounts("选择账号"),
    Preview("名片预览")
}

/**
 * 对应 iOS `ContactCardDesignerViewController` 的 Android Material 3 推名片工作区。
 * 发送操作复用既有 `ToolMessageActions.sendAccountCard`，仅插入本地 ContactLink 名片消息，不新增网络接口。
 * 页面位于现有悬浮根视图中，不创建 Dialog 或 Window，避免 BadTokenException。
 * 测试流程：点击右侧“推名片”，选择账号后查看预览，点击顶部发送并确认聊天流新增名片；点击左上返回，确认页面向下退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountCardFullScreen(
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    onSendAccountCard: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { AccountCardFullScreenTab.entries.size })
    var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val selectedProfile = selectedAccount?.let { account ->
        accountProfiles[account.id] ?: defaultAccountProfileFor(account)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = "推名片",
            onBack = onBack,
            actions = {
                IconButton(
                    enabled = selectedAccount != null,
                    onClick = {
                        selectedAccount?.id?.let { accountId ->
                            onSendAccountCard(accountId)
                            onBack()
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送名片")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            AccountCardFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (AccountCardFullScreenTab.entries[page]) {
                AccountCardFullScreenTab.Accounts -> AccountCardAccountsPage(
                    accounts = accounts,
                    accountProfiles = accountProfiles,
                    selectedAccountId = selectedAccountId,
                    onAccountSelected = { accountId ->
                        selectedAccountId = accountId
                        scope.launch {
                            pagerState.animateScrollToPage(AccountCardFullScreenTab.Preview.ordinal)
                        }
                    }
                )
                AccountCardFullScreenTab.Preview -> AccountCardPreviewPage(
                    account = selectedAccount,
                    profile = selectedProfile
                )
            }
        }
    }
}

@Composable
private fun AccountCardAccountsPage(
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "选择要发送的账号名片",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        if (accounts.isEmpty()) {
            item {
                Text(
                    text = "暂无可发送名片的账号",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        } else {
            items(accounts, key = { account -> account.id }) { account ->
                val profile = accountProfiles[account.id] ?: defaultAccountProfileFor(account)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAccountSelected(account.id) }
                ) {
                    ListItem(
                        leadingContent = { AccountInitialAvatar(profile) },
                        headlineContent = { Text(profile.name.ifBlank { account.name }, fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                text = accountProfileSubtitle(profile).ifBlank { profile.wechatId },
                                fontWeight = FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            if (selectedAccountId == account.id) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCardPreviewPage(
    account: FloatingChatContact?,
    profile: FloatingChatAccountProfile?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "发送后的名片样式",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }
        if (account == null || profile == null) {
            item {
                Text(
                    text = "请先在“选择账号”中选择名片",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { AccountInitialAvatar(profile) },
                        headlineContent = { Text(profile.name.ifBlank { account.name }, fontWeight = FontWeight.Normal) },
                        supportingContent = {
                            Text(
                                text = accountProfileSubtitle(profile).ifBlank { account.description },
                                fontWeight = FontWeight.Normal
                            )
                        }
                    )
                }
            }
            accountProfileDetail(profile).takeIf { it.isNotBlank() }?.let { detail ->
                item {
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountInitialAvatar(profile: FloatingChatAccountProfile) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color(profile.avatarColor)
    ) {
        Text(
            text = profile.avatarInitials.ifBlank { profile.name.take(2) },
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp)
        )
    }
}
